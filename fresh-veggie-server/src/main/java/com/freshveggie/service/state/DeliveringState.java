package com.freshveggie.service.state;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.freshveggie.entity.Orders;
import com.freshveggie.enums.OrderEvent;
import com.freshveggie.enums.OrderStatus;
import com.freshveggie.exception.OrderBusinessException;
import com.freshveggie.mapper.OrderMapper;
import com.freshveggie.service.OrderStockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.statemachine.StateMachine;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 配送中状态:可确认送达、可取消(退款)。
 *
 * <p>2026-10-01：改用条件更新（{@code WHERE status=DELIVERING}），
 * 并在后台取消（= 退款）时回退 {@code product.sales_count}。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DeliveringState implements IOrderState<OrderStatus, OrderEvent> {

    private final OrderMapper orderMapper;
    private final OrderStockService orderStockService;

    @Override
    public void pay(Orders order, StateMachine<OrderStatus, OrderEvent> stateMachine) {
        throw new OrderBusinessException("订单已完成支付，不要重复付款");
    }

    @Override
    public void userCancel(Orders order, StateMachine<OrderStatus, OrderEvent> stateMachine) {
        throw new OrderBusinessException("配送中订单请联系商家取消");
    }

    @Override
    public void adminCancel(Orders order, StateMachine<OrderStatus, OrderEvent> stateMachine, String cancelReason) {
        fire(order, stateMachine, OrderEvent.ADMIN_CANCEL, OrderStatus.CANCELLED);

        LocalDateTime now = LocalDateTime.now();
        int updated = orderMapper.update(null, Wrappers.<Orders>lambdaUpdate()
                .eq(Orders::getId, order.getId())
                .eq(Orders::getStatus, Orders.DELIVERING)
                .set(Orders::getStatus, Orders.CANCELLED)
                .set(Orders::getPayStatus, Orders.REFUND)
                .set(Orders::getCancelReason, cancelReason)
                .set(Orders::getCancelTime, now));
        if (updated == 0) {
            log.warn("后台取消未生效：订单 {} 状态已被并发变更", order.getOrderNo());
            throw new OrderBusinessException("订单状态已变更，取消失败");
        }
        order.setStatus(Orders.CANCELLED);
        order.setPayStatus(Orders.REFUND);
        order.setCancelReason(cancelReason);
        order.setCancelTime(now);
        // 已支付订单退款 → 库存回补 + 销量回退
        orderStockService.restoreStock(order.getId());
        orderStockService.decreaseSales(order.getId());
        log.info("{} 后台取消成功（已退款，库存已回补、销量已回退）", order.getOrderNo());
    }

    @Override
    public void startDelivery(Orders order, StateMachine<OrderStatus, OrderEvent> stateMachine) {
        throw new OrderBusinessException("订单配送中，不要重复操作");
    }

    @Override
    public void complete(Orders order, StateMachine<OrderStatus, OrderEvent> stateMachine) {
        fire(order, stateMachine, OrderEvent.RECEIVE, OrderStatus.COMPLETED);

        LocalDateTime now = LocalDateTime.now();
        int updated = orderMapper.update(null, Wrappers.<Orders>lambdaUpdate()
                .eq(Orders::getId, order.getId())
                .eq(Orders::getStatus, Orders.DELIVERING)
                .set(Orders::getStatus, Orders.COMPLETED)
                .set(Orders::getDeliveredTime, now)
                .set(Orders::getCompletedTime, now));
        if (updated == 0) {
            log.warn("确认送达未生效：订单 {} 状态已被并发变更", order.getOrderNo());
            throw new OrderBusinessException("订单状态已变更，操作失败");
        }
        order.setStatus(Orders.COMPLETED);
        order.setDeliveredTime(now);
        order.setCompletedTime(now);
        log.info("{} 已完成", order.getOrderNo());
    }
}
