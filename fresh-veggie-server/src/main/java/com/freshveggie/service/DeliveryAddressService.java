package com.freshveggie.service;

import com.freshveggie.entity.DeliveryAddress;

import java.util.List;

public interface DeliveryAddressService {
    boolean saveAddress(DeliveryAddress address);

    List<DeliveryAddress> getAllAddress();

    DeliveryAddress getDefaultAddress();

    boolean setDefaultAddress(Long id);

    DeliveryAddress getAddressById(Long id);

    boolean deleteAddressById(Long id);

    boolean updateAddress(DeliveryAddress address);
}
