package com.freshveggie.aspect;

import com.freshveggie.annotation.Idempotent;
import com.freshveggie.context.BaseContext;
import com.freshveggie.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * 幂等切面：基于 Redis SETNX 实现。
 *
 * <p>第一次进入时 SETNX 成功，执行业务；后续重复进入时 SETNX 失败，抛出异常。
 *
 * <p><b>本次修复的三个缺陷</b>（原实现都会导致幂等实际失效）：
 * <ol>
 *   <li><b>fallback key 不含请求内容</b>：原来是 {@code userId + 类名 + 方法名}，
 *       于是「同一用户 10 秒内下两笔不同商品的订单」会被误判成重复请求而拦掉。
 *       现在把请求参数摘要(sha256)并入 key。</li>
 *   <li><b>任何异常都释放占位</b>：原来 {@code catch (Throwable) { delete(key) }}，
 *       业务已经成功、但响应写出阶段失败时也会删 key，客户端重试就会产生第二笔订单。
 *       现在只对「业务失败」释放，成功路径永不释放。</li>
 *   <li><b>释放时未校验归属</b>：直接 delete 会删掉「别人刚抢到的」占位。
 *       现在用 Lua 脚本「比较 value 相等才删」，保证只释放自己那一份。</li>
 * </ol>
 *
 * <p>另外 header 优先级可配置：{@link Idempotent#trustClientKey()} 默认 false，
 * 因为 {@code X-Idempotent-Key} 是客户端可控值，默认信任等于把幂等开关交给前端。
 */
@Aspect
@Component
@Slf4j
public class IdempotentAspect {

    private static final String HEADER_KEY = "X-Idempotent-Key";
    private static final String REDIS_KEY_PREFIX = "veg:idempotent:";

    /** 比较 value 相等才删除，避免误删他人占位。 */
    private static final DefaultRedisScript<Long> COMPARE_AND_DELETE = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Around("@annotation(com.freshveggie.annotation.Idempotent)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        Idempotent annotation = method.getAnnotation(Idempotent.class);

        String key = buildKey(pjp, method, annotation);
        // value 用「本次请求的唯一标识」，释放时校验归属，避免误删
        String token = key + ":" + System.nanoTime();

        Boolean firstTime = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, token, annotation.expireSeconds(), TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(firstTime)) {
            log.warn("[Idempotent] 重复请求被拦截, key={}", key);
            throw new BusinessException(annotation.message());
        }

        try {
            return pjp.proceed();
        } catch (Throwable t) {
            // 只对「业务失败」释放占位，让调用方可以重试。
            // 成功路径不释放：避免「业务已提交、响应写出失败」造成重复下单。
            if (annotation.releaseOnFailure()) {
                release(key, token);
            }
            throw t;
        }
    }

    private void release(String key, String token) {
        try {
            stringRedisTemplate.execute(COMPARE_AND_DELETE, Collections.singletonList(key), token);
        } catch (Exception e) {
            // 释放失败不影响主流程：占位会随 TTL 自然过期
            log.warn("[Idempotent] 释放占位失败, key={}", key, e);
        }
    }

    private String buildKey(ProceedingJoinPoint pjp, Method method, Idempotent annotation) {
        // 1. header 优先，但默认不信任客户端提供的值（见 trustClientKey 注释）
        if (annotation.trustClientKey()) {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                String headerKey = attrs.getRequest().getHeader(HEADER_KEY);
                if (headerKey != null && !headerKey.isEmpty()) {
                    return REDIS_KEY_PREFIX + annotation.keyPrefix() + ":h:" + headerKey;
                }
            }
        }

        // 2. 登录用户 + 方法签名 + 请求参数摘要
        //    加参数摘要的原因：否则同一用户的第二笔「不同订单」会被误判为重复提交
        Long userId = BaseContext.getCurrentId();
        String methodKey = annotation.keyPrefix()
                + ":" + method.getDeclaringClass().getSimpleName()
                + ":" + method.getName();
        return REDIS_KEY_PREFIX + methodKey + ":" + (userId == null ? "anon" : userId)
                + ":" + sha256(Arrays.toString(pjp.getArgs()));
    }

    private String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 必备算法，正常不会发生
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
