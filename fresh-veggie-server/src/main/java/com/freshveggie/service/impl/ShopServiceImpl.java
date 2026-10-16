package com.freshveggie.service.impl;

import com.freshveggie.constant.RedisConstant;
import com.freshveggie.service.ShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ShopServiceImpl implements ShopService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public Integer getShopStatus() {
        Integer status = (Integer) redisTemplate.opsForValue().get(RedisConstant.SHOP_STATUS_KEY);
        return status == null ? 1 : status;
    }

    @Override
    public void setShopStatus(Integer status) {
        redisTemplate.opsForValue().set(RedisConstant.SHOP_STATUS_KEY, status);
    }
}
