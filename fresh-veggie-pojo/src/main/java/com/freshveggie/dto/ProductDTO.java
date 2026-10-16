package com.freshveggie.dto;

import com.freshveggie.valid.groups.Update;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.hibernate.validator.constraints.Range;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@Schema(description = "商品模板")
public class ProductDTO implements Serializable {

    @Schema(description = "商品id")
    @NotNull(groups = Update.class, message = "商品ID不能为空")
    private Long id;

    //商品名称
    @Schema(description = "商品名称")
    @NotBlank(message = "商品名称不能为空")
    private String name;

    //商品分类id
    @Schema(description = "商品分类ID")
    @NotNull(message = "商品分类ID不能为空")
    private Long categoryId;

    //商品价格
    @Schema(description = "商品价格")
    @NotNull(message = "商品价格不能为空")
    @Range(message = "商品价格不合法")
    private BigDecimal price;

    //划线原价(促销展示)
    @Schema(description = "划线原价")
    private BigDecimal originalPrice;

    //单位 斤/个/盒/袋
    @Schema(description = "单位")
    private String unit;

    //净含量/规格 如 500g/盒
    @Schema(description = "规格")
    private String spec;

    //产地
    @Schema(description = "产地")
    private String origin;

    //保质期(天)
    @Schema(description = "保质期(天)")
    private Integer shelfLifeDays;

    //存储条件 1冷藏 2常温 3冷冻
    @Schema(description = "存储条件", allowableValues = "1,2,3")
    @Range(min = 1L, max = 3L, message = "存储条件只能是 1冷藏 / 2常温 / 3冷冻")
    private Integer storageType;

    //是否预售 0否 1是
    @Schema(description = "是否预售", allowableValues = "0,1")
    @Range(min = 0L, max = 1L, message = "是否预售只能是 0否 / 1是")
    private Integer isPresale;

    //可售库存
    @Schema(description = "可售库存")
    @Min(value = 0, message = "库存不能为负数")
    private Integer stock;

    //图片
    @Schema(description = "商品图片")
    @NotBlank(message = "商品图片不能为空")
    private String image;

    //描述信息
    @Schema(description = "商品描述")
    private String description;

    //0 下架 1 上架
    @Schema(description = "商品状态", allowableValues = "1,0")
    @Range(max = 1L, message = "商品状态不合法")
    private Integer status;

}
