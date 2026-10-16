package com.freshveggie.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 百度地图配置
 */
@Component
@ConfigurationProperties(prefix = "veggie.baidu")
@Data
public class BaiduProperties {
    private String ak;
    private String locationUrl;
}
