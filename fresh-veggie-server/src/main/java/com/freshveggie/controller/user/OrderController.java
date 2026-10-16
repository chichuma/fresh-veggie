package com.freshveggie.controller.user;

import com.freshveggie.context.BaseContext;
import com.freshveggie.dto.OrdersPaymentDTO;
import com.freshveggie.dto.OrdersSubmitDTO;
import com.freshveggie.dto.pageQuery.OrdersPageQueryDTO;
import com.freshveggie.result.PageResult;
import com.freshveggie.result.Result;
import com.freshveggie.service.OrderService;
import com.freshveggie.vo.OrderPaymentVO;
import com.freshveggie.vo.OrderSubmitVO;
import com.freshveggie.vo.OrderVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController("UserOrderController")
@Slf4j
@Tag(name = "订单相关接口")
@Validated
@RequestMapping("/user/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/submit")
    @Operation(description = "提交订单接口")
    @com.freshveggie.annotation.Idempotent(keyPrefix = "order:submit", expireSeconds = 10, message = "订单提交过于频繁，请稍后再试")
    public Result<OrderSubmitVO> submit(@RequestBody @Validated OrdersSubmitDTO ordersSubmitDTO) {
        log.info("提交订单: {}", ordersSubmitDTO);
        OrderSubmitVO submitVO = orderService.submit(ordersSubmitDTO);
        return Result.success(submitVO);
    }

    @PutMapping("/payment")
    @Operation(description = "订单支付")
    public Result<OrderPaymentVO> payment(@RequestBody @Validated OrdersPaymentDTO ordersPaymentDTO) {
        log.info("订单支付：{}", ordersPaymentDTO);
        orderService.payment(ordersPaymentDTO);
        return Result.success();
    }

    @GetMapping("/historyOrders")
    @Operation(description = "查询历史订单")
    public Result<PageResult<OrderVO>> getHistoryOrders(OrdersPageQueryDTO ordersPageQueryDTO) {
        // 【越权修复】查询条件里的 userId 来自请求参数,属于用户可控输入。
        // 若不在入口强制覆盖,不传参时 mapper 的 <if test="userId != null"> 不生效,
        // 会返回全站订单;传 ?userId=xxx 则能拉取他人订单(含收货人手机号、地址)。
        // 服务端只认登录态里的 userId,永不信任入参。
        ordersPageQueryDTO.setUserId(BaseContext.getCurrentId());
        log.info("历史订单查询: {}", ordersPageQueryDTO);
        PageResult<OrderVO> orderVOPageResult = orderService.getHistoryOrders(ordersPageQueryDTO);
        return Result.success(orderVOPageResult);
    }

    @GetMapping("/orderItem/{id}")
    @Operation(description = "查询订单详情")
    public Result<OrderVO> getOrderItem(@PathVariable @Parameter(description = "订单id", required = true) Long id) {
        log.info("查询订单详情，ID：{}", id);
        OrderVO orderVO = orderService.getUserOrderItem(id);
        return Result.success(orderVO);
    }

    @PostMapping("/repetition/{id}")
    @Operation(description = "再次下单")
    public Result<?> repeatOrder(@PathVariable @Parameter(description = "订单ID", required = true) Long id) {
        log.info("再次下单，ID：{}", id);
        orderService.repeatOrder(id);
        return Result.success();
    }

    @PutMapping("/cancel/{id}")
    @Operation(description = "取消订单")
    public Result<?> cancelOrder(@PathVariable Long id) {
        log.info("取消订单，ID: {}", id);
        orderService.userCancelOrder(id);
        return Result.success();
    }
}
