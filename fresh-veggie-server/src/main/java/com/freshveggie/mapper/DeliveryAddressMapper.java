package com.freshveggie.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.freshveggie.entity.DeliveryAddress;
import org.apache.ibatis.annotations.Mapper;

/**
 * 收货地址 Mapper
 */
@Mapper
public interface DeliveryAddressMapper extends BaseMapper<DeliveryAddress> {
}
