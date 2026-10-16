package com.freshveggie.service;

/**
 * 订单对商品库存与销量的影响。
 *
 * <p>2026-10-01 建立，用于统一此前散落在 {@code OrderServiceImpl} 与
 * {@code ShopTask} 里的两份「回补库存」实现，并补上此前完全缺失的
 * {@code product.sales_count} 维护。
 *
 * <p>为什么单独抽一层而不是留在 Service 里：下单、用户取消、管理端取消、
 * 超时取消、退款五条路径都要动库存/销量，散着写必然漏改某一处
 * —— 这正是 {@code sales_count} 长期为零维护的原因。
 */
public interface OrderStockService {

    /** 取消订单时按明细回补库存 */
    void restoreStock(Long orderId);

    /** 订单成交（支付成功）时累加销量 */
    void increaseSales(Long orderId);

    /** 已支付订单被取消时回退销量 */
    void decreaseSales(Long orderId);
}
