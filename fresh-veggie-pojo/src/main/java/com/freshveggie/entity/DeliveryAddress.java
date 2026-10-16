package com.freshveggie.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryAddress implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    //主键
    private Long id;

    //用户id
    private Long userId;

    //收货人
    private String consignee;

    //手机号
    private String phone;

    //省级区划编号
    private String provinceCode;

    //省级名称
    private String provinceName;

    //市级区划编号
    private String cityCode;

    //市级名称
    private String cityName;

    //区级区划编号
    private String districtCode;

    //区级名称
    private String districtName;

    //详细地址
    private String detail;

    //标签
    private String label;

    //默认 0 否 1是
    private Integer isDefault;

    /**
     * 经度（bd09ll，百度地理编码坐标系）。
     *
     * <p>新增于 2026-09-28，用于把「地址 → 坐标」这一步从下单热路径上摘掉。
     * 取值来源与回填策略见 {@link #latLngCached()}。
     */
    private java.math.BigDecimal longitude;

    /**
     * 纬度（bd09ll，百度地理编码坐标系）。
     *
     * @see #longitude
     */
    private java.math.BigDecimal latitude;

    /**
     * 坐标是否已落库（两者都非空才算命中）。
     *
     * <p>为什么要落库：下单时的配送范围校验原本每次都要调百度地理编码
     * （JFR 实测平均 42ms、冷启动可达 688ms，且 AK 只有 3 QPS 配额）。
     * 地址是不变的，所以解析一次就该永久复用 —— 见
     * bench/sql/refactor/2026-09-28-03-address-coords.sql。
     */
    public boolean latLngCached() {
        return longitude != null && latitude != null;
    }

    /**
     * 拼出完整地址，供地理编码与订单地址快照使用。
     *
     * <p>直辖市（北京/上海/天津/重庆）的 provinceName 与 cityName 都是「北京市」，
     * 直接拼接会得到「北京市北京市朝阳区…」，既冗余又会干扰地图地理编码命中率，
     * 因此相邻层级同名时只保留一个。
     */
    public String detailedAddress() {
        StringBuilder sb = new StringBuilder();
        appendIfDistinct(sb, provinceName);
        appendIfDistinct(sb, cityName);
        appendIfDistinct(sb, districtName);
        appendIfDistinct(sb, detail);
        return sb.toString();
    }

    private static void appendIfDistinct(StringBuilder sb, String part) {
        if (part == null || part.isBlank()) {
            return;
        }
        String current = sb.toString();
        if (!current.isEmpty() && current.endsWith(part)) {
            return;
        }
        sb.append(part);
    }
}
