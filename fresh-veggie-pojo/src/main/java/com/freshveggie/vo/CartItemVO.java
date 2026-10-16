package com.freshveggie.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 购物车项视图对象（购物车行 + 实时商品信息）。
 *
 * <h3>为什么需要这个 VO</h3>
 * 2026-09-28 购物车瘦身后，{@code cart_item} 只存
 * {@code user_id / product_id / number}，不再存商品快照
 * （原设计导致商品改价后订单金额按过期价计算）。
 *
 * <p>于是展示时需要把 {@code product} 的实时信息拼进来 —— 本类就是那个拼接结果。
 * 字段名刻意与瘦身前 {@code CartItem} 的 JSON 形状保持一致
 * （name / image / spec / unit / unitPrice / number / amount），
 * 前端契约的破坏面因此只限于「值不再来自快照」这一点。
 *
 * <p><b>amount 是实时计算值</b>：{@code unitPrice × number}，
 * 其中 unitPrice 取自 {@code product.price}。商品改价后，
 * 下一次查询购物车立即反映新价格 —— 这正是本次改动要的行为。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItemVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 购物车行主键 */
    private Long id;

    private Long userId;

    private Long productId;

    /** 商品名称（实时） */
    private String name;

    /** 商品图片（实时） */
    private String image;

    /** 规格（实时） */
    private String spec;

    /** 单位（实时） */
    private String unit;

    /** 单价（实时，来自 product.price） */
    private BigDecimal unitPrice;

    /** 数量（来自 cart_item） */
    private Integer number;

    /** 小计（实时计算 = unitPrice × number） */
    private BigDecimal amount;

    /** 商品当前库存（供前端提示"库存不足"，也便于排查） */
    private Integer stock;

    /** 商品是否上架：1 上架 0 下架 */
    private Integer productStatus;

    /** 加购时间（来自 cart_item） */
    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
