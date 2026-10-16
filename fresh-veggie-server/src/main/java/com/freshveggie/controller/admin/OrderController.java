package com.freshveggie.controller.admin;

import com.freshveggie.dto.OrdersCancelDTO;
import com.freshveggie.dto.pageQuery.OrdersPageQueryDTO;
import com.freshveggie.result.PageResult;
import com.freshveggie.result.Result;
import com.freshveggie.service.OrderService;
import com.freshveggie.vo.OrderStatisticsVO;
import com.freshveggie.vo.OrderVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;


@RestController("AdminOrderController")
@Slf4j
@Tag(name = "订单管理相关接口")
@Validated
@RequestMapping("/admin/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @GetMapping("/conditionSearch")
    @Operation(description = "订单搜素")
    public Result<PageResult<OrderVO>> getOrderListByCondition(OrdersPageQueryDTO ordersPageQueryDTO) {
        log.info("后台订单管理分页查询，{}", ordersPageQueryDTO);
        PageResult<OrderVO> orderVOPageResult = orderService.getOrderListByCondition(ordersPageQueryDTO);
        return Result.success(orderVOPageResult);
    }

    @GetMapping("/details/{id}")
    @Operation(description = "查询订单详情")
    public Result<OrderVO> getOrderItem(@PathVariable @Parameter(description = "订单ID", required = true) Long id) {
        log.info("查询订单详情，ID：{}", id);
        OrderVO orderVO = orderService.getOrderItem(id);
        return Result.success(orderVO);
    }

    @GetMapping("/statistics")
    @Operation(description = "订单状态数量统计")
    public Result<OrderStatisticsVO> statistics() {
        log.info("查询各个状态的订单数量");
        OrderStatisticsVO statisticsVO = orderService.statistics();
        return Result.success(statisticsVO);
    }

    @PutMapping("/cancel")
    @Operation(description = "取消订单")
    public Result<?> cancelOrder(@RequestBody @Valid OrdersCancelDTO ordersCancelDTO) {
        log.info("后台取消订单, ID: {}, 取消原因：{}", ordersCancelDTO.getId(), ordersCancelDTO.getCancelReason());
        orderService.adminCancelOrder(ordersCancelDTO);
        return Result.success();
    }

    @PutMapping("/delivery/{id}")
    @Operation(description = "开始配送(备货中 -> 配送中)")
    public Result<?> startDelivery(@PathVariable @Parameter(description = "订单ID", required = true) Long id) {
        log.info("开始配送，ID：{}", id);
        orderService.startDelivery(id);
        return Result.success();
    }

    @PutMapping("/complete/{id}")
    @Operation(description = "完成订单")
    public Result<?> completeOrder(@PathVariable @Parameter(description = "订单ID", required = true) Long id) {
        log.info("完成订单，ID：{}", id);
        orderService.completeOrder(id);
        return Result.success();
    }
}
