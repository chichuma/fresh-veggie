package com.freshveggie.service.state;

import com.freshveggie.entity.Orders;
import com.freshveggie.exception.OrderBusinessException;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import reactor.core.publisher.Mono;

/**
 * 订单状态动作接口:每种状态只允许合法流转,非法操作抛异常。
 * 状态:待支付 -> 备货中 -> 配送中 -> 已完成 / 已取消。
 */
public interface IOrderState<S, E> {

    /**
     * 触发状态机事件,并返回流转「成功之后」的状态。
     *
     * <p>为什么必须收敛到这一个方法里:
     * <ol>
     *   <li><b>时序</b>:sendEvent 返回的是 Flux,是惰性的。若只 subscribe() 不等待,
     *       下一行 getState() 可能在事件真正处理之前就执行,读到的是旧状态(竞态)。</li>
     *   <li><b>拒绝语义</b>:事件被状态机拒绝时(非法流转),不等待的写法会静默什么都不做,
     *       调用方以为成功、实际没落库,还继续执行后续动作(例如回补库存)。
     *       这里改成抛异常,失败就是失败。</li>
     * </ol>
     *
     * @param order   当前订单,作为事件 header 传给监听器用于日志
     * @param sm      已重建好的状态机
     * @param event   目标事件
     * @param target  期望流转到的目标状态
     * @return 流转后的实际状态(调用方通常直接用 target 之外的语义判断)
     * @throws OrderBusinessException 事件被拒绝,或流转结果与期望不符
     */
    default S fire(Orders order, StateMachine<S, E> sm, E event, S target) {
        Message<E> message = MessageBuilder.withPayload(event)
                .setHeader("order", order)
                .build();
        // blockLast():等待事件处理完成再往下走,消除读旧状态的竞态
        sm.sendEvent(Mono.just(message)).blockLast();

        S now = sm.getState().getId();
        if (!target.equals(now)) {
            throw new OrderBusinessException("当前订单状态不允许该操作");
        }
        return now;
    }

    void pay(Orders order, StateMachine<S, E> stateMachine);

    void userCancel(Orders order, StateMachine<S, E> stateMachine);

    void adminCancel(Orders order, StateMachine<S, E> stateMachine, String cancelReason);

    void startDelivery(Orders order, StateMachine<S, E> stateMachine);

    void complete(Orders order, StateMachine<S, E> stateMachine);
}
