package com.freshveggie.dto.pageQuery;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@Schema(description = "员工分页查询模板")
public class  StaffPageQueryDTO extends PageQueryDTO{

    //员工姓名
    @Schema(description = "员工姓名")
    private String name;
}
