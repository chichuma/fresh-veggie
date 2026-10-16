package com.freshveggie.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 门店（配送起点）配置
 *
 * <p>2026-10-01（P2-16）：删掉两个误引入的无用 import
 * （{@code org.aspectj.weaver.Position} 与
 * {@code org.springframework.boot.test.context.SpringBootTest}）——
 * 后者是测试范围的类，被生产代码 import 属于依赖倒挂。
 */
@Component
@ConfigurationProperties(prefix = "veggie.shop")
@Data
public class ShopProperties {

    /** 配送范围阈值，单位公里。与直线距离比较 */
    private double limitDistance;

    /**
     * 地图接口不可用时是否降级放行（跳过配送范围校验）。
     *
     * <p>默认 {@code true}，保持原有生产语义：「校验能力不可用」≠「确认超范围」，
     * 不能因为百度挂了就堵死全部下单。
     *
     * <p>压测时建议置为 {@code false}（配置项 {@code veggie.shop.degrade-on-map-failure}）：
     * 降级放行会让「外部依赖失败」伪装成「业务成功」，
     * 于是压测能跑出漂亮的 QPS，而配送范围校验其实是空转的。
     * 关掉之后，地图失败会直接以业务异常暴露出来，
     * 让这一部分样本从成功数里剔除，QPS 数字才是可信的。
     */
    private boolean degradeOnMapFailure = true;

    /**
     * 最多可提前几天预约配送（含今天）。
     *
     * <p>用途：{@code DeliverySlotServiceImpl#deleteSlot} 判断「该时段是否被订单引用」时，
     * 用它把扫描范围收敛到「可能存在的配送日期」上，从而能走上
     * {@code idx_orders_date_slot(delivery_date, start_time)} 的前导列。
     *
     * <p>下单侧的 {@code checkDeliveryTimeliness} 只拦「过去的日期」，
     * 没有限制未来上限；若将来加了上限，请与这里保持一致，
     * 否则删时段的判据可能漏掉超出该窗口的订单。
     */
    private int maxDeliveryLeadDays = 30;

    /** 门店经纬度，配送起点 */
    private Location location = new Location();

    @Data
    public static class Location {
        private double lng;
        private double lat;
    }
}
