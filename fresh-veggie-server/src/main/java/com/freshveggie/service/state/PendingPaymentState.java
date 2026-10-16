package com.freshveggie.service.state;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freshveggie.entity.Orders;
import com.freshveggie.enums.OrderEvent;
import com.freshveggie.enums.OrderStatus;
import com.freshveggie.exception.OrderBusinessException;
import com.freshveggie.mapper.OrderMapper;
import com.freshveggie.service.OrderStockService;
import com.freshveggie.websocket.WebSocketServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.statemachine.StateMachine;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 待支付状态:可支付、可取消。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PendingPaymentState implements IOrderState<OrderStatus, OrderEvent> {

    private final OrderMapper orderMapper;
    private final OrderStockService orderStockService;
    private final WebSocketServer webSocketServer;
    private final ObjectMapper objectMapper;

    @Override
    public void pay(Orders order, StateMachine<OrderStatus, OrderEvent> stateMachine) {
        // 事件被拒绝会抛 OrderBusinessException,不再静默失败
        fire(order, stateMachine, OrderEvent.PAY, OrderStatus.PREPARING);

        // ============================================================
        // 【先写库成功，再改内存状态】+ 条件更新（2026-10-01 修正竞态）
        // ============================================================
        // 原来是无条件 `orderMapper.updateById(order)`，与 ShopTask 的
        // 「超时 15 分钟自动取消」构成「读-无条件写」竞态：
        //   若本单在 ShopTask 读出候选列表之后、写回之前完成支付，
        //   定时任务会把这一行覆盖成 status=5(已取消) 且 pay_status=1(已支付)
        //   的矛盾行，并且【错误回补库存】—— 资损口径错误。
        //
        // 现在加 `status = PENDING_PAYMENT` 作为前置条件，按影响行数判断：
        // 若为 0 行，说明本单已被并发取消，则【不改内存状态、不推 WebSocket】，
        // 直接告诉用户支付失败，而不是返回一个假的支付成功。
        LocalDateTime paidTime = LocalDateTime.now();
        int updated = orderMapper.update(null, Wrappers.<Orders>lambdaUpdate()
                .eq(Orders::getId, order.getId())
                .eq(Orders::getStatus, Orders.PENDING_PAYMENT)
                .set(Orders::getStatus, Orders.PREPARING)
                .set(Orders::getPayStatus, Orders.PAID)
                .set(Orders::getPaidTime, paidTime));
        if (updated == 0) {
            log.warn("支付失败：订单已被取消或状态已变更，orderNo={}, id={}",
                    order.getOrderNo(), order.getId());
            throw new OrderBusinessException("订单已取消或状态已变更，无法支付");
        }

        order.setStatus(Orders.PREPARING);
        order.setPayStatus(Orders.PAID);
        order.setPaidTime(paidTime);
        log.info("{} 支付成功，进入备货", order.getOrderNo());

        // 成交后累加销量。
        // product.sales_count 此前全工程零维护（P2-2），这里补上写入口。
        // 放在条件更新成功之后，保证「只有真正支付成功的订单才计销量」。
        orderStockService.increaseSales(order.getId());

        // WebSocket 推送新订单提醒
        Map<String, Object> message = new HashMap<>();
        message.put("type", 1);
        message.put("orderId", order.getId());
        message.put("content", "订单号: " + order.getOrderNo());
        try {
            webSocketServer.sendToAllClient(objectMapper.writeValueAsString(message));
        } catch (JsonProcessingException exception) {
            log.error("支付时转换 JSON 异常", exception);
        }
    }

    @Override
    public void userCancel(Orders order, StateMachine<OrderStatus, OrderEvent> stateMachine) {
        fire(order, stateMachine, OrderEvent.USER_CANCEL, OrderStatus.CANCELLED);
        cancelOrder(order, "用户取消");
    }

    @Override
    public void adminCancel(Orders order, StateMachine<OrderStatus, OrderEvent> stateMachine, String cancelReason) {
        fire(order, stateMachine, OrderEvent.ADMIN_CANCEL, OrderStatus.CANCELLED);
        cancelOrder(order, cancelReason);
    }

    /**
     * 取消待支付订单：条件更新 + 回补库存。
     *
     * <p>本状态的订单都是未支付的，所以<b>不回退销量</b>
     * （销量在支付成功时才累加，见 {@link #pay}）。
     *
     * <p>回补库存放在这里而不是调用方：取消入口有用户取消、后台取消、超时取消三条，
     * 散在调用方写必然漏改 —— 这正是此前 {@code sales_count} 长期零维护的成因。
     */
    private void cancelOrder(Orders order, String cancelReason) {
        LocalDateTime now = LocalDateTime.now();
        int updated = orderMapper.update(null, Wrappers.<Orders>lambdaUpdate()
                .eq(Orders::getId, order.getId())
                .eq(Orders::getStatus, Orders.PENDING_PAYMENT)
                .set(Orders::getStatus, Orders.CANCELLED)
                .set(Orders::getCancelReason, cancelReason)
                .set(Orders::getCancelTime, now));
        if (updated == 0) {
            log.warn("取消未生效：订单 {} 状态已被并发变更（可能刚被支付）", order.getOrderNo());
            throw new OrderBusinessException("订单状态已变更，取消失败");
        }
        order.setStatus(Orders.CANCELLED);
        order.setCancelReason(cancelReason);
        order.setCancelTime(now);
        // 未支付订单取消 → 释放库存（不回退销量，因为从未计入）
        orderStockService.restoreStock(order.getId());
        log.info("{} 取消成功（库存已回补）", order.getOrderNo());
    }

    @Override
    public void startDelivery(Orders order, StateMachine<OrderStatus, OrderEvent> stateMachine) {
        throw new OrderBusinessException("订单未支付，无法开始配送");
    }

    @Override
    public void complete(Orders order, StateMachine<OrderStatus, OrderEvent> stateMachine) {
        throw new OrderBusinessException("订单未支付，不要重复操作");
    }
}
