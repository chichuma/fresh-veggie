package com.freshveggie.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 微信小程序配置。
 *
 * <p>只保留登录换取 openid 所需的两项（{@code /sns/jscode2session}）。
 * 支付相关字段（商户号、证书、回调地址）已随未接线的 WeChatPayUtil 一并移除；
 * 将来接支付时建议基于微信官方 wechatpay-java 重新设计配置结构。
 */
@Component
@ConfigurationProperties(prefix = "veggie.wechat")
@Data
public class WeChatProperties {

    private String appid; //小程序的appid
    private String secret; //小程序的秘钥

}
