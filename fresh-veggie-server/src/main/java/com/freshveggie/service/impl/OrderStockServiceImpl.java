package com.freshveggie.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.freshveggie.entity.OrderItem;
import com.freshveggie.entity.Product;
import com.freshveggie.mapper.OrderItemMapper;
import com.freshveggie.mapper.ProductMapper;
import com.freshveggie.service.OrderStockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 订单对商品库存与销量的影响。
 *
 * <h3>2026-10-01 建立：统一三处重复实现</h3>
 * 此前「按订单明细回补库存」有两份几乎相同的私有方法
 * （{@code OrderServiceImpl#restoreStock(List)} 与 {@code ShopTask#restoreStock(Long)}），
 * 且 {@code product.sales_count}（列注释写着"销量(冗余,来自order_item聚合)"）
 * <b>全工程零维护</b> —— 只有读、没有任何写，商品页销量恒为 0 或陈旧值（P2-2）。
 *
 * <p>现在集中到本类：
 * <ul>
 *   <li>{@link #restoreStock(Long)} —— 下单取消时回补库存</li>
 *   <li>{@link #increaseSales(Long)} / {@link #decreaseSales(Long)} —— 维护销量冗余列</li>
 * </ul>
 *
 * <h3>销量口径</h3>
 * <b>支付成功才计销量</b>（订单进入"备货中"），取消时若该单曾支付过则回退。
 * 这样"销量"表达的是真实成交，与报表 Top10 的口径一致。
 *
 * <p>库存与销量都用 {@code stock = stock ± n} 的**原子 SQL** 自增自减，
 * 不做「读出来加一再写回」——后者在并发下会丢失更新。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderStockServiceImpl implements OrderStockService {

    private final OrderItemMapper orderItemMapper;
    private final ProductMapper productMapper;

    /**
     * 按订单明细回补库存（取消订单时调用）。
     *
     * <p>与 {@code OrderSubmitServiceImpl#deductStock} 的原子扣减对称。
     */
    @Override
    public void restoreStock(Long orderId) {
        for (OrderItem item : listItems(orderId)) {
            if (item.getProductId() == null || item.getNumber() == null) {
                continue;
            }
            productMapper.update(null, Wrappers.<Product>lambdaUpdate()
                    .eq(Product::getId, item.getProductId())
                    .setSql("stock = stock + {0}", item.getNumber()));
        }
    }

    /**
     * 订单成交，累加销量（支付成功时调用）。
     */
    @Override
    public void increaseSales(Long orderId) {
        for (OrderItem item : listItems(orderId)) {
            if (item.getProductId() == null || item.getNumber() == null) {
                continue;
            }
            productMapper.update(null, Wrappers.<Product>lambdaUpdate()
                    .eq(Product::getId, item.getProductId())
                    .setSql("sales_count = sales_count + {0}", item.getNumber()));
        }
        log.info("订单 {} 已计入销量", orderId);
    }

    /**
     * 已支付订单被取消，回退销量（退款/取消时调用）。
     *
     * <p>用 {@code GREATEST(sales_count - n, 0)} 兜底，保证不会出现负销量
     * —— 历史数据里 sales_count 可能本就是 0（此前零维护），直接减会变负数。
     */
    @Override
    public void decreaseSales(Long orderId) {
        for (OrderItem item : listItems(orderId)) {
            if (item.getProductId() == null || item.getNumber() == null) {
                continue;
            }
            productMapper.update(null, Wrappers.<Product>lambdaUpdate()
                    .eq(Product::getId, item.getProductId())
                    .setSql("sales_count = GREATEST(sales_count - {0}, 0)", item.getNumber()));
        }
        log.info("订单 {} 已回退销量", orderId);
    }

    private List<OrderItem> listItems(Long orderId) {
        return orderItemMapper.selectList(Wrappers.<OrderItem>lambdaQuery()
                .eq(OrderItem::getOrderId, orderId));
    }
}
