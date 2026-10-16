package com.freshveggie.controller.admin;

import com.freshveggie.result.Result;
import com.freshveggie.service.ShopService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.Range;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController("adminShopController")
@Slf4j
@Tag(name = "店铺操作接口")
@RequestMapping("/admin/shop")
@Validated
@RequiredArgsConstructor
public class ShopController {

    private final ShopService shopService;

    @GetMapping("/status")
    @Operation(description = "后台获取营业状态")
    public Result<Integer> getShopStatus() {
        Integer shopStatus = shopService.getShopStatus();
        log.info("后台获取店铺营业状态: {}", shopStatus == 1 ? "营业中" : "打烊中");
        return Result.success(shopStatus);
    }

    @PutMapping("/{status}")
    @Operation(description = "设置营业状态")
    public Result<?> setShopStatus(@PathVariable @Range(max = 1L, message = "status不合法") Integer status) {
        log.info("后台设置店铺营业状态：{}", status == 1 ? "营业中" : "打烊中");
        shopService.setShopStatus(status);
        return Result.success();
    }
}
