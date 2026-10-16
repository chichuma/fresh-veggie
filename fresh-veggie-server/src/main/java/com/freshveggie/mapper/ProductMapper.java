package com.freshveggie.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.freshveggie.dto.pageQuery.ProductPageQueryDTO;
import com.freshveggie.entity.Product;
import com.freshveggie.vo.ProductOverViewVO;
import com.freshveggie.vo.ProductVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 生鲜商品 Mapper; 联表/聚合查询保留在 ProductMapper.xml。
 */
@Mapper
public interface ProductMapper extends BaseMapper<Product> {

    // 分页条件获取商品列表(含分类名,联表)
    IPage<ProductVO> getProductVoList(IPage<ProductVO> page, @Param("query") ProductPageQueryDTO productPageQueryDTO);

    // 通过 id 获取商品详情(含分类名)
    ProductVO getProductVOById(Long id);

    // 通过 categoryId 获取启售商品列表(含分类名)
    List<ProductVO> getProductVoListByCategoryId(@Param("categoryId") Long categoryId);

    // 通过 ids 获取启售商品 id 列表(删除/停售前上架校验)
    List<Long> getSellingProductListByIds(@Param("ids") List<Long> ids);

    // 通过 ids 获取商品图片路径(删除时清理 OSS)
    List<String> getProductImagesByIds(@Param("ids") List<Long> ids);

    // 获取启售/停售商品数量(工作台统计)
    ProductOverViewVO getAllStatusProductesCount();
}
