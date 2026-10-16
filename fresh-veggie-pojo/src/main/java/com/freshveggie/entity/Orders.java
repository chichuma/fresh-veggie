package com.freshveggie.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Orders implements Serializable {


    /**
     * 订单状态 1待支付 2备货中 3配送中 4已完成 5已取消
     */
    public static final Integer PENDING_PAYMENT = 1;
    public static final Integer PREPARING = 2;
    public static final Integer DELIVERING = 3;
    public static final Integer COMPLETED = 4;
    public static final Integer CANCELLED = 5;

    /**
     * 支付状态 0未支付 1已支付 2退款
     */
    public static final Integer UN_PAID = 0;
    public static final Integer PAID = 1;
    public static final Integer REFUND = 2;


    @Serial
    private static final long serialVersionUID = 1L;

    //主键
    private Long id;

    //订单号
    private String orderNo;

    //订单状态 1待支付 2备货中 3配送中 4已完成 5已取消
    private Integer status;

    //下单用户
    private Long userId;

    //收货地址id
    private Long deliveryAddressId;

    //支付方式 1微信
    private Integer payMethod;

    //支付状态 0未支付 1已支付 2退款
    private Integer payStatus;

    //收货人快照
    private String consignee;

    //手机号快照
    private String phone;

    //地址快照(省市区+详址)
    private String address;

    //配送日期(次日达)
    private LocalDate deliveryDate;

    /**
     * 配送时段开始时间（下单时的快照）。
     *
     * <h3>2026-09-28：为什么不再存 delivery_slot_id + slot_name</h3>
     * 订单是快照，而原来的两个字段是【对 delivery_slot 表的引用】：
     * 一旦时段行被改名或删除，历史订单展示出来的就是错的时段名
     * （删除路径还会被 {@code fk_orders_slot} 直接挡住）。
     *
     * <p>改存起止时间后，订单自带完整的时段语义，不依赖 delivery_slot
     * 表是否还存在 —— 与 consignee / phone / address 三个快照列同一个思路。
     */
    private LocalTime startTime;

    /**
     * 配送时段结束时间（下单时的快照）。
     *
     * @see #startTime
     */
    private LocalTime endTime;

    //商品总额
    private BigDecimal goodsAmount;

    //配送费
    private BigDecimal deliveryFee;

    //应付总额
    private BigDecimal totalAmount;

    //是否预售 0否 1是
    private Integer isPresale;

    //备注
    private String remark;

    //取消/拒单原因
    private String cancelReason;

    //取消时间
    private LocalDateTime cancelTime;

    //支付时间
    private LocalDateTime paidTime;

    //送达时间
    private LocalDateTime deliveredTime;

    //完成时间
    private LocalDateTime completedTime;

    //下单时间(创建时间)
    private LocalDateTime createTime;

    //更新时间
    private LocalDateTime updateTime;
}
