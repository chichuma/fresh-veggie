package com.freshveggie.service.impl;

import com.freshveggie.mapper.OrderMapper;
import com.freshveggie.mapper.ProductMapper;
import com.freshveggie.mapper.ReportMapper;
import com.freshveggie.service.WorkSpaceService;
import com.freshveggie.vo.BusinessDataVO;
import com.freshveggie.vo.OrderOverViewVO;
import com.freshveggie.vo.ProductOverViewVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WorkSpaceServiceImpl implements WorkSpaceService {

    private final OrderMapper orderMapper;
    private final ReportMapper reportMapper;
    private final ProductMapper productMapper;

    @Override
    public OrderOverViewVO getOverviewOrders() {
        return orderMapper.getAllStatusOrderCount();
    }

    @Override
    public ProductOverViewVO getOverviewProductes() {
        return productMapper.getAllStatusProductesCount();
    }

    @Override
    public BusinessDataVO getBusinessData() {
        return reportMapper.getBusinessData(null, null);
    }
}
