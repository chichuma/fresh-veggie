package com.freshveggie.service;

import com.freshveggie.dto.ProductDTO;
import com.freshveggie.dto.pageQuery.ProductPageQueryDTO;
import com.freshveggie.entity.Product;
import com.freshveggie.result.PageResult;
import com.freshveggie.vo.ProductVO;

import java.util.List;

public interface ProductService {
    PageResult<ProductVO> getProductList(ProductPageQueryDTO productPageQueryDTO);

    boolean saveProduct(ProductDTO productDTO);

    boolean deleteProductByIds(List<Long> ids);

    ProductVO getProductVOById(Long id);

    boolean updateProduct(ProductDTO productDTO);

    boolean updateProductStatus(Long id, Integer status);

    List<Product> getProductListByCategoryId(Long categoryId);

    List<ProductVO> getProductVoListByCategoryId(Long categoryId);
}
