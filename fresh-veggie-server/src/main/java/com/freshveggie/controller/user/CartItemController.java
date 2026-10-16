package com.freshveggie.controller.user;

import com.freshveggie.context.BaseContext;
import com.freshveggie.dto.CartItemDTO;
import com.freshveggie.entity.CartItem;
import com.freshveggie.vo.CartItemVO;
import com.freshveggie.result.Result;
import com.freshveggie.service.CartItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Slf4j
@Validated
@RequestMapping("/user/cart")
@Tag(name = "购物车相关接口")
@RequiredArgsConstructor
public class CartItemController {

    private final CartItemService cartItemService;

    @PostMapping("/add")
    @Operation(description = "添加购物车")
    public Result<?> add(@RequestBody @Valid CartItemDTO cartItemDTO) {
        log.info("添加购物车，商品信息为：{}", cartItemDTO);
        boolean result = cartItemService.addCartItem(cartItemDTO);
        return result ? Result.success() : Result.error("添加失败");
    }

    @GetMapping("/list")
    @Operation(description = "获取购物车列表")
    public Result<List<CartItemVO>> listCartItem() {
        log.info("获取购物车列表, 用户ID：{}", BaseContext.getCurrentId());
        List<CartItemVO> cartItemList = cartItemService.listCartItem();
        return Result.success(cartItemList);
    }

    @DeleteMapping("/clean")
    @Operation(description = "清空购物车")
    public Result<?> clearCartItem() {
        log.info("清空购物车列表，用户ID：{}", BaseContext.getCurrentId());
        boolean result = cartItemService.clearCartItem();
        return result ? Result.success() : Result.error("清空失败");
    }

    @PostMapping("/sub")
    @Operation(description = "删除购物车")
    public Result<?> sub(@RequestBody @Valid CartItemDTO cartItemDTO) {
        log.info("删除购物车，商品信息为：{}", cartItemDTO);
        boolean result = cartItemService.subCartItem(cartItemDTO);
        return result ? Result.success() : Result.error("删除失败");
    }
}
