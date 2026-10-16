package com.freshveggie.service;

import com.freshveggie.dto.OrdersSubmitDTO;
import com.freshveggie.entity.*;
import com.freshveggie.vo.OrderSubmitVO;

import java.util.List;

public interface OrderSubmitService {

    OrderSubmitVO doSubmit(List<CartItem> cartItemList, Orders orders, List<OrderItem> orderItemList, Long userId);
}
