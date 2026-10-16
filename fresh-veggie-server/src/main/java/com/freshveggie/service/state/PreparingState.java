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
 * 备货中状态(已支付,仓库分拣打包):可开始配送、可取消。
 *
 * <h3>2026-10-01 两处加固</h3>
 * <ol>
 *   <li><b>条件更新</b>：原先是无条件 {@code updateById(order)}（整实体覆盖写）。
 *       与并发的取消/配送操作一起会互相覆盖，改为
 *       {@code UPDATE ... WHERE id=? AND status=<当前状态>} 并检查影响行数。</li>
 *   <li><b>回退销量</b>：本状态的订单都是已支付的，取消意味着退款，
 *       需要把 {@code product.sales_count} 减回去（P2-2）。</li>
 * </ol>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PreparingState implements IOrderState<OrderStatus, OrderEvent> {

    private final OrderMapper orderMapper;
    private final OrderStockService orderStockService;

    @Override
    public void pay(Orders order, StateMachine<OrderStatus, OrderEvent> stateMachine) {
        throw new OrderBusinessException("订单已完成支付，不要重复付款");
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
     * 取消订单：条件更新 + 回退销量。
     *
     * <p>只有真正抢到状态迁移（影响 1 行）才回退销量 ——
     * 否则并发下可能重复扣减，把销量减成负的。
     */
    private void cancelOrder(Orders order, String cancelReason) {
        LocalDateTime now = LocalDateTime.now();
        int updated = orderMapper.update(null, Wrappers.<Orders>lambdaUpdate()
                .eq(Orders::getId, order.getId())
                .eq(Orders::getStatus, Orders.PREPARING)
                .set(Orders::getStatus, Orders.CANCELLED)
                .set(Orders::getPayStatus, Orders.REFUND)
                .set(Orders::getCancelReason, cancelReason)
                .set(Orders::getCancelTime, now));
        if (updated == 0) {
            log.warn("取消未生效：订单 {} 状态已被并发变更", order.getOrderNo());
            throw new OrderBusinessException("订单状态已变更，取消失败");
        }
        order.setStatus(Orders.CANCELLED);
        order.setPayStatus(Orders.REFUND);
        order.setCancelReason(cancelReason);
        order.setCancelTime(now);
        // 已支付订单退款 → 库存回补 + 销量回退。
        // 库存原本由调用方 OrderServiceImpl 事后回补，现在收敛到状态类内部：
        // 取消入口有用户取消/后台取消/超时取消三条，散在调用方写必然漏改。
        orderStockService.restoreStock(order.getId());
        orderStockService.decreaseSales(order.getId());
        log.info("{} 取消成功（已退款，库存已回补、销量已回退）", order.getOrderNo());
    }

    @Override
    public void startDelivery(Orders order, StateMachine<OrderStatus, OrderEvent> stateMachine) {
        fire(order, stateMachine, OrderEvent.START_DELIVERY, OrderStatus.DELIVERING);

        int updated = orderMapper.update(null, Wrappers.<Orders>lambdaUpdate()
                .eq(Orders::getId, order.getId())
                .eq(Orders::getStatus, Orders.PREPARING)
                .set(Orders::getStatus, Orders.DELIVERING));
        if (updated == 0) {
            log.warn("开始配送未生效：订单 {} 状态已被并发变更", order.getOrderNo());
            throw new OrderBusinessException("订单状态已变更，操作失败");
        }
        order.setStatus(Orders.DELIVERING);
        log.info("{} 开始配送", order.getOrderNo());
    }

    @Override
    public void complete(Orders order, StateMachine<OrderStatus, OrderEvent> stateMachine) {
        throw new OrderBusinessException("订单未开始配送，无法确认送达");
    }
}
