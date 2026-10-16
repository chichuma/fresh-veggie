package com.freshveggie.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 购物车项。
 *
 * <h3>2026-09-28 瘦身：只保留「谁 + 买了什么 + 几件」</h3>
 * 原本这张表还存着加购时刻的商品快照（name / image / spec / unit / unit_price / amount）。
 * 那个设计带来一个真实的金额 bug：
 * 商品改价后，下单金额仍按 {@code cart_item.unit_price} 这个【过期价】计算，
 * 与商品实际价格对不上。
 *
 * <p>现在商品信息一律从 {@code product} 表实时读取，改价立即生效。
 * <b>快照职责归还给 {@link OrderItem}</b> —— 它才是「下单那一刻定格」的正确位置。
 *
 * @see com.freshveggie.service.impl.CartItemServiceImpl 展示时回填商品信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItem implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    //主键
    private Long id;

    //用户ID
    private Long userId;

    //商品ID（商品名称/价格/规格等一律实时查 product，不在本表存快照）
    private Long productId;

    //数量
    private Integer number;

    //创建时间
    private LocalDateTime createTime;

    //更新时间
    private LocalDateTime updateTime;
}
