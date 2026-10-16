package com.freshveggie.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.Range;

import java.io.Serializable;
import java.time.LocalDate;

@Data
@Schema(description = "订单提交模型")
public class OrdersSubmitDTO implements Serializable {

    //地址ID
    @Schema(description = "地址ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "地址ID不能为空")
    private Long deliveryAddressId;

    //配送日期(次日达)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    @Schema(description = "配送日期", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "配送日期不能为空")
    private LocalDate deliveryDate;

    //配送时段ID
    @Schema(description = "配送时段ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "配送时段不能为空")
    private Long deliverySlotId;

    //付款方式, 默认为1：微信支付
    @Schema(description = "支付方式", allowableValues = "1, 2", requiredMode = Schema.RequiredMode.REQUIRED)
    @Range(min = 1L, max = 1L, message = "支付方式暂只支持 1微信")
    private Integer payMethod = 1;

    //备注
    @Schema(description = "订单备注")
    private String remark;
}
