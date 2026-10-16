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
 * 已完成状态:仅支持后台取消(退款)。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CompletedState implements IOrderState<OrderStatus, OrderEvent> {

    private final OrderMapper orderMapper;
    private final OrderStockService orderStockService;

    @Override
    public void pay(Orders order, StateMachine<OrderStatus, OrderEvent> stateMachine) {
        throw new OrderBusinessException("订单已完成支付，不要重复付款");
    }

    @Override
    public void userCancel(Orders order, StateMachine<OrderStatus, OrderEvent> stateMachine) {
        throw new OrderBusinessException("请联系商家进行退款");
    }

    @Override
    public void adminCancel(Orders order, StateMachine<OrderStatus, OrderEvent> stateMachine, String cancelReason) {
        fire(order, stateMachine, OrderEvent.ADMIN_CANCEL, OrderStatus.CANCELLED);

        LocalDateTime now = LocalDateTime.now();
        int updated = orderMapper.update(null, Wrappers.<Orders>lambdaUpdate()
                .eq(Orders::getId, order.getId())
                .eq(Orders::getStatus, Orders.COMPLETED)
                .set(Orders::getStatus, Orders.CANCELLED)
                .set(Orders::getPayStatus, Orders.REFUND)
                .set(Orders::getCancelReason, cancelReason)
                .set(Orders::getCancelTime, now));
        if (updated == 0) {
            log.warn("退款未生效：订单 {} 状态已被并发变更", order.getOrderNo());
            throw new OrderBusinessException("订单状态已变更，退款失败");
        }
        order.setStatus(Orders.CANCELLED);
        order.setPayStatus(Orders.REFUND);
        order.setCancelReason(cancelReason);
        order.setCancelTime(now);
        // 已完成订单退款 → 库存回补 + 销量回退
        orderStockService.restoreStock(order.getId());
        orderStockService.decreaseSales(order.getId());
        log.info("{} 退款成功（库存已回补、销量已回退）", order.getOrderNo());
    }

    @Override
    public void startDelivery(Orders order, StateMachine<OrderStatus, OrderEvent> stateMachine) {
        throw new OrderBusinessException("订单已完成，不要重复操作");
    }

    @Override
    public void complete(Orders order, StateMachine<OrderStatus, OrderEvent> stateMachine) {
        throw new OrderBusinessException("订单已完成，不要重复操作");
    }
}
