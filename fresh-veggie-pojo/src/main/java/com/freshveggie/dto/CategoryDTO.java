package com.freshveggie.dto;

import com.freshveggie.valid.groups.Update;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

@Data
@Schema(description = "分类模板")
public class CategoryDTO implements Serializable {

    //主键
    @Schema(description = "分类ID")
    @NotNull(groups = Update.class, message = "分类ID不能为空")
    private Long id;

    //分类名称
    @Schema(description = "分类名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "分类名称不能为空")
    private String name;

    //排序
    @Schema(description = "排序值", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "排序值不能为空")
    private Integer sort;

}
