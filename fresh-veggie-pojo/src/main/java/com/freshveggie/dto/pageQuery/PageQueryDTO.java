package com.freshveggie.dto.pageQuery;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.hibernate.validator.constraints.Range;

import java.io.Serializable;

@Data
@Schema(description = "分页查询基类")
public abstract class PageQueryDTO implements Serializable {

    @Schema(description = "页码", defaultValue = "1")
    @Range(min = 1, max = Integer.MAX_VALUE, message = "页码错误")
    private int page = 1;

    @Schema(description = "每页记录数", defaultValue = "10")
    @Range(min = 1, max = Integer.MAX_VALUE, message = "每页记录数错误")
    private int pageSize = 10;
}