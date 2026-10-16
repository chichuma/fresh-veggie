package com.freshveggie.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.freshveggie.entity.Staff;
import org.apache.ibatis.annotations.Mapper;

/**
 * 后台员工 Mapper
 */
@Mapper
public interface StaffMapper extends BaseMapper<Staff> {
}
