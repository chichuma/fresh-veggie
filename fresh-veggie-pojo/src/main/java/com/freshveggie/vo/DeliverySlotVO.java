package com.freshveggie.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliverySlotVO implements Serializable {

    private Long id;

    //时段名
    private String slotName;

    //开始时间
    private LocalTime startTime;

    //结束时间
    private LocalTime endTime;
}
