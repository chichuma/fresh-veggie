package com.freshveggie.service;

import com.freshveggie.dto.CategoryDTO;
import com.freshveggie.dto.pageQuery.CategoryPageQueryDTO;
import com.freshveggie.entity.Category;
import com.freshveggie.result.PageResult;

import java.util.List;

public interface CategoryService {
    PageResult<Category> getCategoryList(CategoryPageQueryDTO categoryPageQueryDTO);

    boolean addCategory(CategoryDTO categoryDTO);

    boolean updateCategory(CategoryDTO categoryDTO);

    boolean delCategory(Long id);

    Category getCategoryById(Long id);

    int getCategoryByName(String categoryName);

    boolean changeCategoryStatus(Long id, Integer status);

    List<Category> listAllCategory();
}
