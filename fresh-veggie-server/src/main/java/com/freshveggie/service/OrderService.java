package com.freshveggie.service;

import com.freshveggie.dto.OrdersCancelDTO;
import com.freshveggie.dto.OrdersPaymentDTO;
import com.freshveggie.dto.OrdersSubmitDTO;
import com.freshveggie.dto.pageQuery.OrdersPageQueryDTO;
import com.freshveggie.result.PageResult;
import com.freshveggie.vo.OrderStatisticsVO;
import com.freshveggie.vo.OrderSubmitVO;
import com.freshveggie.vo.OrderVO;

public interface OrderService {

    OrderSubmitVO submit(OrdersSubmitDTO ordersSubmitDTO);

    // 用户端:支付(微信,支付成功后 待支付 -> 备货中)
    void payment(OrdersPaymentDTO ordersPaymentDTO);

    // 用户端:历史订单分页
    PageResult<OrderVO> getHistoryOrders(OrdersPageQueryDTO ordersPageQueryDTO);

    // 用户端:订单详情
    OrderVO getUserOrderItem(Long id);

    // 用户端:再次下单(校验商品仍在售,回填购物车)
    void repeatOrder(Long id);

    // 用户端:取消订单(待支付/备货中可取消,回补库存)
    void userCancelOrder(Long id);

    // 管理端:条件分页查询订单(订单号/手机号/状态/时间区间)
    PageResult<OrderVO> getOrderListByCondition(OrdersPageQueryDTO ordersPageQueryDTO);

    // 管理端:订单详情
    OrderVO getOrderItem(Long id);

    // 管理端:开始配送(备货中 -> 配送中)
    void startDelivery(Long id);

    // 管理端:确认送达(配送中 -> 已完成)
    void completeOrder(Long id);

    // 管理端:取消订单/拒单(统一写 cancel_reason)
    void adminCancelOrder(OrdersCancelDTO ordersCancelDTO);

    // 管理端:各状态订单数量统计
    OrderStatisticsVO statistics();
}
