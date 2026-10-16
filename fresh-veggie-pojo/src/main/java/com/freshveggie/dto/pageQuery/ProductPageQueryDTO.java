package com.freshveggie.dto.pageQuery;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.validator.constraints.Range;

@EqualsAndHashCode(callSuper = true)
@Data
@Schema(description = "菜品分页查询模版")
public class ProductPageQueryDTO extends PageQueryDTO{

    @Schema(description = "查询菜品名称")
    private String name;

    @Schema(description = "分类ID")
    private Integer categoryId;

    //状态 0表示禁用 1表示启用
    @Schema(description = "菜品状态")
    @Range(max = 1L, message = "菜品状态不合法")
    private Integer status;

    //存储条件 1冷藏 2常温 3冷冻
    @Schema(description = "存储条件", allowableValues = "1,2,3")
    private Integer storageType;

    //是否预售 0否 1是
    @Schema(description = "是否预售", allowableValues = "0,1")
    private Integer isPresale;

}