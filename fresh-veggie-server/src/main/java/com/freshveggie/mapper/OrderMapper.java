package com.freshveggie.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.freshveggie.entity.Orders;
import com.freshveggie.vo.OrderOverViewVO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单 Mapper。
 */
@Mapper
public interface OrderMapper extends BaseMapper<Orders> {

    // 订单总览各状态数量(工作台)：5 个相关子查询，Wrapper 表达不了
    OrderOverViewVO getAllStatusOrderCount();
}
