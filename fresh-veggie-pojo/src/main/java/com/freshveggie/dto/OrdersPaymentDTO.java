package com.freshveggie.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.Range;

import java.io.Serializable;

@Data
@Schema(description = "订单支付请求模版")
public class OrdersPaymentDTO implements Serializable {

    //订单号
    @Schema(description = "订单号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "订单号不能为空")
    private String orderNumber;

    //付款方式
    @Schema(description = "付款方式", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "付款方式不能为空")
    @Range(min = 1L, max = 2L, message = "付款方式错误")
    private Integer payMethod = 1;

}
