package com.freshveggie.controller.user;

import com.freshveggie.entity.Category;
import com.freshveggie.result.Result;
import com.freshveggie.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController("userCategoryController")
@RequestMapping("/user/category")
@Tag(name = "分类相关接口")
@Slf4j
@Validated
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping("/list")
    @Operation(description = "用户端查询分类")
    public Result<List<Category>> list(
    ) {
        log.info("用户端查询所有分类");
        List<Category> categories = categoryService.listAllCategory();
        return Result.success(categories);
    }
}
