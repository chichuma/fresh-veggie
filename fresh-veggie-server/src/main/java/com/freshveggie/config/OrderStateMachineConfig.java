package com.freshveggie.config;

import com.freshveggie.enums.OrderEvent;
import com.freshveggie.enums.OrderStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.config.EnumStateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineConfigurationConfigurer;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;
import org.springframework.statemachine.listener.StateMachineListenerAdapter;
import org.springframework.statemachine.state.State;

import java.util.EnumSet;

@Slf4j
@Configuration
@EnableStateMachineFactory
public class OrderStateMachineConfig extends EnumStateMachineConfigurerAdapter<OrderStatus, OrderEvent> {

    @Override
    public void configure(StateMachineStateConfigurer<OrderStatus, OrderEvent> states) throws Exception {
        states.withStates()
                .initial(OrderStatus.PENDING_PAYMENT)
                .states(EnumSet.allOf(OrderStatus.class));
    }

    @Override
    public void configure(StateMachineTransitionConfigurer<OrderStatus, OrderEvent> transitions) throws Exception {
        transitions.withExternal()
                .source(OrderStatus.PENDING_PAYMENT).target(OrderStatus.PREPARING).event(OrderEvent.PAY) // 支付 -> 备货中
                .and()
                .withExternal()
                .source(OrderStatus.PENDING_PAYMENT).target(OrderStatus.CANCELLED).event(OrderEvent.USER_CANCEL)
                .and()
                .withExternal()
                .source(OrderStatus.PENDING_PAYMENT).target(OrderStatus.CANCELLED).event(OrderEvent.ADMIN_CANCEL)
                .and()
                .withExternal()
                .source(OrderStatus.PREPARING).target(OrderStatus.DELIVERING).event(OrderEvent.START_DELIVERY) // 备货中 -> 配送中
                .and()
                .withExternal()
                .source(OrderStatus.PREPARING).target(OrderStatus.CANCELLED).event(OrderEvent.USER_CANCEL)
                .and()
                .withExternal()
                .source(OrderStatus.PREPARING).target(OrderStatus.CANCELLED).event(OrderEvent.ADMIN_CANCEL)
                .and()
                .withExternal()
                .source(OrderStatus.DELIVERING).target(OrderStatus.COMPLETED).event(OrderEvent.RECEIVE) // 配送中 -> 已完成
                .and()
                .withExternal()
                .source(OrderStatus.DELIVERING).target(OrderStatus.CANCELLED).event(OrderEvent.ADMIN_CANCEL)
                .and()
                .withExternal()
                .source(OrderStatus.COMPLETED).target(OrderStatus.CANCELLED).event(OrderEvent.ADMIN_CANCEL); // 已完成 -> 已取消(退款)
    }

    @Override
    public void configure(StateMachineConfigurationConfigurer<OrderStatus, OrderEvent> config) throws Exception {
        config.withConfiguration().listener(new StateMachineListenerAdapter<OrderStatus, OrderEvent>() {
            @Override
            public void eventNotAccepted(Message<OrderEvent> event) {
                log.error("被拒绝的状态：{}，当前触发事件：{}", event.getHeaders().get("order"), event.getPayload());
            }

            @Override
            public void stateChanged(State<OrderStatus, OrderEvent> from, State<OrderStatus, OrderEvent> to) {
                log.info("state changed from {} to {}", from == null ? "none" : from.getId(), to.getId());
            }
        });
    }
}
