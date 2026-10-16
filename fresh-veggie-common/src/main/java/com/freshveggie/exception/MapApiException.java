package com.freshveggie.exception;

/**
 * 地图（百度）接口不可用：网络失败、DNS 解析失败，或返回非 0 状态码（如 210 APP IP 校验失败）。
 *
 * <p>与「距离确实超出配送范围」严格区分：
 * <ul>
 *   <li>本异常 = 校验能力不可用 → 调用方应降级放行并告警，不能因为第三方挂了就堵死下单；</li>
 *   <li>{@link OrderBusinessException}("超出配送范围") = 校验成功且确认超范围 → 必须拦截。</li>
 * </ul>
 */
public class MapApiException extends BaseException {

    public MapApiException(String message) {
        super(message);
    }

    public MapApiException(String message, Throwable cause) {
        super(message);
        initCause(cause);
    }
}
