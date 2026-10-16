package com.freshveggie.enums;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Getter
public enum OrderStatus {
    PENDING_PAYMENT(1),  // 待支付
    PREPARING(2),  // 备货中
    DELIVERING(3),  // 配送中
    COMPLETED(4),  // 已完成
    CANCELLED(5);  // 已取消

    private final Integer state;

    OrderStatus(Integer state) {
        this.state = state;
    }

    private static final Map<Integer, OrderStatus> STATE_MAP = new HashMap<>();

    // 初始化 STATE_MAP
    static {
        for (OrderStatus orderStatus : OrderStatus.values()) {
            STATE_MAP.put(orderStatus.getState(), orderStatus);
        }
    }

    public static OrderStatus fromState(Integer state) {
        return STATE_MAP.get(state);
    }
}
