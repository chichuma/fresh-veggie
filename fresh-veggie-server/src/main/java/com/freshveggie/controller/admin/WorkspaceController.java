package com.freshveggie.controller.admin;

import com.freshveggie.result.Result;
import com.freshveggie.service.WorkSpaceService;
import com.freshveggie.vo.BusinessDataVO;
import com.freshveggie.vo.OrderOverViewVO;
import com.freshveggie.vo.ProductOverViewVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
@Tag(name = "工作台相关接口")
@RequestMapping("/admin/workspace")
@Validated
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkSpaceService workSpaceService;

    @GetMapping("/overviewOrders")
    @Operation(description = "查询订单管理数据")
    public Result<OrderOverViewVO> getOverviewOrders() {
        log.info("查询订单管理数据");
        OrderOverViewVO orderOverViewVO = workSpaceService.getOverviewOrders();
        return Result.success(orderOverViewVO);
    }

    @GetMapping("/overviewProductes")
    @Operation(description = "查询菜品总览")
    public Result<ProductOverViewVO> getOverviewProductes() {
        log.info("查询菜品总览");
        ProductOverViewVO productOverViewVO = workSpaceService.getOverviewProductes();
        return Result.success(productOverViewVO);
    }

    @GetMapping("/businessData")
    @Operation(description = "查询运营数据")
    public Result<BusinessDataVO> getBusinessData() {
        log.info("查询今日运营数据");
        BusinessDataVO businessDataVO = workSpaceService.getBusinessData();
        return Result.success(businessDataVO);
    }
}
