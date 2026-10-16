package com.freshveggie.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.freshveggie.config.CacheConfig;
import com.freshveggie.dto.DeliverySlotDTO;
import com.freshveggie.entity.DeliverySlot;
import com.freshveggie.entity.Orders;
import com.freshveggie.exception.BusinessException;
import com.freshveggie.mapper.DeliverySlotMapper;
import com.freshveggie.mapper.OrderMapper;
import com.freshveggie.properties.ShopProperties;
import com.freshveggie.service.DeliverySlotService;
import com.freshveggie.vo.DeliverySlotVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DeliverySlotServiceImpl implements DeliverySlotService {

    private final DeliverySlotMapper deliverySlotMapper;
    private final OrderMapper orderMapper;
    private final ShopProperties shopProperties;

    /**
     * 按状态查找时段
     * <p>
     * 按启用，停用，全部查询时段状态，升序输出
     *
     * @param status 时段状态
     * @return 查询表
     */
    @Override
    public List<DeliverySlot> listSlots(Integer status) {
        return deliverySlotMapper.selectList(Wrappers.<DeliverySlot>lambdaQuery()
                .eq(status != null, DeliverySlot::getStatus, status)
                .orderByAsc(DeliverySlot::getSort));
    }

    /**
     * 保存时段
     * <p>
     * 使用 dto 里面的 name 字段，查询具体行来查重
     *
     * @param dto 里面可用字段为 name
     * @return 保存结果
     */
    @Override
    @CachePut(
            cacheNames = CacheConfig.CACHE_SLOT,
            key = "#result",
            unless = "#result == null || #result == 0L"
    )
    public Long saveSlot(DeliverySlotDTO dto) {
        checkTimeRange(dto);
        Long count = deliverySlotMapper.selectCount(Wrappers.<DeliverySlot>lambdaQuery()
                .eq(DeliverySlot::getSlotName, dto.getSlotName()));
        if (count != null && count > 0) {
            throw new BusinessException("时段名称已存在");
        }
        DeliverySlot slot = new DeliverySlot();
        BeanUtils.copyProperties(dto, slot);
        if (deliverySlotMapper.insert(slot) > 0) {
            return slot.getId();
        } else {
            return 0L;
        }
    }

    /**
     * 修改时段。
     *
     * <h3>2026-10-01 重写：原来是「先删后插」，会丢数据</h3>
     * 旧实现是 {@code BeanUtils.copyProperties(dto, slot); deleteSlot(id); saveSlot(dto);}，
     * 三个独立问题：
     * <ol>
     *   <li><b>丢数据</b>：两次操作各自自动提交、无事务。若把时段改成已存在的名称，
     *       {@code saveSlot} 会抛「时段名称已存在」，而这时旧行【已经被物理删掉了】——
     *       唯一键只能拦住插入，救不回已删的行。</li>
     *   <li><b>主键漂移</b>：更新后是全新 insert，id 变了，前端持有的旧 id
     *       与所有按 id 的缓存键全部失效。</li>
     *   <li><b>死赋值</b>：{@code copyProperties} 拷进 {@code slot} 的结果从未被使用。</li>
     * </ol>
     *
     * <p>现在直接用 {@code updateById}：一次原子写，id 不变，改名冲突在写之前就报错。
     * 配合 {@code @CachePut} 刷新该 id 的缓存 —— 旧实现内部 this 调用
     * {@code deleteSlot/saveSlot} 会绕过 Spring 代理，那两个方法上的
     * {@code @CacheEvict/@CachePut} 根本不生效，导致
     * {@code checkDeliverySlot}（{@code @Cacheable(key="#id")}）读到已删除/已停用的旧时段。
     */
    /**
     * 时段起止时间校验（P2-5）。
     *
     * <p>数据库有 {@code chk_slot_time (end_time > start_time)}，但此前 DTO 与
     * Service 都不校验，管理端把结束时间填得比开始时间早，会直接撞 CHECK 约束，
     * 抛出一个数据库异常 —— 用户看到的是裸 500。现在在写入前就以可读提示拦下。
     *
     * <p>放在 Service 而不是 DTO 注解里：新增与修改两条路径共用，
     * 且 {@code @MultiFieldAssociationCheck} 位于 common 模块，
     * pojo 在依赖链上更低、引不到。
     */
    private void checkTimeRange(DeliverySlotDTO dto) {
        if (dto.getStartTime() == null || dto.getEndTime() == null) {
            throw new BusinessException("开始时间与结束时间不能为空");
        }
        if (!dto.getEndTime().isAfter(dto.getStartTime())) {
            throw new BusinessException("结束时间必须晚于开始时间");
        }
    }

    @Override
    @CachePut(cacheNames = CacheConfig.CACHE_SLOT, key = "#dto.id", unless = "#result == null")
    public DeliverySlot updateSlot(DeliverySlotDTO dto) {
        checkTimeRange(dto);
        DeliverySlot slot = deliverySlotMapper.selectById(dto.getId());
        if (slot == null) {
            throw new BusinessException("时段不存在");
        }
        // 改名时先查重（排除自己），避免撞 uk_delivery_slot_name 抛裸 500
        if (dto.getSlotName() != null && !Objects.equals(dto.getSlotName(), slot.getSlotName())) {
            Long count = deliverySlotMapper.selectCount(Wrappers.<DeliverySlot>lambdaQuery()
                    .eq(DeliverySlot::getSlotName, dto.getSlotName())
                    .ne(DeliverySlot::getId, dto.getId()));
            if (count != null && count > 0) {
                throw new BusinessException("时段名称已存在");
            }
        }
        BeanUtils.copyProperties(dto, slot);
        if (deliverySlotMapper.updateById(slot) <= 0) {
            throw new BusinessException("修改失败");
        }
        return slot;
    }

    /**
     * 删除时段
     * <p>
     * 使用 id 查询是否有该时段
     * 再查有没有被订单引用
     *
     * @param id 字段ID
     * @return 保存结果
     */
    @Override
    @CacheEvict(cacheNames = CacheConfig.CACHE_SLOT, key = "#id")
    public boolean deleteSlot(Long id) {
        DeliverySlot slot = deliverySlotMapper.selectById(id);
        if (slot == null) {
            throw new BusinessException("时段不存在");
        }
        // 订单现在存的是【起止时间快照】而不是 slot.id（2026-09-28 改动），
        // 所以「是否被引用」要按时间窗口匹配，而不是按 id。
        //
        // 2026-10-01（P2-9）：原本只按 start_time + end_time 精确匹配，
        // 而现有索引是 idx_orders_date_slot(delivery_date, start_time) ——
        // 条件里没有前导列 delivery_date，用不上该索引，orders 上到几十万行
        // 就是一次全表扫描。
        //
        // 现在补上 delivery_date 的范围条件，让索引的前导列可用：
        // 配送日期必然落在 [今天, 今天+提前期] 内 ——
        // 早于今天的配送日期会被下单时的时效校验拦掉（见 OrderServiceImpl
        // #checkDeliveryTimeliness），晚于提前期的属于异常数据。
        //
        // 注意：这**没有放宽**判据语义 —— 它只是把「不可能存在订单」的日期排除掉，
        // 真正决定「是否被引用」的仍是 start_time + end_time 的精确匹配。
        LocalDate today = LocalDate.now();
        LocalDate horizon = today.plusDays(shopProperties.getMaxDeliveryLeadDays());
        Long orderCount = orderMapper.selectCount(Wrappers.<Orders>lambdaQuery()
                .eq(Orders::getStartTime, slot.getStartTime())
                .eq(Orders::getEndTime, slot.getEndTime())
                .ge(Orders::getDeliveryDate, today)
                .le(Orders::getDeliveryDate, horizon));
        if (orderCount != null && orderCount > 0) {
            throw new BusinessException("该时段已被订单引用，无法删除");
        }
        return deliverySlotMapper.deleteById(id) > 0;
    }

    /**
     * 时段启停用。
     *
     * <h3>2026-10-01 修正（P2-11）</h3>
     * 本方法原来返回 {@code boolean}，但上面挂了
     * {@code @CachePut(cacheNames = CACHE_SLOT, key = "#id")} ——
     * {@code @CachePut} 缓存的是**方法返回值**，于是会把 {@code true/false}
     * 写进 {@code deliverySlotCache} 的该 id 下。
     * 而 {@link #checkDeliverySlot(Long)} 上的 {@code @Cacheable} 期望拿到
     * {@code DeliverySlot}（同一个缓存名、同一个键），命中后强转必然抛
     * {@code ClassCastException}。
     *
     * <p>现在返回更新后的实体，与 {@link #updateSlot} 同一模式：
     * 缓存里存的始终是 {@code DeliverySlot}。返回 {@code null} 表示更新失败。
     */
    @Caching(
            // status == 0 时：删除缓存（停用的时段不该留在缓存里）
            evict = @CacheEvict(
                    cacheNames = CacheConfig.CACHE_SLOT,
                    key = "#id",
                    condition = "#status == 0"
            ),
            // status == 1 时：写入缓存（存的是下面返回的实体，不是 boolean）
            put = @CachePut(
                    cacheNames = CacheConfig.CACHE_SLOT,
                    key = "#id",
                    condition = "#status == 1",
                    unless = "#result == null"
            )
    )
    @Override
    public DeliverySlot changeSlotStatus(Long id, Integer status) {
        DeliverySlot slot = deliverySlotMapper.selectById(id);
        if (slot == null) {
            throw new BusinessException("时段不存在");
        }
        slot.setStatus(status);
        return deliverySlotMapper.updateById(slot) > 0 ? slot : null;
    }

    @Override
    public List<DeliverySlotVO> listEnabledSlots() {
        return deliverySlotMapper.selectList(Wrappers.<DeliverySlot>lambdaQuery()
                        .eq(DeliverySlot::getStatus, 1)
                        .orderByAsc(DeliverySlot::getSort))
                .stream()
                .map(slot -> DeliverySlotVO.builder()
                        .id(slot.getId())
                        .slotName(slot.getSlotName())
                        .startTime(slot.getStartTime())
                        .endTime(slot.getEndTime())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Cacheable(cacheNames = CacheConfig.CACHE_SLOT, key = "#id")
    public DeliverySlot checkDeliverySlot(Long id) {
        DeliverySlot slot = deliverySlotMapper.selectById(id);
        if (slot == null || !Objects.equals(slot.getStatus(), 1)) {
            throw new BusinessException("配送时段不存在或已停用");
        }
        return slot;
    }
}
