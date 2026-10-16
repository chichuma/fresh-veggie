package com.freshveggie.enums;

public enum OrderEvent {
    PAY,  // 支付
    START_DELIVERY,  // 开始配送(备货中 -> 配送中)
    RECEIVE,  // 确认收货/送达(配送中 -> 已完成)
    USER_CANCEL,  // 用户取消
    ADMIN_CANCEL // 后台取消
}
