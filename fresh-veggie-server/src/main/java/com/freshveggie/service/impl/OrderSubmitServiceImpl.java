package com.freshveggie.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.freshveggie.entity.*;
import com.freshveggie.exception.BusinessException;
import com.freshveggie.mapper.CartItemMapper;
import com.freshveggie.mapper.OrderItemMapper;
import com.freshveggie.mapper.OrderMapper;
import com.freshveggie.mapper.ProductMapper;
import com.freshveggie.service.OrderSubmitService;
import com.freshveggie.vo.OrderSubmitVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@RequiredArgsConstructor
@Service
public class OrderSubmitServiceImpl implements OrderSubmitService {
    private final OrderMapper orderMapper;
    private final ProductMapper productMapper;
    private final CartItemMapper cartItemMapper;
    private final OrderItemMapper orderItemMapper;

    /**
     * 正式提交订单
     *
     * @return 查询表
     */
    @Transactional
    public OrderSubmitVO doSubmit(
            List<CartItem> cartItemList,
            Orders orders,
            List<OrderItem> orderItemList,
            Long userId
    ) {
        // 插入订单（反填主键ID）
        orderMapper.insert(orders);

        for (OrderItem orderItem : orderItemList) {
            orderItem.setOrderId(orders.getId());
        }

        // 插入订单-商品
        orderItemMapper.insertBatch(orderItemList);

        // 删购物车
        cartItemMapper.delete(Wrappers.<CartItem>lambdaQuery().eq(CartItem::getUserId, userId));

        // ============================================================
        // 库存扣减：刻意放在事务的【最后一步】
        // ============================================================
        // 为什么放最后：
        //   `product` 是被本压测的【唯一热点行】（所有订单买同一商品 9001）。
        //   deductStock 会在这行上拿排他锁（X 锁）并【持有到事务提交】。
        //   把它放在开头 = X 锁覆盖整个事务 = 所有并发事务在这把锁上串行排队。
        //   JFR 实测（2026-09-28）：放开头时 deductStock 占 MySQL socket 阻塞
        //   时间的 99.32%、平均 1384ms/次，是 150~210 req/s 天花板的全部来源。
        //
        // 为什么现在放最后【不会】死锁（与 2026-09-27 那次的区别）：
        //   那次放最后 → 4190 次 Deadlock。根因是 order_item 上的外键
        //   fk_order_item_product：插入子表行时 InnoDB 会对父行（product 热行）
        //   加 S 锁并持有到提交。于是「先插明细拿 S 锁 → 再扣减要 X 锁」
        //   构成 S→X 锁升级，并发下必然成环。
        //   2026-09-28 的迁移已删除该外键（见
        //   bench/sql/refactor/2026-09-28-01-drop-hotspot-fks.sql）。
        //   现在插入明细不再在 product 上留任何锁，本事务在 product 上的
        //   唯一一次加锁就是下面这条 UPDATE —— 不构成环。
        //
        // 正确性：deductStock 里 rows == 0 会抛业务异常，整个事务回滚
        //   （此前插入的订单与明细一并撤销），与放在开头时语义完全一致。
        deductStock(cartItemList);

        return OrderSubmitVO.builder()
                .id(orders.getId())
                .orderNumber(orders.getOrderNo())
                .orderAmount(orders.getTotalAmount())
                .orderTime(LocalDateTime.now())
                .build();
    }

    /**
     * 商品库存扣减
     * <p>
     * 从购物车列表读 商品+数量
     * 原子扣减，失败就回滚全部
     */
    private void deductStock(List<CartItem> cartItemList) {

        // 1. 先按 productId 合并，防止重复 id
        Map<Long, Integer> merged = new LinkedHashMap<>();
        for (CartItem item : cartItemList) {
            merged.merge(item.getProductId(), item.getNumber(), Integer::sum);
        }

        // 2. 构建 CASE WHEN SQL 片段
        StringBuilder caseSql = new StringBuilder("stock = stock - CASE id ");
        for (Long productId : merged.keySet()) {
            caseSql.append("WHEN ").append(productId)
                    .append(" THEN ").append(merged.get(productId)).append(" ");
        }
        caseSql.append("END");

        // 3. 构建库存校验条件（同样用 CASE WHEN 确保每个商品都够扣）
        StringBuilder stockCheckSql = new StringBuilder("stock >= CASE id ");
        for (Long productId : merged.keySet()) {
            stockCheckSql.append("WHEN ").append(productId)
                    .append(" THEN ").append(merged.get(productId)).append(" ");
        }
        stockCheckSql.append("END");

        // 4. 执行更新
        int rows = productMapper.update(null, Wrappers.<Product>lambdaUpdate()
                .setSql(caseSql.toString())
                .in(Product::getId, merged.keySet())
                .last("AND " + stockCheckSql)
        );

        // 5. 校验影响行数
        if (rows != merged.size()) {
            throw new BusinessException("库存不足，请刷新后重试");
        }
    }
}
