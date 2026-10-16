package com.freshveggie.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.freshveggie.context.BaseContext;
import com.freshveggie.dto.OrdersCancelDTO;
import com.freshveggie.dto.OrdersPaymentDTO;
import com.freshveggie.dto.OrdersSubmitDTO;
import com.freshveggie.dto.pageQuery.OrdersPageQueryDTO;
import com.freshveggie.entity.*;
import com.freshveggie.enums.OrderEvent;
import com.freshveggie.enums.OrderStatus;
import com.freshveggie.exception.BusinessException;
import com.freshveggie.exception.MapApiException;
import com.freshveggie.exception.OrderBusinessException;
import com.freshveggie.mapper.*;
import com.freshveggie.properties.ShopProperties;
import com.freshveggie.result.PageResult;
import com.freshveggie.service.DeliverySlotService;
import com.freshveggie.service.OrderService;
import com.freshveggie.service.OrderSubmitService;
import com.freshveggie.service.ShopService;
import com.freshveggie.service.state.OrderStateContext;
import com.freshveggie.utils.DistanceUtil;
import com.freshveggie.utils.RedissonLockUtil;
import com.freshveggie.vo.OrderStatisticsVO;
import com.freshveggie.vo.OrderSubmitVO;
import com.freshveggie.vo.OrderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.support.DefaultStateMachineContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderSubmitService orderSubmitService;
    private final DistanceUtil distanceUtil;
    private final RedissonLockUtil redissonLockUtil;
    private final OrderMapper orderMapper;
    private final ProductMapper productMapper;
    private final CartItemMapper cartItemMapper;
    private final OrderItemMapper orderItemMapper;
    private final DeliveryAddressMapper addressMapper;
    private final OrderStateContext orderStateContext;
    private final DeliverySlotService deliverySlotService;
    private final StateMachineFactory<OrderStatus, OrderEvent> stateMachineFactory;
    private final ShopService shopService;
    private final ShopProperties shopProperties;


    // ==================== 提交/支付方法 ====================

    /**
     * 尝试提交订单
     * <p>
     *     先验证订单符合条件，再抢分布式锁
     *
     * @return 提交视图
     */
    @Override
    public OrderSubmitVO submit(OrdersSubmitDTO ordersSubmitDTO) {
        Long userId = BaseContext.getCurrentId();
        checkShopStatus();
        DeliverySlot slot = deliverySlotService.checkDeliverySlot(ordersSubmitDTO.getDeliverySlotId());
        List<CartItem> cartItemList = checkCartItems(userId);
        DeliveryAddress address = checkAddress(userId, ordersSubmitDTO);
        // 时效校验（P2-4）放在最后一项校验的位置。
        //
        // 为什么不放最前面：最初放在 checkCartItems 之前，结果压测时
        // 2000 个请求全部在这一步被拒，购物车、地址、坐标回填等路径
        // 【一次都没被执行到】—— 校验顺序把后面的代码"遮住"了，
        // 让人误判后面的链路也有问题。放在所有校验之后，
        // 前面的校验该失败还是会失败，不会被这一步掩盖。
        checkDeliveryTimeliness(ordersSubmitDTO.getDeliveryDate(), slot);

        // 商品信息一次性加载，并在【分布式锁之外】完成。
        //
        // 为什么必须在锁外：购物车瘦身后（2026-09-28）cart_item 不再存商品快照，
        // 订单明细的名称/价格/规格只能来自 product 表 —— 若放在 doSubmit 事务内查，
        // 会拉长事务、从而拉长 product 热行 X 锁的持有窗口（JFR 实测那是当前
        // 94% 以上 MySQL 阻塞的来源）。放在锁外则为纯读，不影响临界区。
        Map<Long, Product> productMap = loadProductMap(cartItemList);

        // 金额与明细都在锁外算好（纯内存 + 实时价），事务内只做写入
        Orders orders = loadOrders(userId, address, ordersSubmitDTO, slot, cartItemList, productMap);
        List<OrderItem> orderItemList = loadOrderItemList(cartItemList, productMap);

        // 分布式锁串行化同一用户下单
        OrderSubmitVO submitVO = redissonLockUtil.tryLock("order:submit:" + userId, 3, 10,
                () -> orderSubmitService.doSubmit(cartItemList, orders, orderItemList, userId));

        if (submitVO == null) {
            throw new OrderBusinessException("下单请求处理中，请勿重复提交");
        }

        return submitVO;
    }


    /**
     * 支付方法
     * <p>
     * 没有接入微信支付接口，直接返回成功
     */
    @Override
    public void payment(OrdersPaymentDTO ordersPaymentDTO) {
        Long userId = BaseContext.getCurrentId();
        Orders order = orderMapper.selectOne(Wrappers.<Orders>lambdaQuery()
                .eq(Orders::getUserId, userId)
                .eq(Orders::getOrderNo, ordersPaymentDTO.getOrderNumber()));
        if (order == null) {
            throw new OrderBusinessException("订单不存在");
        }
        StateMachine<OrderStatus, OrderEvent> stateMachine = buildOrderStateMachine(order);
        orderStateContext.init(order, stateMachine);
        orderStateContext.pay();
    }

    // ==================== 订单查询方法 ====================

    @Override
    public PageResult<OrderVO> getHistoryOrders(OrdersPageQueryDTO dto) {
        return queryOrderPage(dto);
    }

    @Override
    public OrderVO getUserOrderItem(Long id) {
        Long userId = BaseContext.getCurrentId();
        Orders order = orderMapper.selectOne(Wrappers.<Orders>lambdaQuery()
                .eq(Orders::getId, id)
                .eq(Orders::getUserId, userId));
        if (order == null) {
            throw new OrderBusinessException("订单不存在");
        }
        return buildOrderVO(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void repeatOrder(Long id) {
        List<OrderItem> orderItems = getOrderItems(id);
        if (CollectionUtils.isEmpty(orderItems)) {
            throw new OrderBusinessException("订单不存在");
        }
        List<Long> productIds = orderItems.stream().map(OrderItem::getProductId).toList();
        List<Long> sellingIds = productMapper.getSellingProductListByIds(productIds);
        if (sellingIds.size() != productIds.size()) {
            throw new OrderBusinessException("存在商品已下架，无法重新加入购物车");
        }

        Long userId = BaseContext.getCurrentId();
        // 命中唯一键前先清掉同名商品旧行,避免 insert 冲突
        cartItemMapper.delete(Wrappers.<CartItem>lambdaQuery()
                .eq(CartItem::getUserId, userId)
                .in(CartItem::getProductId, productIds));

        List<CartItem> cartItems = orderItems.stream()
                .map(item -> CartItem.builder()
                        .userId(userId)
                        .productId(item.getProductId())
                        // 购物车瘦身后（2026-09-28）不再存商品快照：
                        // 名称/图片/规格/单价等一律实时读 product 表。
                        // 「再来一单」只需把商品与数量放回购物车。
                        .number(item.getNumber())
                        .createTime(LocalDateTime.now())
                        .updateTime(LocalDateTime.now())
                        .build())
                .toList();
        cartItemMapper.saveItemBatch(cartItems);
    }

    @Override
    public PageResult<OrderVO> getOrderListByCondition(OrdersPageQueryDTO dto) {
        return queryOrderPage(dto);
    }

    /**
     * 订单分页查询（C 端历史订单与后台条件列表共用）。
     *
     * <p>分页必须落在「订单」维度。早期实现用 orders join order_item 再分页，
     * LIMIT 作用在联表行上：一单 N 个明细就占 N 行，于是 pageSize=10 实际只回 3~5 单，
     * 相邻页还会因为明细行被切开而出现重复订单；count 口径又是订单数，二者对不上。
     *
     * <p>所以这里改成两段：先用 Wrapper 分页查订单（行数 == 订单数），
     * 再按本页 orderIds 批量补明细，在内存里归组。
     * 条件与 {@link OrdersPageQueryDTO} 的字段一一对应，用布尔开关重载拼装，
     * 为 null 的条件整段不会进 SQL。
     */
    private PageResult<OrderVO> queryOrderPage(OrdersPageQueryDTO dto) {
        Page<Orders> orderPage = orderMapper.selectPage(
                new Page<>(dto.getPage(), dto.getPageSize()),
                Wrappers.<Orders>lambdaQuery()
                        .eq(dto.getUserId() != null, Orders::getUserId, dto.getUserId())
                        .eq(dto.getStatus() != null, Orders::getStatus, dto.getStatus())
                        .eq(dto.getNumber() != null && !dto.getNumber().isEmpty(),
                                Orders::getOrderNo, dto.getNumber())
                        .eq(dto.getPhone() != null && !dto.getPhone().isEmpty(),
                                Orders::getPhone, dto.getPhone())
                        .ge(dto.getBeginTime() != null, Orders::getCreateTime, dto.getBeginTime())
                        .le(dto.getEndTime() != null, Orders::getCreateTime, dto.getEndTime())
                        .orderByDesc(Orders::getCreateTime)
                        .orderByDesc(Orders::getId));

        // total 由分页插件按同一套条件自动 COUNT，与 records 天然同口径
        return new PageResult<>(orderPage.getTotal(), assembleOrderVOs(orderPage.getRecords()),
                orderPage.getSize(), orderPage.getCurrent());
    }

    /** 只为本页订单批量取明细再在内存归组，避免联表行参与分页 */
    private List<OrderVO> assembleOrderVOs(List<Orders> orders) {
        if (CollectionUtils.isEmpty(orders)) {
            return List.of();
        }
        List<Long> orderIds = orders.stream().map(Orders::getId).toList();
        Map<Long, List<OrderItem>> itemMap = orderItemMapper.selectList(Wrappers.<OrderItem>lambdaQuery()
                        .in(OrderItem::getOrderId, orderIds)
                        .orderByAsc(OrderItem::getOrderId)
                        .orderByAsc(OrderItem::getId))
                .stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId));

        return orders.stream().map(order -> {
            OrderVO vo = new OrderVO();
            BeanUtils.copyProperties(order, vo);
            List<OrderItem> items = itemMap.getOrDefault(order.getId(), List.of());
            vo.setOrderItems(items);
            // 列表页展示用的商品摘要，格式沿用原实现的 "名称*数量;"（末尾保留分号，避免下游解析口径变化）
            StringBuilder orderProducts = new StringBuilder();
            for (OrderItem item : items) {
                orderProducts.append(item.getName()).append("*").append(item.getNumber()).append(";");
            }
            vo.setOrderProducts(orderProducts.toString());
            return vo;
        }).toList();
    }

    @Override
    public OrderVO getOrderItem(Long id) {
        Orders order = orderMapper.selectById(id);
        if (order == null) {
            throw new OrderBusinessException("订单不存在");
        }
        return buildOrderVO(order);
    }


    // ==================== 订单流转方法 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void userCancelOrder(Long id) {
        Long userId = BaseContext.getCurrentId();
        Orders order = orderMapper.selectOne(Wrappers.<Orders>lambdaQuery()
                .eq(Orders::getId, id)
                .eq(Orders::getUserId, userId));
        if (order == null) {
            throw new OrderBusinessException("订单不存在");
        }
        StateMachine<OrderStatus, OrderEvent> stateMachine = buildOrderStateMachine(order);
        orderStateContext.init(order, stateMachine);
        // 流转失败会抛异常(见 IOrderState#fire),因此到这一行时订单一定已置为 CANCELLED
        //
        // 注意：这里【不再】调用 restoreStock —— 库存回补与销量回退已收敛到
        // 状态类内部（各 State 的 cancelOrder）。若在此处再补一次，
        // 会造成【双倍回补库存】（2026-10-01 修正）。
        orderStateContext.userCancel();
    }

    @Override
    public void startDelivery(Long id) {
        Orders order = orderMapper.selectById(id);
        if (order == null) {
            throw new OrderBusinessException("订单不存在");
        }
        StateMachine<OrderStatus, OrderEvent> stateMachine = buildOrderStateMachine(order);
        orderStateContext.init(order, stateMachine);
        orderStateContext.startDelivery();
    }

    @Override
    public void completeOrder(Long id) {
        Orders order = orderMapper.selectById(id);
        if (order == null) {
            throw new OrderBusinessException("订单不存在");
        }
        StateMachine<OrderStatus, OrderEvent> stateMachine = buildOrderStateMachine(order);
        orderStateContext.init(order, stateMachine);
        orderStateContext.complete();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adminCancelOrder(OrdersCancelDTO ordersCancelDTO) {
        Orders order = orderMapper.selectById(ordersCancelDTO.getId());
        if (order == null) {
            throw new OrderBusinessException("订单不存在");
        }
        StateMachine<OrderStatus, OrderEvent> stateMachine = buildOrderStateMachine(order);
        orderStateContext.init(order, stateMachine);
        // 同样不再在此处 restoreStock：状态类内部已按「是否已支付」分别处理
        // 库存回补与销量回退，这里重复调用会双倍回补（2026-10-01 修正）。
        orderStateContext.adminCancel(ordersCancelDTO.getCancelReason());
    }



    // ==================== 统计方法 ====================

    @Override
    public OrderStatisticsVO statistics() {
        OrderStatisticsVO vo = new OrderStatisticsVO();
        vo.setPendingPayment(countByStatus(Orders.PENDING_PAYMENT));
        vo.setPreparing(countByStatus(Orders.PREPARING));
        vo.setDelivering(countByStatus(Orders.DELIVERING));
        return vo;
    }

    // ==================== 私有方法 ====================

    private OrderVO buildOrderVO(Orders order) {
        List<OrderItem> orderItems = getOrderItems(order.getId());
        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(order, orderVO);
        orderVO.setOrderItems(orderItems);
        return orderVO;
    }

    private List<OrderItem> getOrderItems(Long orderId) {
        return orderItemMapper.selectList(Wrappers.<OrderItem>lambdaQuery()
                .eq(OrderItem::getOrderId, orderId));
    }

    private Integer countByStatus(Integer status) {
        Long count = orderMapper.selectCount(Wrappers.<Orders>lambdaQuery().eq(Orders::getStatus, status));
        return count == null ? 0 : count.intValue();
    }


    private StateMachine<OrderStatus, OrderEvent> buildOrderStateMachine(Orders order) {
        StateMachine<OrderStatus, OrderEvent> stateMachine = stateMachineFactory.getStateMachine(order.getOrderNo());
        stateMachine.stopReactively().block();
        stateMachine.getStateMachineAccessor()
                .doWithAllRegions(access -> access.resetStateMachineReactively(
                        new DefaultStateMachineContext<>(OrderStatus.fromState(order.getStatus()), null, null, null)
                ).block());
        stateMachine.startReactively().block();
        return stateMachine;
    }

    /**
     * 配送范围校验：门店 → 收货地址的直线距离必须在上限内。
     *
     * <h3>坐标优先走库，缺失才调百度（lazy backfill）</h3>
     * 原本每次下单都要调一次百度地理编码。JFR 实测（2026-09-28）它平均阻塞
     * 42.45ms，冷缓存首次解析可达 688ms，是 submit 链路里除 MySQL 外唯一
     * 超过 10ms 的环节，还带来两个不受本机控制的尾巴：AK 只有 3 QPS 配额
     * （超了返回 status=401）、以及网络抖动。
     *
     * <p>地址是不变的，所以解析一次就写回 {@code delivery_address.longitude/latitude}，
     * 之后永远走本地 Haversine（微秒级）。存量 5000+ 行没有坐标，
     * 由本方法按访问流量自然摊平回填 —— 不起一次性批量任务，
     * 否则会瞬时时打爆百度配额。
     *
     * @param address 收货地址（含可能已落库的坐标）
     */
    private void verifiedDistance(DeliveryAddress address) {
        String detailedAddress = address.detailedAddress();
        try {
            double distance;
            if (address.latLngCached()) {
                // 命中：纯本地计算，不碰网络
                distance = distanceUtil.distanceKm(
                        distanceUtil.shopCoordinate(),
                        new DistanceUtil.Coordinate(
                                address.getLatitude().doubleValue(),
                                address.getLongitude().doubleValue()));
            } else {
                // 未命中：调百度解析，成功后回写该行，下次即命中
                distance = distanceUtil.getDistance(detailedAddress);
                backfillCoordinates(address, detailedAddress);
            }

            if (distance > shopProperties.getLimitDistance()) {
                throw new OrderBusinessException("下单失败，超出配送范围");
            }
        } catch (MapApiException exception) {
            if (!shopProperties.isDegradeOnMapFailure()) {
                // 严格模式（压测用）：地图不可用直接失败，不静默跳过校验。
                // 否则「外部依赖失败」会被算进成功样本，抬高 QPS 数字的可信度上限。
                log.error("配送范围校验失败（严格模式，不降级）: address={}, reason={}",
                        detailedAddress, exception.getMessage(), exception);
                throw new OrderBusinessException("下单失败，配送范围校验服务不可用");
            }
            // 「校验能力不可用」≠「确认超范围」：百度挂掉/AK 被拒时不能堵死全部下单，
            // 降级放行并打 ERROR 告警，由人工或后续重试补校验。
            log.error("配送范围校验降级放行（地图接口不可用）: address={}, reason={}",
                    detailedAddress, exception.getMessage(), exception);
        }
    }

    /**
     * 把刚解析出的坐标写回地址行（lazy backfill）。
     *
     * <p>刻意用 {@code UPDATE ... WHERE id=? AND longitude IS NULL} 而不是
     * {@code updateById}：并发下同一地址可能有多个请求同时未命中并各自调百度，
     * 加 {@code longitude IS NULL} 条件让「第一个写成功的人赢」，后续写入自然
     * 变成 0 行受影响 —— 避免把后来的值覆盖上去（虽然同一地址结果相同，
     * 但显式表达"只填空、不覆盖"更安全）。
     *
     * <p>回填失败不影响下单：只记 WARN，下次请求会再试。
     */
    private void backfillCoordinates(DeliveryAddress address, String detailedAddress) {
        try {
            DistanceUtil.Coordinate c = distanceUtil.geocode(detailedAddress);
            int rows = addressMapper.update(null, Wrappers.<DeliveryAddress>lambdaUpdate()
                    .eq(DeliveryAddress::getId, address.getId())
                    .isNull(DeliveryAddress::getLongitude)
                    .set(DeliveryAddress::getLongitude, java.math.BigDecimal.valueOf(c.lng()))
                    .set(DeliveryAddress::getLatitude, java.math.BigDecimal.valueOf(c.lat())));
            if (rows > 0) {
                log.info("地址坐标已回填: addressId={}, lat={}, lng={}", address.getId(), c.lat(), c.lng());
            }
        } catch (Exception e) {
            log.warn("地址坐标回填失败（不影响下单，下次请求会重试）: addressId={}, reason={}",
                    address.getId(), e.getMessage());
        }
    }

    private void checkShopStatus() {
        Integer shopStatus = shopService.getShopStatus();
        if (shopStatus != null && shopStatus == 0) {
            throw new OrderBusinessException("下单失败，店铺不在营业中");
        }
    }

    private DeliveryAddress checkAddress(Long userId , OrdersSubmitDTO ordersSubmitDTO) {
        DeliveryAddress address = addressMapper.selectOne(Wrappers.<DeliveryAddress>lambdaQuery()
                .eq(DeliveryAddress::getId, ordersSubmitDTO.getDeliveryAddressId())
                .eq(DeliveryAddress::getUserId, userId));
        if (address == null) {
            throw new BusinessException("地址不存在");
        }
        verifiedDistance(address);
        return address;
    }

    /**
     * 配送时效校验（P2-4）。
     *
     * <p>此前 {@code checkDeliverySlot} 只校验时段「非空 + 启用」，
     * {@code deliveryDate} 只有 {@code @NotNull}，于是可以选一个**过去的日期**
     * 或**今天已经结束的时段**下单，"次日达"在代码上毫无约束（P2-4）。
     *
     * <p>规则：
     * <ol>
     *   <li>配送日期不得早于今天 —— 选过去的日期一定送达不了；</li>
     *   <li>若配送日期就是今天，则时段结束时间必须还没过 ——
     *       否则等于承诺一个已经流逝的配送窗口。</li>
     * </ol>
     *
     * <p>为什么不放在 DTO 的注解里：这是**跨字段**约束（日期 + 时段起止），
     * 单字段注解表达不了；用类级自定义校验器又会把"今天"这个时间点固化在
     * 校验时刻，不如直接在业务层判。
     *
     * @param deliveryDate 期望配送日期
     * @param slot         已校验存在且启用的时段
     */
    private void checkDeliveryTimeliness(LocalDate deliveryDate, DeliverySlot slot) {
        if (deliveryDate == null) {
            throw new BusinessException("请选择配送日期");
        }
        LocalDate today = LocalDate.now();
        if (deliveryDate.isBefore(today)) {
            throw new BusinessException("配送日期不能早于今天");
        }
        if (deliveryDate.isEqual(today)
                && slot.getEndTime() != null
                && !LocalTime.now().isBefore(slot.getEndTime())) {
            throw new BusinessException("该配送时段今日已结束，请选择之后的时段");
        }
    }
    private List<CartItem> checkCartItems(Long userId) {        List<CartItem> cartItemList = cartItemMapper.selectList(Wrappers.<CartItem>lambdaQuery()
                .eq(CartItem::getUserId, userId));
        if (CollectionUtils.isEmpty(cartItemList)) {
            throw new BusinessException("购物车为空，无法下单");
        }
        return cartItemList;
    }

    /**
     * 批量加载购物车涉及的商品，并校验商品都还在。
     *
     * <p>购物车瘦身后（2026-09-28）{@code cart_item} 只有 product_id，
     * 订单明细所需的名称/图片/规格/单位/价格全部来自这里。
     *
     * <p>放在锁外执行：这是纯读，不参与 product 热行的 X 锁争用；
     * 若挪进事务会把临界区拉长，直接压低吞吐（JFR 已量化）。
     */
    private Map<Long, Product> loadProductMap(List<CartItem> cartItemList) {
        Set<Long> productIds = cartItemList.stream()
                .map(CartItem::getProductId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, Product> productMap = productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        // 商品被删除时必须在【下单前】失败，而不是等 deductStock 扣不到行才报库存不足
        // —— 后者给出的错误信息会误导排查方向。
        for (Long pid : productIds) {
            if (!productMap.containsKey(pid)) {
                throw new BusinessException("下单失败，商品已下架或不存在：" + pid);
            }
        }
        return productMap;
    }

    /**
     * 商品金额合计。
     *
     * <p>价格取 {@code product.price} 的【实时值】，不再用 cart_item 里的快照价。
     * 这修掉了「商品改价后订单金额按过期价计算」的 bug —— 见
     * bench/sql/refactor/2026-09-28-04-cart-slim-and-slot-snapshot.sql。
     */
    private BigDecimal countAmount(List<CartItem> cartItemList, Map<Long, Product> productMap) {
        BigDecimal total = BigDecimal.ZERO;
        for (CartItem cart : cartItemList) {
            Product p = productMap.get(cart.getProductId());
            total = total.add(p.getPrice().multiply(BigDecimal.valueOf(cart.getNumber())));
        }
        return total;
    }

    private String createOrderNumber(Long userId) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmssSSS");
        String timestamp = sdf.format(new Date());
        int randomNum = new Random().nextInt(900) + 100;
        return timestamp + userId + randomNum;
    }

    private Orders loadOrders(
            Long userId,
            DeliveryAddress address,
            OrdersSubmitDTO dto,
            DeliverySlot slot,
            List<CartItem> cartItemList,
            Map<Long, Product> productMap
    ) {
        BigDecimal goodsAmount = countAmount(cartItemList, productMap);
        BigDecimal deliveryFee = BigDecimal.valueOf(6);
        BigDecimal totalAmount = goodsAmount.add(deliveryFee);

        Orders orders = new Orders();
        orders.setUserId(userId);
        orders.setOrderNo(createOrderNumber(userId));
        orders.setStatus(Orders.PENDING_PAYMENT);
        orders.setPayStatus(Orders.UN_PAID);
        orders.setPayMethod(dto.getPayMethod());
        orders.setDeliveryDate(dto.getDeliveryDate());
        orders.setDeliveryAddressId(address.getId());
        orders.setConsignee(address.getConsignee());
        orders.setPhone(address.getPhone());
        orders.setAddress(address.detailedAddress());
        // 配送时段：存【起止时间快照】而不是 slot.id / slot.name。
        // 订单是快照 —— 时段行被改名或删除后，历史订单仍能正确展示当时的配送窗口。
        orders.setStartTime(slot.getStartTime());
        orders.setEndTime(slot.getEndTime());
        orders.setGoodsAmount(goodsAmount);
        orders.setDeliveryFee(deliveryFee);
        orders.setTotalAmount(totalAmount);
        orders.setIsPresale(0);
        orders.setRemark(dto.getRemark());
        return orders;
    }

    /**
     * 构造订单明细（商品快照在这里定格）。
     *
     * <p>购物车不再存商品快照，所以这些字段全部取自 {@code productMap}
     * 的实时值 —— 而「下单这一刻」正是快照应当定格的时刻。
     * 之后商品改价不会影响已生成的订单。
     */
    private List<OrderItem> loadOrderItemList(List<CartItem> cartItemList, Map<Long, Product> productMap) {
        List<OrderItem> orderItemList = new ArrayList<>(cartItemList.size());
        for (CartItem cartItem : cartItemList) {
            Product p = productMap.get(cartItem.getProductId());
            int number = cartItem.getNumber();
            orderItemList.add(OrderItem.builder()
                    .productId(cartItem.getProductId())
                    .name(p.getName())
                    .image(p.getImage())
                    .spec(p.getSpec())
                    .unit(p.getUnit())
                    .origin(p.getOrigin())
                    .unitPrice(p.getPrice())
                    .number(number)
                    .amount(p.getPrice().multiply(BigDecimal.valueOf(number)))
                    .build());
        }
        return orderItemList;
    }

}
