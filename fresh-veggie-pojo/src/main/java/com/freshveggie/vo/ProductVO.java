package com.freshveggie.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductVO implements Serializable {

    private Long id;
    //商品名称
    private String name;
    //商品分类id
    private Long categoryId;
    //分类名称
    private String categoryName;
    //售价
    private BigDecimal price;
    //划线原价
    private BigDecimal originalPrice;
    //单位 斤/个/盒/袋
    private String unit;
    //规格 500g/盒
    private String spec;
    //产地
    private String origin;
    //保质期(天)
    private Integer shelfLifeDays;
    //存储条件 1冷藏 2常温 3冷冻
    private Integer storageType;
    //是否预售
    private Integer isPresale;
    //可售库存
    private Integer stock;
    //销量
    private Integer salesCount;
    //图片
    private String image;
    //描述信息
    private String description;
    //0 下架 1 上架
    private Integer status;
    //更新时间
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

}
