package com.freshveggie.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.freshveggie.context.BaseContext;
import com.freshveggie.entity.DeliveryAddress;
import com.freshveggie.entity.Orders;
import com.freshveggie.exception.BusinessException;
import com.freshveggie.mapper.DeliveryAddressMapper;
import com.freshveggie.mapper.OrderMapper;
import com.freshveggie.service.DeliveryAddressService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 收货地址服务。
 *
 * <p>2026-10-01（P2-3）：删除地址时同步把历史订单的
 * {@code orders.delivery_address_id} 置空。该列注释写着
 * 「收货地址ID(删址后置空,保留快照)」，但此前没有任何代码实现这个置空
 * —— 历史订单长期持有悬空地址 id（因为已删掉 {@code fk_orders_address}
 * 外键，不会报错，只会静默不一致）。
 *
 * <p>订单的收货信息本身有 consignee / phone / address 三个快照列，
 * 置空这个 id 不会影响展示，只是让数据不再指向已删除的行。
 */
@Service
@RequiredArgsConstructor
public class DeliveryAddressServiceImpl implements DeliveryAddressService {

    private final DeliveryAddressMapper addressMapper;
    private final OrderMapper orderMapper;

    @Override
    public boolean saveAddress(DeliveryAddress address) {
        address.setUserId(BaseContext.getCurrentId());
        return addressMapper.insert(address) > 0;
    }

    @Override
    public List<DeliveryAddress> getAllAddress() {
        return addressMapper.selectList(Wrappers.<DeliveryAddress>lambdaQuery()
                .eq(DeliveryAddress::getUserId, BaseContext.getCurrentId()));
    }

    @Override
    public DeliveryAddress getDefaultAddress() {
        return addressMapper.selectOne(Wrappers.<DeliveryAddress>lambdaQuery()
                .eq(DeliveryAddress::getUserId, BaseContext.getCurrentId())
                .eq(DeliveryAddress::getIsDefault, 1));
    }

    @Override
    public DeliveryAddress getAddressById(Long id) {
        return addressMapper.selectOne(Wrappers.<DeliveryAddress>lambdaQuery()
                .eq(DeliveryAddress::getId, id)
                .eq(DeliveryAddress::getUserId, BaseContext.getCurrentId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteAddressById(Long id) {
        Long userId = BaseContext.getCurrentId();
        // 先确认这条地址确实属于当前用户，避免越权删除
        DeliveryAddress exist = addressMapper.selectOne(Wrappers.<DeliveryAddress>lambdaQuery()
                .eq(DeliveryAddress::getId, id)
                .eq(DeliveryAddress::getUserId, userId));
        if (exist == null) {
            throw new BusinessException("地址不存在");
        }
        // 历史订单保留 consignee/phone/address 快照，但把指向本行的 id 置空，
        // 与 orders.delivery_address_id 的列注释语义一致（P2-3）。
        orderMapper.update(null, Wrappers.<Orders>lambdaUpdate()
                .eq(Orders::getDeliveryAddressId, id)
                .set(Orders::getDeliveryAddressId, null));
        return addressMapper.deleteById(id) > 0;
    }

    @Override
    public boolean updateAddress(DeliveryAddress address) {
        DeliveryAddress exist = getAddressById(address.getId());
        if (exist == null) {
            throw new BusinessException("地址不存在");
        }
        address.setUserId(BaseContext.getCurrentId());
        return addressMapper.updateById(address) > 0;
    }

    @Override
    public boolean setDefaultAddress(Long id) {
        DeliveryAddress target = getAddressById(id);
        if (target == null) {
            throw new BusinessException("地址不存在");
        }
        // 清除当前默认
        addressMapper.update(null, Wrappers.<DeliveryAddress>lambdaUpdate()
                .eq(DeliveryAddress::getUserId, BaseContext.getCurrentId())
                .set(DeliveryAddress::getIsDefault, 0));
        // 设置新默认
        target.setIsDefault(1);
        return addressMapper.updateById(target) > 0;
    }
}
