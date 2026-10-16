package com.freshveggie.controller.user;

import com.freshveggie.result.Result;
import com.freshveggie.service.ProductService;
import com.freshveggie.vo.ProductVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController("userProductController")
@RequestMapping("/user/product")
@Slf4j
@Tag(name = "菜品相关接口")
@Validated
@RequiredArgsConstructor
public class ProductController {
    private final ProductService productService;

    /**
     * 根据分类id查询菜品
     *
     * @param categoryId 分类ID
     * @return 该分类下启售的菜品列表
     */
    @GetMapping("/list")
    @Operation(description = "根据分类id查询菜品")
    public Result<List<ProductVO>> list(@RequestParam Long categoryId) {
        log.info("根据分类ID查找菜品，分类ID：{}", categoryId);
        List<ProductVO> productVOList = productService.getProductVoListByCategoryId(categoryId);
        return Result.success(productVOList);
    }

}
