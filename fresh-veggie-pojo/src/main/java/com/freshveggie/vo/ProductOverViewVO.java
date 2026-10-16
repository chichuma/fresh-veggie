package com.freshveggie.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 菜品总览
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
    // 已启售数量
public class ProductOverViewVO implements Serializable {
    private Integer sold;

    // 已停售数量
    private Integer discontinued;
}
