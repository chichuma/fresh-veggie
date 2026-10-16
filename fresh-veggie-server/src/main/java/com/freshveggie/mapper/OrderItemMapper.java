package com.freshveggie.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.freshveggie.entity.OrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 订单明细 Mapper, 批量插入保留在 OrderItemMapper.xml
 */
@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItem> {

    // 批量新增订单明细
    void insertBatch(@Param("orderItems") List<OrderItem> orderItems);
}
