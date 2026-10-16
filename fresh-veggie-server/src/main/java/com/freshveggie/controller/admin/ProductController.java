package com.freshveggie.controller.admin;

import com.freshveggie.dto.ProductDTO;
import com.freshveggie.dto.pageQuery.ProductPageQueryDTO;
import com.freshveggie.entity.Product;
import com.freshveggie.result.PageResult;
import com.freshveggie.result.Result;
import com.freshveggie.service.ProductService;
import com.freshveggie.valid.groups.Add;
import com.freshveggie.valid.groups.Update;
import com.freshveggie.vo.ProductVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.Range;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Slf4j
@RequestMapping("/admin/product")
@Validated
@Tag(name = "菜品相关接口")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @Operation(description = "新增菜品")
    @PostMapping
    public Result<?> saveProduct(@RequestBody @Validated(Add.class) ProductDTO productDTO) {
        boolean result = productService.saveProduct(productDTO);
        return result ? Result.success() : Result.error("添加菜品失败");
    }

    @Operation(description = "分页查询菜品列表")
    @GetMapping("/page")
    public Result<PageResult<ProductVO>> getProductList(@Valid ProductPageQueryDTO productPageQueryDTO) {
        PageResult<ProductVO> productVOPageResult = productService.getProductList(productPageQueryDTO);
        return Result.success(productVOPageResult);
    }

    @Operation(description = "删除菜品")
    @DeleteMapping
    public Result<?> deleteProduct(
            @RequestParam
            @Parameter(description = "需要删除的菜品ID")
            @NotEmpty(message = "菜品ID不能为空")
            List<Long> ids
    ) {
        log.info("删除菜品：{}", ids);
        boolean result = productService.deleteProductByIds(ids);
        return result ? Result.success() : Result.error("删除失败");
    }

    @Operation(description = "根据ID查询菜品")
    @GetMapping("{id}")
    public Result<ProductVO> getProductVOById(@PathVariable Long id) {
        log.info("查询菜品：{}", id);
        ProductVO productVO = productService.getProductVOById(id);
        return Result.success(productVO);
    }

    @Operation(description = "修改菜品")
    @PutMapping
    public Result<?> updateProduct(@RequestBody @Validated(Update.class) ProductDTO productDTO) {
        log.info("更新菜品：{}", productDTO);
        boolean result = productService.updateProduct(productDTO);
        return result ? Result.success() : Result.error("更新失败");
    }

    @Operation(description = "菜品起售、停售")
    @PostMapping("/status/{status}")
    public Result<?> updateProductStatus(
            @PathVariable
            @Range(max = 1L, message = "菜品状态不合法")
            @Parameter(description = "菜品状态", required = true)
            @Schema(allowableValues = "0, 1")
            Integer status,

            @Parameter(description = "菜品ID", required = true)
            @RequestParam
            Long id
    ) {
        log.info("修改菜品状态：id: {}, status: {}", id, status);
        boolean result = productService.updateProductStatus(id, status);
        return result ? Result.success() : Result.error("更新失败");
    }

    @Operation(description = "根据分类ID查询菜品")
    @GetMapping("/list")
    public Result<List<Product>> listProductByCategoryId(@RequestParam Long categoryId) {
        log.info("根据分类ID查询菜品：{}", categoryId);
        List<Product> products = productService.getProductListByCategoryId(categoryId);
        return Result.success(products);
    }
}
