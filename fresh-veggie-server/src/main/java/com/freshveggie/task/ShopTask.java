package com.freshveggie.task;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.freshveggie.entity.Orders;
import com.freshveggie.mapper.OrderMapper;
import com.freshveggie.service.OrderStockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 店铺相关的定时任务。
 *
 * <p>注意:本类依赖启动类上的 {@code @EnableScheduling}。
 * 缺这个注解时 {@code @Scheduled} 不会报错、也不会执行——属于「静默失效」。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ShopTask {

    private final OrderMapper orderMapper;
    private final OrderStockService orderStockService;

    /**
     * 每分钟:取消超时 15 分钟未支付的订单,并回补库存。
     *
     * <p>这里必须回补库存:超时取消和用户主动取消在业务上等价,
     * 都是「订单不再成立」,库存理应释放。早期版本只改了订单状态不回补,
     * 会导致每笔弃单永久占用库存,库存越用越少。
     */
    @Scheduled(cron = "0 * * * * ?")
    @Transactional(rollbackFor = Exception.class)
    public void dealWithTimeoutOrder() {
        List<Orders> timeoutOrders = orderMapper.selectList(Wrappers.<Orders>lambdaQuery()
                .eq(Orders::getStatus, Orders.PENDING_PAYMENT)
                .lt(Orders::getCreateTime, LocalDateTime.now().minusMinutes(15)));
        if (timeoutOrders.isEmpty()) {
            return;
        }
        log.info("定时处理超时待支付订单,共 {} 笔", timeoutOrders.size());
        for (Orders order : timeoutOrders) {
            // ============================================================
            // 【条件更新 + 只更新自己关心的列】（2026-10-01 修正竞态）
            // ============================================================
            // 原来是 orderMapper.updateById(order) —— 整实体无条件覆盖写。
            // 与用户支付构成「读-无条件写」竞态：
            //   本单在 selectList 之后、写回之前完成支付，就会被覆盖成
            //   status=5(已取消) 且 pay_status=1(已支付) 的矛盾行，
            //   并且下面还会【错误回补库存】—— 资损口径错误。
            //
            // 两处修正：
            //   1. 加 status = PENDING_PAYMENT 前置条件，按影响行数判断是否抢到；
            //   2. 改用 lambdaUpdate 只 set 取消相关的三列 —— updateById 会把
            //      内存里那份【过期的】实体全字段写回，可能覆盖掉并发改动的列。
            int updated = orderMapper.update(null, Wrappers.<Orders>lambdaUpdate()
                    .eq(Orders::getId, order.getId())
                    .eq(Orders::getStatus, Orders.PENDING_PAYMENT)
                    .set(Orders::getStatus, Orders.CANCELLED)
                    .set(Orders::getCancelTime, LocalDateTime.now())
                    .set(Orders::getCancelReason, "支付超时，取消订单"));
            if (updated == 0) {
                // 已被并发处理（用户刚支付 / 用户主动取消 / 上一轮任务处理过）。
                // 此时【绝不能】回补库存 —— 订单仍然成立，库存不该释放。
                log.info("超时取消跳过：订单 {} 状态已变更（可能刚被支付），不回补库存",
                        order.getOrderNo());
                continue;
            }
            log.info("订单 {} 超时未支付，已取消并回补库存", order.getOrderNo());
            // 库存回补统一走 OrderStockService，不再在本类重复实现。
            // 本路径的订单都是未支付的，所以不回退销量（销量在支付时才累加）。
            orderStockService.restoreStock(order.getId());
        }
    }

    /**
     * 每天凌晨 1 点:配送中超过 1 小时的订单自动完成。
     */
    @Scheduled(cron = "0 0 1 * * ?")
    @Transactional(rollbackFor = Exception.class)
    public void checkDeliveringOrders() {
        List<Orders> orders = orderMapper.selectList(Wrappers.<Orders>lambdaQuery()
                .eq(Orders::getStatus, Orders.DELIVERING)
                .lt(Orders::getCreateTime, LocalDateTime.now().minusHours(1)));
        if (orders.isEmpty()) {
            return;
        }
        log.info("定时处理配送中的订单,共 {} 笔", orders.size());
        for (Orders order : orders) {
            // 同样改为条件更新：避免与并发的后台取消互相覆盖
            LocalDateTime now = LocalDateTime.now();
            int updated = orderMapper.update(null, Wrappers.<Orders>lambdaUpdate()
                    .eq(Orders::getId, order.getId())
                    .eq(Orders::getStatus, Orders.DELIVERING)
                    .set(Orders::getStatus, Orders.COMPLETED)
                    .set(Orders::getDeliveredTime, now)
                    .set(Orders::getCompletedTime, now));
            if (updated == 0) {
                log.info("自动完成跳过：订单 {} 状态已变更", order.getOrderNo());
            }
        }
    }
}
