package com.freshveggie.vo;

import lombok.Data;
import java.io.Serializable;

@Data
public class OrderStatisticsVO implements Serializable {

    //待支付数量
    private Integer pendingPayment;

    //备货中数量
    private Integer preparing;

    //配送中数量
    private Integer delivering;
}
