package com.freshveggie.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.freshveggie.entity.DeliverySlot;
import org.apache.ibatis.annotations.Mapper;

/**
 * 配送时段 Mapper
 */
@Mapper
public interface DeliverySlotMapper extends BaseMapper<DeliverySlot> {
}
