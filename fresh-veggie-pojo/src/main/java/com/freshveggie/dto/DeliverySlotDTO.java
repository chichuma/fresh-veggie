package com.freshveggie.dto;

import com.freshveggie.valid.groups.Update;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.Range;

import java.io.Serializable;
import java.time.LocalTime;

/**
 * 配送时段模板。
 *
 * <p>注：{@code end_time > start_time} 的跨字段校验放在
 * {@code DeliverySlotServiceImpl#checkTimeRange} 里做，而不是用 DTO 注解 ——
 * 校验注解 {@code @MultiFieldAssociationCheck} 位于 {@code fresh-veggie-common}，
 * 而 {@code fresh-veggie-pojo} 在依赖链上位于 common **之下**（common 依赖 pojo），
 * 引不到它。放在 Service 层还有个好处：新增与修改两条路径共用同一份校验。
 */
@Data
@Schema(description = "配送时段模板")
public class DeliverySlotDTO implements Serializable {

    @Schema(description = "时段ID")
    @NotNull(groups = Update.class, message = "时段ID不能为空")
    private Long id;

    @Schema(description = "时段名", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "时段名不能为空")
    private String slotName;

    @Schema(description = "开始时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "开始时间不能为空")
    private LocalTime startTime;

    @Schema(description = "结束时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "结束时间不能为空")
    private LocalTime endTime;

    @Schema(description = "展示顺序")
    private Integer sort;

    @Schema(description = "状态 0停用 1启用", allowableValues = "0, 1")
    @Range(max = 1L, message = "状态错误")
    private Integer status;
}
