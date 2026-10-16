package com.freshveggie.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口幂等注解。
 *
 * 用法：在 Controller 方法上添加：
 *   @PostMapping("/submit")
 *   @Idempotent(expireSeconds = 10)
 *   public Result<?> submit(...) { ... }
 *
 * 幂等键默认为：用户ID + 类名 + 方法名 + 【请求参数摘要(sha256)】。
 * 参数摘要必须参与,否则同一用户短时间内提交两笔「内容不同」的订单
 * 会被误判为重复请求而拦掉。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Idempotent {

    /** Redis 中幂等键的过期时间（秒），建议略大于业务处理时间 */
    int expireSeconds() default 10;

    /** 幂等键前缀，配合方法签名做默认 key */
    String keyPrefix() default "";

    /** 重复请求时的提示信息 */
    String message() default "请勿重复提交";

    /**
     * 是否信任客户端传来的 {@code X-Idempotent-Key} header。
     *
     * <p>默认 false。该 header 是客户端可控值，信任它等于把幂等的开关交给前端：
     * 前端只要每次换一个新 key，幂等就完全失效。仅在可信客户端（如自家 App）
     * 且需要「跨请求去重」时才开启。
     */
    boolean trustClientKey() default false;

    /**
     * 业务方法抛异常时是否释放幂等占位（允许调用方重试）。
     *
     * <p>默认 true。注意「成功路径永不释放」：若在成功返回后再释放，
     * 那么「业务已提交、但响应写出失败」的场景下客户端重试会产生第二笔订单。
     */
    boolean releaseOnFailure() default true;
}
