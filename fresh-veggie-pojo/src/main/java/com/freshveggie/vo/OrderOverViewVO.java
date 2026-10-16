package com.freshveggie.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 订单概览数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderOverViewVO implements Serializable {
    //备货中数量
    private Integer preparing;

    //配送中数量
    private Integer delivering;

    //已完成数量
    private Integer completed;

    //已取消数量
    private Integer cancelled;

    //全部订单
    private Integer allOrders;
}
