package com.freshveggie.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItem implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    //主键
    private Long id;

    //订单id
    private Long orderId;

    //商品id
    private Long productId;

    //商品名快照
    private String name;

    //图片快照
    private String image;

    //规格快照
    private String spec;

    //单位快照
    private String unit;

    //产地快照
    private String origin;

    //单价快照
    private BigDecimal unitPrice;

    //数量
    private Integer number;

    //小计
    private BigDecimal amount;
}
