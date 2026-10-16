package com.freshveggie.controller.user;

import com.freshveggie.context.BaseContext;
import com.freshveggie.entity.DeliveryAddress;
import com.freshveggie.result.Result;
import com.freshveggie.service.impl.DeliveryAddressServiceImpl;
import com.freshveggie.valid.groups.Add;
import com.freshveggie.valid.groups.Update;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@Slf4j
@Tag(name = "收货地址相关接口")
@Validated
@RequestMapping("/user/address")
@RequiredArgsConstructor
public class DeliveryAddressController {

    private final DeliveryAddressServiceImpl addressService;

    @PostMapping
    @Operation(description = "新增收货地址")
    public Result<?> saveAddress(@RequestBody @Validated(Add.class) DeliveryAddress address) {
        log.info("新增收货地址：{}", address);
        boolean result = addressService.saveAddress(address);
        return result ? Result.success() : Result.error("添加失败");
    }

    @GetMapping("/list")
    @Operation(description = "获取当前用户所有收货地址")
    public Result<List<DeliveryAddress>> getAllAddress() {
        log.info("查询当前用户所有收货地址，当前用户ID：{}", BaseContext.getCurrentId());
        List<DeliveryAddress> addresses = addressService.getAllAddress();
        return Result.success(addresses);
    }

    @GetMapping("/default")
    @Operation(description = "查询默认地址")
    public Result<DeliveryAddress> getDefaultAddress() {
        log.info("查询当前用户的默认收货地址, 当前用户ID：{}", BaseContext.getCurrentId());
        DeliveryAddress defaultAddress = addressService.getDefaultAddress();
        return Result.success(defaultAddress);
    }

    @PutMapping("/default")
    @Operation(description = "设置为默认地址")
    public Result<?> setDefaultAddress(@RequestBody DeliveryAddress address) {
        log.info("设置默认地址，地址ID：{}", address.getId());
        boolean result = addressService.setDefaultAddress(address.getId());
        return result ? Result.success() : Result.error("设置失败");
    }

    @GetMapping("/{id}")
    @Operation(description = "根据ID查询收货地址")
    public Result<DeliveryAddress> getAddressById(@PathVariable @Parameter(description = "地址ID", required = true) Long id) {
        log.info("根据ID查询地址，ID为：{}", id);
        DeliveryAddress address = addressService.getAddressById(id);
        return Result.success(address);
    }

    @DeleteMapping
    @Operation(description = "根据ID删除收货地址")
    public Result<?> deleteAddressById(@RequestParam @Parameter(description = "地址ID", required = true) Long id) {
        log.info("根据ID删除地址，ID为：{}", id);
        boolean result = addressService.deleteAddressById(id);
        return result ? Result.success() : Result.error("删除失败");
    }

    @PutMapping
    @Operation(description = "修改收货地址")
    public Result<?> updateAddress(@RequestBody @Validated(Update.class) DeliveryAddress address) {
        log.info("修改收货地址：{}", address);
        boolean result = addressService.updateAddress(address);
        return result ? Result.success() : Result.error("修改失败");
    }

}
