package com.freshveggie.service;

import com.freshveggie.vo.BusinessDataVO;
import com.freshveggie.vo.OrderOverViewVO;
import com.freshveggie.vo.ProductOverViewVO;

public interface WorkSpaceService {
    OrderOverViewVO getOverviewOrders();

    ProductOverViewVO getOverviewProductes();

    BusinessDataVO getBusinessData();
}
