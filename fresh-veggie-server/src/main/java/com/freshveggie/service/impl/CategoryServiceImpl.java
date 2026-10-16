package com.freshveggie.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.freshveggie.constant.StatusConstant;
import com.freshveggie.dto.CategoryDTO;
import com.freshveggie.dto.pageQuery.CategoryPageQueryDTO;
import com.freshveggie.entity.Category;
import com.freshveggie.entity.Product;
import com.freshveggie.exception.BusinessException;
import com.freshveggie.exception.DeletionNotAllowedException;
import com.freshveggie.mapper.CategoryMapper;
import com.freshveggie.mapper.ProductMapper;
import com.freshveggie.result.PageResult;
import com.freshveggie.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryMapper categoryMapper;
    private final ProductMapper productMapper;

    /**
     * 模糊查询分类
     * <p>
     * 从传入的 Page-dto 获取页信息
     * 使用 dto 里面的 name 字段，模糊匹配 %name%
     *
     * @param dto 里面可用字段为 name
     * @return 查询页
     */
    @Override
    public PageResult<Category> getCategoryList(CategoryPageQueryDTO dto) {
        Page<Category> page = new Page<>(dto.getPage(), dto.getPageSize());
        Page<Category> result = categoryMapper.selectPage(page, Wrappers.<Category>lambdaQuery()
                .like(dto.getName() != null && !dto.getName().isEmpty(), Category::getName, dto.getName()));
        return new PageResult<>(result.getTotal(), result.getRecords(), result.getSize(), result.getCurrent());
    }

    /**
     * 新建分类
     * <p>
     * 使用 dto 里面的 name 字段，查重
     *
     * @param dto 里面可用字段为 name
     * @return 查询结果
     */
    @Override
    public boolean addCategory(CategoryDTO dto) {
        Long count = categoryMapper.selectCount(Wrappers.<Category>lambdaQuery().eq(Category::getName, dto.getName()));
        if (count != null && count > 0) {
            throw new BusinessException("分类名称已存在，请重新输入");
        }
        Category category = new Category();
        BeanUtils.copyProperties(dto, category);
        category.setStatus(StatusConstant.DISABLE);
        return categoryMapper.insert(category) > 0;
    }

    /**
     * 更新分类
     * <p>
     * 使用 dto 里面的 id 字段，查询具体行，然后更新
     *
     * @param dto 里面可用字段为 id、name
     * @return 更新结果
     */
    @Override
    public boolean updateCategory(CategoryDTO dto) {
        Category category = categoryMapper.selectById(dto.getId());
        if (category == null) {
            throw new BusinessException("分类ID不存在");
        }
        if (!dto.getName().equals(category.getName())) {
            Long count = categoryMapper.selectCount(Wrappers.<Category>lambdaQuery().eq(Category::getName, dto.getName()));
            if (count != null && count > 0) {
                throw new BusinessException("分类名称已存在，请重新输入");
            }
        }
        BeanUtils.copyProperties(dto, category);
        return categoryMapper.updateById(category) > 0;
    }

    /**
     * 删除分类
     * <p>
     * 使用 id 准确查询，同时判断当前分类有无关联商品
     *
     * @param id 分类ID
     * @return 删除结果
     */
    @Override
    public boolean delCategory(Long id) {
        Category category = getCategoryById(id);
        if (category == null) {
            throw new BusinessException("分类ID不存在");
        }
        Long productCount = productMapper.selectCount(Wrappers.<Product>lambdaQuery()
                .eq(Product::getCategoryId, id));
        if (productCount != null && productCount > 0) {
            throw new DeletionNotAllowedException("删除失败，当前分类下存在商品，请检查");
        }
        return categoryMapper.deleteById(id) > 0;
    }

    /**
     * 修改分类状态
     * <p>
     * 使用 id 准确查询
     *
     * @param id 分类ID
     * @param status 状态
     * @return 修改结果
     */
    @Override
    public boolean changeCategoryStatus(Long id, Integer status) {
        Category category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BusinessException("分类ID不存在");
        }
        category.setStatus(status);
        return categoryMapper.updateById(category) > 0;
    }

    @Override
    public List<Category> listAllCategory() {
        return categoryMapper.selectList(Wrappers.<Category>lambdaQuery()
                .eq(Category::getStatus, StatusConstant.ENABLE)
                .orderByAsc(Category::getSort));
    }

    @Override
    public Category getCategoryById(Long id) {
        return categoryMapper.selectById(id);
    }

    @Override
    public int getCategoryByName(String categoryName) {
        Long count = categoryMapper.selectCount(Wrappers.<Category>lambdaQuery().eq(Category::getName, categoryName));
        return count == null ? 0 : count.intValue();
    }
}
