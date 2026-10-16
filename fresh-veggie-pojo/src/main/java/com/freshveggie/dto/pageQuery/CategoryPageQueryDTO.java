package com.freshveggie.dto.pageQuery;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@Schema(description = "分类分页查询模板")
public class CategoryPageQueryDTO extends PageQueryDTO{

    @Schema(description = "分类名称")
    private String name;
}
