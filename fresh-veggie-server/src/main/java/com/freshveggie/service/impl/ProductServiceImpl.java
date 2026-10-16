package com.freshveggie.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freshveggie.constant.RedisConstant;
import com.freshveggie.dto.ProductDTO;
import com.freshveggie.dto.pageQuery.ProductPageQueryDTO;
import com.freshveggie.entity.Category;
import com.freshveggie.entity.Product;
import com.freshveggie.exception.BusinessException;
import com.freshveggie.exception.DeletionNotAllowedException;
import com.freshveggie.mapper.CategoryMapper;
import com.freshveggie.mapper.ProductMapper;
import com.freshveggie.result.PageResult;
import com.freshveggie.service.ProductService;
import com.freshveggie.utils.AliOssUtil;
import com.freshveggie.vo.ProductVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final AliOssUtil aliOssUtil;
    private final ProductMapper productMapper;
    private final CategoryMapper categoryMapper;

    @Override
    public PageResult<ProductVO> getProductList(ProductPageQueryDTO dto) {
        Page<ProductVO> page = new Page<>(dto.getPage(), dto.getPageSize());
        IPage<ProductVO> result = productMapper.getProductVoList(page, dto);
        return new PageResult<>(result.getTotal(), result.getRecords(), result.getSize(), result.getCurrent());
    }

    @Override
    public boolean saveProduct(ProductDTO productDTO) {
        Long count = productMapper.selectCount(Wrappers.<Product>lambdaQuery()
                .eq(Product::getName, productDTO.getName()));
        if (count != null && count > 0) {
            throw new BusinessException("商品名称重复");
        }
        Category category = categoryMapper.selectById(productDTO.getCategoryId());
        if (category == null) {
            throw new BusinessException("分类ID不存在");
        }
        Product product = new Product();
        BeanUtils.copyProperties(productDTO, product);
        int row = productMapper.insert(product);
        deleteAllProductCache();
        return row > 0;
    }

    @Override
    @Transactional
    public boolean deleteProductByIds(List<Long> ids) {
        // 起售中的商品不允许删除
        List<Long> sellingProductIds = productMapper.getSellingProductListByIds(ids);
        if (!sellingProductIds.isEmpty()) {
            throw new DeletionNotAllowedException("删除失败，商品ID为：" + sellingProductIds + " 状态为起售中");
        }
        List<String> images = productMapper.getProductImagesByIds(ids);
        int row = productMapper.deleteByIds(ids);
        deleteAllProductCache();
        aliOssUtil.deleteFileBatch(images);
        return row >= 0;
    }

    @Override
    public ProductVO getProductVOById(Long id) {
        ProductVO productVO = productMapper.getProductVOById(id);
        if (productVO == null) {
            throw new BusinessException("商品ID不存在");
        }
        return productVO;
    }

    @Override
    @Transactional
    public boolean updateProduct(ProductDTO productDTO) {
        Product product = productMapper.selectById(productDTO.getId());
        if (product == null) {
            throw new BusinessException("商品ID不存在");
        }
        if (!productDTO.getName().equals(product.getName())) {
            Long count = productMapper.selectCount(Wrappers.<Product>lambdaQuery()
                    .eq(Product::getName, productDTO.getName()));
            if (count != null && count > 0) {
                throw new BusinessException("商品名称重复");
            }
        }
        if (product.getImage() != null && !product.getImage().equals(productDTO.getImage())) {
            aliOssUtil.deleteFile(product.getImage());
        }
        BeanUtils.copyProperties(productDTO, product);
        int row = productMapper.updateById(product);
        deleteAllProductCache();
        return row > 0;
    }

    @Override
    public boolean updateProductStatus(Long id, Integer status) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException("商品ID不存在");
        }
        product.setStatus(status);
        int row = productMapper.updateById(product);
        deleteAllProductCache();
        return row > 0;
    }

    @Override
    public List<Product> getProductListByCategoryId(Long categoryId) {
        return productMapper.selectList(Wrappers.<Product>lambdaQuery()
                .eq(Product::getCategoryId, categoryId)
                .eq(Product::getStatus, 1));
    }

    @Override
    public List<ProductVO> getProductVoListByCategoryId(Long categoryId) {
        String jsonStr = (String) redisTemplate.opsForHash().get(RedisConstant.SHOP_CATEGORY_PRODUCTS, categoryId.toString());
        if (jsonStr == null) {
            List<ProductVO> list = productMapper.getProductVoListByCategoryId(categoryId);
            try {
                redisTemplate.opsForHash().put(RedisConstant.SHOP_CATEGORY_PRODUCTS, categoryId.toString(),
                        objectMapper.writeValueAsString(list));
            } catch (JsonProcessingException e) {
                log.error("DB返回的商品视图转JSON异常", e);
            }
            return list;
        }
        try {
            return objectMapper.readValue(jsonStr, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            log.error("JSON转商品视图异常", e);
            return new ArrayList<>();
        }
    }

    private void deleteAllProductCache() {
        redisTemplate.delete(RedisConstant.SHOP_CATEGORY_PRODUCTS);
    }
}
