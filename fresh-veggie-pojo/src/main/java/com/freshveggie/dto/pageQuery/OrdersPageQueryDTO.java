package com.freshveggie.dto.pageQuery;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.validator.constraints.Range;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Data
@Schema(description = "订单分页查询模板")
public class OrdersPageQueryDTO extends PageQueryDTO{

    @Schema(description = "订单号")
    private String number;

    @Schema(description = "手机号码")
    private String phone;

    @Schema(description = "订单状态")
    @Range(min = 1, max = 5, message = "订单状态错误")
    private Integer status;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "下单时间")
    private LocalDateTime beginTime;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "下单时间")
    private LocalDateTime endTime;

    @Schema(description = "用户ID")
    private Long userId;
}
