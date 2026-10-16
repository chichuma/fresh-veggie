package com.freshveggie.service;

import com.freshveggie.dto.DeliverySlotDTO;
import com.freshveggie.entity.DeliverySlot;
import com.freshveggie.vo.DeliverySlotVO;

import java.util.List;

/**
 * 配送时段 Service。"次日达 + 时段配送"核心模块。
 */
public interface DeliverySlotService {


    // 管理端:查询时段列表(可按状态筛选,status 为 null 查全部,按 sort 排序)
    List<DeliverySlot> listSlots(Integer status);

    // 管理端:新增时段
    Long saveSlot(DeliverySlotDTO deliverySlotDTO);

    // 管理端:修改时段
    // 返回更新后的实体（而非 boolean）：便于用 @CachePut 按 id 刷新时段缓存，
    // 也便于调用方拿到最新值。2026-10-01 由 boolean 改为实体。
    DeliverySlot updateSlot(DeliverySlotDTO deliverySlotDTO);

    // 管理端:删除时段(已被订单引用时禁止删除)
    boolean deleteSlot(Long id);

    // 管理端:启停用(0 停用 1 启用)
    // 返回更新后的实体：@CachePut 缓存的是返回值，必须是 DeliverySlot
    // 才能被 checkDeliverySlot 的 @Cacheable 正确读取（2026-10-01 由 boolean 改为实体）。
    // 返回 null 表示更新失败。
    DeliverySlot changeSlotStatus(Long id, Integer status);

    // C端:查询可预约的启用时段
    List<DeliverySlotVO> listEnabledSlots();

    // C端:通过ID获取当前时段
    DeliverySlot checkDeliverySlot (Long id) ;
}
