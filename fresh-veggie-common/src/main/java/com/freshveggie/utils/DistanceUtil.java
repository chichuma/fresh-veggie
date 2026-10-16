package com.freshveggie.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freshveggie.exception.MapApiException;
import com.freshveggie.properties.BaiduProperties;
import com.freshveggie.properties.ShopProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 配送距离工具：地址 → 经纬度 → 直线距离（公里）。
 *
 * <h3>百度接口的坑</h3>
 * 业务状态码在**顶层** {@code status}（0 成功），且失败时 {@code result} 是空数组
 */
@Slf4j
@Component
public class DistanceUtil {

    // 百度地图成功状态码
    private static final int STATUS_SUCCESS = 0;
    // 地球平均半径，Haversine 用
    private static final double EARTH_RADIUS_METERS = 6_371_008.8d;

    @Getter
    private final ShopProperties shopProperties;
    private final ObjectMapper objectMapper;
    private final BaiduProperties baiduProperties;
    private final Coordinate shopCoordinate;

    public DistanceUtil(ObjectMapper objectMapper, BaiduProperties baiduProperties, ShopProperties shopProperties) {
        this.objectMapper = objectMapper;
        this.baiduProperties = baiduProperties;
        this.shopProperties = shopProperties;

        ShopProperties.Location location = shopProperties.getLocation();
        if (location == null || location.getLat() == 0 || location.getLng() == 0) {
            throw new MapApiException("门店坐标未配置: 请检查 veggie.shop.location.lat / lng");
        }
        this.shopCoordinate = new Coordinate(location.getLat(), location.getLng());
    }

    private final Cache<String, Coordinate> geocodeCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofDays(1))
            .build();

    /**
     * 门店坐标（来自 {@code veggie.shop.location}）。
     *
     * <p>供调用方在【已有地址坐标】时直接做本地 Haversine 计算，绕开地理编码。
     * 2026-09-28 加入：坐标落库后，submit 热路径不再需要调百度。
     */
    public Coordinate shopCoordinate() {
        return shopCoordinate;
    }

    // 缓存地址
    public Coordinate geocode(String address) {
        return geocodeCache.get(address, this::doGeocode);
    }

    /**
     * 门店到目标地址的直线距离 km
     *
     * @param destination 目标地址文本，交给百度地理编码解析
     * @return 直线距离，单位公里
     * @throws MapApiException 地理编码不可用——调用方应降级放行，不能因此阻断下单
     */
    public double getDistance(String destination) {
        Coordinate to = geocode(destination);
        double km = distanceKm(shopCoordinate, to);
        log.info("直线距离: {} 公里 ({} -> {}), destination={}",
                String.format("%.3f", km), shopCoordinate, to, destination);
        return km;
    }

    /**
     * 地址 → 经纬度
     *
     * @throws MapApiException 地理编码不可用或未解析出坐标
     */
    public Coordinate doGeocode(String address) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("ak", baiduProperties.getAk());
        params.put("address", address);
        params.put("output", "json");

        JsonNode root = getJson(baiduProperties.getLocationUrl(), params, "地理编码");
        requireSuccess(root, "地理编码");

        // 成功时 result 是对象；失败时百度返回空数组，这里再兜一层
        JsonNode location = root.path("result").path("location");
        double lat = location.path("lat").asDouble(Double.NaN);
        double lng = location.path("lng").asDouble(Double.NaN);

        if (Double.isNaN(lat) || Double.isNaN(lng)) {
            throw new MapApiException("百度地理编码未返回有效坐标, address=" + address
                    + ", result=" + root.path("result"));
        }
        if ( lat < -90 || lat > 90 || lng < -180 || lng > 180 ) {
            throw new MapApiException("百度地理编码返回越界坐标, lat=" + lat + ", lng=" + lng);
        }

        return new Coordinate(lat, lng);
    }

    /**
     * 两点间球面大圆距离（Haversine），纯函数、无副作用、可单测
     *
     * @return 距离，单位公里
     */
    public static double distanceKm(Coordinate from, Coordinate to) {
        double lat1 = Math.toRadians(from.lat());
        double lat2 = Math.toRadians(to.lat());
        double deltaLat = lat2 - lat1;
        double deltaLng = Math.toRadians(to.lng() - from.lng());

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(deltaLng / 2) * Math.sin(deltaLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c / 1000d;
    }


    // ==================== 私有辅助 ====================

    // 发请求 + 解析 JSON，把网络类异常统一收敛成 MapApiException
    // 不 private 可测试
     JsonNode getJson(String url, Map<String, String> params, String apiName) {
        String body;
        try {
            body = HttpClientUtil.doGet(url, params);
        } catch (Exception e) {
            throw new MapApiException(apiName + "接口调用失败: " + e, e);
        }
        if (body == null || body.isBlank()) {
            throw new MapApiException(apiName + "接口无响应(可能是网络不可达或 AK 被拒绝)");
        }
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new MapApiException(apiName + "接口响应不是合法 JSON: " + body, e);
        }
    }

    // 校验百度顶层 status，非 0 一律视为接口不可用
    private void requireSuccess(JsonNode root, String apiName) {
        int status = root.path("status").asInt();
        if (status != STATUS_SUCCESS) {
            throw new MapApiException(apiName + "返回失败状态: status=" + status
                    + ", message=" + root.path("message").asText());
        }
    }


    // 经纬度坐标（百度坐标系 bd09ll）。lat=纬度, lng=经度
    public record Coordinate(double lat, double lng) {
        @Override
        @NonNull
        // 百度接口的坐标顺序是 lng,lat
        public String toString() {
            return lng + "," + lat;
        }
    }
}
