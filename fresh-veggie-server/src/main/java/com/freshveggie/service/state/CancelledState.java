package com.freshveggie.service.state;

import com.freshveggie.entity.Orders;
import com.freshveggie.enums.OrderEvent;
import com.freshveggie.enums.OrderStatus;
import com.freshveggie.exception.OrderBusinessException;
import org.springframework.statemachine.StateMachine;
import org.springframework.stereotype.Service;

/**
 * 已取消状态:终态,不允许任何操作。
 */
@Service
public class CancelledState implements IOrderState<OrderStatus, OrderEvent> {

    @Override
    public void pay(Orders order, StateMachine<OrderStatus, OrderEvent> stateMachine) {
        throw new OrderBusinessException("订单已取消，不要重复付款！");
    }

    @Override
    public void userCancel(Orders order, StateMachine<OrderStatus, OrderEvent> stateMachine) {
        throw new OrderBusinessException("订单已取消，不要重复取消！");
    }

    @Override
    public void adminCancel(Orders order, StateMachine<OrderStatus, OrderEvent> stateMachine, String cancelReason) {
        throw new OrderBusinessException("订单已取消，不要重复操作！");
    }

    @Override
    public void startDelivery(Orders order, StateMachine<OrderStatus, OrderEvent> stateMachine) {
        throw new OrderBusinessException("订单已取消，不要重复操作！");
    }

    @Override
    public void complete(Orders order, StateMachine<OrderStatus, OrderEvent> stateMachine) {
        throw new OrderBusinessException("订单已取消，不要重复操作！");
    }
}
