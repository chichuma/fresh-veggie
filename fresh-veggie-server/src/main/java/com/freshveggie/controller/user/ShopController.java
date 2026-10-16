package com.freshveggie.controller.user;

import com.freshveggie.result.Result;
import com.freshveggie.service.ShopService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController("userShopController")
@Slf4j
@Tag(name = "店铺相关接口")
@RequestMapping("/user/shop")
@RequiredArgsConstructor
public class ShopController {

    private final ShopService shopService;

    @GetMapping("/status")
    @Operation(description = "用户端获取营业状态")
    public Result<Integer> getShopStatus() {
        return Result.success(shopService.getShopStatus());
    }
}
