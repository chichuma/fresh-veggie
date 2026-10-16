package com.freshveggie.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.freshveggie.context.BaseContext;
import com.freshveggie.dto.CartItemDTO;
import com.freshveggie.entity.CartItem;
import com.freshveggie.entity.Product;
import com.freshveggie.exception.BusinessException;
import com.freshveggie.mapper.CartItemMapper;
import com.freshveggie.mapper.ProductMapper;
import com.freshveggie.service.CartItemService;
import com.freshveggie.vo.CartItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 购物车服务。
 *
 * <h3>2026-09-28 瘦身带来的两条规则</h3>
 * <ol>
 *   <li><b>不存价格</b>：{@code cart_item} 只有 user_id / product_id / number。
 *       单价一律实时读 {@code product.price} —— 商品改价后购物车金额立即跟着变。</li>
 *   <li><b>展示时回填</b>：{@link #listCartItem()} 用一次
 *       {@code selectBatchIds} 把商品信息拼成 {@link CartItemVO}，避免 N+1。</li>
 * </ol>
 *
 * <p>这样做修掉一个真实 bug：旧实现下单时用 {@code cart_item.unit_price}
 * 这个「加购时刻的快照价」算金额，商品改价后订单金额与商品实际价格对不上。
 * 快照职责现在完全归 {@code order_item}（下单那一刻定格）。
 */
@Service
@RequiredArgsConstructor
public class CartItemServiceImpl implements CartItemService {

    private final CartItemMapper cartItemMapper;
    private final ProductMapper productMapper;

    /**
     * 加入购物车。
     *
     * <p>命中 (user_id, product_id) 唯一键则数量累加，否则插入新行。
     *
     * <h3>2026-10-01（P2-12）：改为数据库原子 upsert</h3>
     * 原实现是「先 selectOne，查不到就 insert」。同一用户并发加购同一商品时，
     * 两个线程可能都查不到行、都执行 insert，第二个撞
     * {@code uk_cart_item(user_id, product_id)} 抛唯一键冲突，
     * 经全局异常处理变成一次莫名其妙的失败。
     *
     * <p>现在用 {@code INSERT ... ON DUPLICATE KEY UPDATE number = number + n}，
     * 判断与写入由数据库在一条语句里完成，唯一索引保证原子性。
     *
     * <p>仍要先校验商品存在：upsert 只认 (user_id, product_id) 唯一键，
     * 不会校验 product_id 是否真实存在，不查会写进悬空行。
     */
    @Override
    public boolean addCartItem(CartItemDTO dto) {
        Long userId = BaseContext.getCurrentId();
        Product product = productMapper.selectById(dto.getProductId());
        if (product == null) {
            throw new BusinessException("商品ID不存在：" + dto.getProductId());
        }
        int number = dto.getNumber() == null ? 1 : dto.getNumber();
        if (number <= 0) {
            throw new BusinessException("加购数量必须大于 0");
        }
        return cartItemMapper.addOrIncrement(userId, product.getId(), number) > 0;
    }

    /**
     * 减少购物车商品数量，减到 0 则删除整行。
     */
    @Override
    public boolean subCartItem(CartItemDTO dto) {
        Long userId = BaseContext.getCurrentId();
        CartItem exist = cartItemMapper.selectOne(Wrappers.<CartItem>lambdaQuery()
                .eq(CartItem::getUserId, userId)
                .eq(CartItem::getProductId, dto.getProductId()));
        if (exist == null) {
            throw new BusinessException("购物车中没有该商品：" + dto.getProductId());
        }
        if (exist.getNumber() == 1) {
            return cartItemMapper.deleteById(exist.getId()) > 0;
        }
        exist.setNumber(exist.getNumber() - 1);
        exist.setUpdateTime(LocalDateTime.now());
        return cartItemMapper.updateById(exist) > 0;
    }

    /**
     * 查询当前用户购物车，并回填实时商品信息。
     *
     * <p>两次查询：cart_item（按 user_id，走 uk_cart_item 最左前缀）
     * + product（一次 selectBatchIds）。不产生 N+1。
     */
    @Override
    public List<CartItemVO> listCartItem() {
        List<CartItem> rows = cartItemMapper.selectList(Wrappers.<CartItem>lambdaQuery()
                .eq(CartItem::getUserId, BaseContext.getCurrentId()));
        if (rows.isEmpty()) {
            return List.of();
        }

        List<Long> productIds = rows.stream().map(CartItem::getProductId).distinct().toList();
        Map<Long, Product> productMap = productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<CartItemVO> result = new ArrayList<>(rows.size());
        for (CartItem row : rows) {
            Product p = productMap.get(row.getProductId());
            // 商品可能已被删除：保留行、把商品字段留空，前端可提示「商品已下架」。
            // 刻意不静默丢行 —— 否则用户会发现数量莫名少了一件。
            BigDecimal unitPrice = p == null ? null : p.getPrice();
            result.add(CartItemVO.builder()
                    .id(row.getId())
                    .userId(row.getUserId())
                    .productId(row.getProductId())
                    .name(p == null ? null : p.getName())
                    .image(p == null ? null : p.getImage())
                    .spec(p == null ? null : p.getSpec())
                    .unit(p == null ? null : p.getUnit())
                    .unitPrice(unitPrice)
                    .number(row.getNumber())
                    .amount(unitPrice == null ? null
                            : unitPrice.multiply(BigDecimal.valueOf(row.getNumber())))
                    .stock(p == null ? null : p.getStock())
                    .productStatus(p == null ? null : p.getStatus())
                    .createTime(row.getCreateTime())
                    .updateTime(row.getUpdateTime())
                    .build());
        }
        return result;
    }

    @Override
    public boolean clearCartItem() {
        return cartItemMapper.delete(Wrappers.<CartItem>lambdaQuery()
                .eq(CartItem::getUserId, BaseContext.getCurrentId())) > 0;
    }
}
