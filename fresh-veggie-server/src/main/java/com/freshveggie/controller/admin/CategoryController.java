package com.freshveggie.controller.admin;

import com.freshveggie.dto.CategoryDTO;
import com.freshveggie.dto.pageQuery.CategoryPageQueryDTO;
import com.freshveggie.entity.Category;
import com.freshveggie.result.PageResult;
import com.freshveggie.result.Result;
import com.freshveggie.service.CategoryService;
import com.freshveggie.valid.groups.Add;
import com.freshveggie.valid.groups.Update;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.Range;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/category")
@Slf4j
@Tag(name = "分类接口")
@Validated
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping("/page")
    @Operation(description = "分类分页查询")
    public Result<PageResult<Category>> page(CategoryPageQueryDTO categoryPageQueryDTO) {
        log.info("分类分页查询：{}", categoryPageQueryDTO);
        PageResult<Category> categoryPageResult = categoryService.getCategoryList(categoryPageQueryDTO);
        return Result.success(categoryPageResult);
    }

    @PostMapping
    @Operation(description = "新增分类")
    public Result<?> save(@RequestBody @Validated(Add.class) CategoryDTO categoryDTO) {
        log.info("新增分类：{}", categoryDTO);
        boolean result = categoryService.addCategory(categoryDTO);
        return result ? Result.success() : Result.error("新增分类失败");
    }

    @PutMapping
    @Operation(description = "修改分类")
    public Result<?> update(@RequestBody @Validated(Update.class) CategoryDTO categoryDTO) {
        log.info("修改分类：{}", categoryDTO);
        boolean result = categoryService.updateCategory(categoryDTO);
        return result ? Result.success() : Result.error("修改分类失败");
    }

    @DeleteMapping
    @Operation(description = "删除分类")
    public Result<?> delete(@RequestParam @Parameter(description = "分类ID", required = true) Long id) {
        log.info("删除分类：{}", id);
        boolean result = categoryService.delCategory(id);
        return result ? Result.success() : Result.error("删除分类失败");
    }

    @PostMapping("/status/{status}")
    @Operation(description = "修改分类状态")
    public Result<?> changeStatus(

            @PathVariable
            @Parameter(description = "分类状态", required = true)
            @Schema(allowableValues = "0, 1")
            @Range(max = 1L, message = "status不合法")
            Integer status,

            @RequestParam
            @Parameter(description = "分类ID", required = true)
            Long id
    ) {
        log.info("修改分类状态：id={}, status={}", id, status);
        boolean result = categoryService.changeCategoryStatus(id, status);
        return result ? Result.success() : Result.error("修改分类状态失败");
    }

    /**
     * 全部分类——注意：这里只返回**启用中**的分类（listAllCategory 带 status=ENABLE 过滤）。
     * 商品表单的分类下拉若需要包含禁用分类，应改用 /admin/category/page（返回全部状态）。
     */
    @GetMapping("/list")
    @Operation(description = "查询启用中的分类（不含禁用分类）")
    public Result<List<Category>> listAll() {
        log.info("查询启用中的分类");
        return Result.success(categoryService.listAllCategory());
    }

}