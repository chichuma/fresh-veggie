package com.freshveggie.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.freshveggie.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * C 端用户 Mapper：只有 BaseMapper 提供的通用 CRUD，全部由 Wrapper 表达
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
