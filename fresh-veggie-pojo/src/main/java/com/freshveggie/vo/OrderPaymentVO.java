package com.freshveggie.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.io.Serial;

/**
 * 微信小程序支付所需的签名参数（占位）。
 *
 * <h3>2026-10-01（P2-14）：这是一个「孤儿 VO」，请注意</h3>
 * 项目<b>没有接入微信支付</b>，{@code OrderController#payment} 只是调用
 * {@code orderService.payment(...)} 把订单置为已支付，但**从未构造本类实例**
 * —— 接口声明返回 {@code Result<OrderPaymentVO>}，实际 {@code data} 恒为
 * {@code null}。
 *
 * <p>保留它的原因：这 5 个字段正是微信小程序 {@code wx.requestPayment}
 * 需要的入参，将来接入时直接填充即可，不必重新设计。
 *
 * <p>排查提示：如果前端拿到 {@code data: null} 以为出错 —— 那是预期行为，
 * 本项目支付是"直接成功"的模拟实现，不返回签名参数。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderPaymentVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String nonceStr; //随机字符串
    private String paySign; //签名
    private String timeStamp; //时间戳
    private String signType; //签名算法
    private String packageStr; //统一下单接口返回的 prepay_id 参数值

}
