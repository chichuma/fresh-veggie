package com.freshveggie.dto;

import lombok.Data;
import org.hibernate.validator.constraints.Range;

import java.io.Serializable;

@Data
public class CartItemDTO implements Serializable {

    @Range(message = "productId错误")
    private Long productId;

    @Range(min = 1L, max = 9999L, message = "数量错误")
    private Integer number;

}
