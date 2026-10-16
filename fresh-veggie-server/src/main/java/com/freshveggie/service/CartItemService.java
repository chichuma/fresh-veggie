package com.freshveggie.service;

import com.freshveggie.dto.CartItemDTO;
import com.freshveggie.vo.CartItemVO;

import java.util.List;

public interface CartItemService {
    boolean addCartItem(CartItemDTO cartItemDTO);

    /**
     * 查询当前用户购物车。
     *
     * <p>返回 {@link CartItemVO} 而非 {@code CartItem}：购物车表瘦身后
     * 不再存商品快照（名称/图片/规格/单价/小计），这些字段由本方法
     * 回填 —— 单价取 {@code product} 实时价，小计实时计算。
     */
    List<CartItemVO> listCartItem();

    boolean clearCartItem();

    boolean subCartItem(CartItemDTO cartItemDTO);
}
