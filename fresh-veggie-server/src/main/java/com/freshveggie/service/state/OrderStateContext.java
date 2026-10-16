package com.freshveggie.service.state;

import com.freshveggie.entity.Orders;
import com.freshveggie.enums.OrderEvent;
import com.freshveggie.enums.OrderStatus;
import com.freshveggie.exception.OrderBusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.statemachine.StateMachine;
import org.springframework.stereotype.Service;

/**
 * 订单状态上下文:根据订单当前状态分发到对应状态类。
 * 状态:待支付 -> 备货中 -> 配送中 -> 已完成 / 已取消。
 *
 * <p><b>作用域必须是 prototype(多例),不能是默认的单例。</b>
 * 本类持有 order / iOrderState / stateMachine 三个<b>请求级</b>可变字段。
 * 若为单例,并发请求下线程 B 的 init() 会覆盖线程 A 的字段,
 * 导致 A 的后续动作(pay / cancel)作用到 B 的订单上——订单串号,
 * 且状态流转是写操作,后果是数据损坏而不只是显示错误。
 */
@Service
@Scope("prototype")
@RequiredArgsConstructor
public class OrderStateContext {

    private final PendingPaymentState pendingPaymentState;
    private final PreparingState preparingState;
    private final DeliveringState deliveringState;
    private final CompletedState completedState;
    private final CancelledState canceledState;

    private Orders order;
    private IOrderState<OrderStatus, OrderEvent> iOrderState;
    private StateMachine<OrderStatus, OrderEvent> stateMachine;

    public void init(Orders order, StateMachine<OrderStatus, OrderEvent> stateMachine) {
        this.order = order;
        this.stateMachine = stateMachine;
        OrderStatus orderStatus = OrderStatus.fromState(order.getStatus());
        switch (orderStatus) {
            case PENDING_PAYMENT: // 待支付
                this.iOrderState = pendingPaymentState;
                break;
            case PREPARING: // 备货中
                this.iOrderState = preparingState;
                break;
            case DELIVERING: // 配送中
                this.iOrderState = deliveringState;
                break;
            case COMPLETED: // 已完成
                this.iOrderState = completedState;
                break;
            case CANCELLED: // 已取消
                this.iOrderState = canceledState;
                break;
            default:
                throw new OrderBusinessException("不存在的订单状态");
        }
    }

    public void pay() {
        iOrderState.pay(order, stateMachine);
    }

    public void userCancel() {
        iOrderState.userCancel(order, stateMachine);
    }

    public void adminCancel(String cancelReason) {
        iOrderState.adminCancel(order, stateMachine, cancelReason);
    }

    public void startDelivery() {
        iOrderState.startDelivery(order, stateMachine);
    }

    public void complete() {
        iOrderState.complete(order, stateMachine);
    }
}
