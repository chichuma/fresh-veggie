package com.freshveggie.utils;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 分布式锁工具（基于 Redisson）
 *
 * 用法：
 *   redissonLockUtil.tryLock("order:submit:" + userId, 3, 10, () -> doSubmit());
 *
 * <p>Redis 不可用时（RedissonConfig 启动降级）本类仍能被正常注入并调用，
 * 此时 tryLock 明确失败并返回 null，而不是抛空指针。
 */
@Component
@Slf4j
public class RedissonLockUtil {

    /**
     * 注入阶段不会失败，把可用性判断推迟到真正调用时
     */
    private final ObjectProvider<RedissonClient> redissonClientProvider;

    public RedissonLockUtil(ObjectProvider<RedissonClient> redissonClientProvider) {
        this.redissonClientProvider = redissonClientProvider;
    }

    /**
     * 尝试加锁并执行业务逻辑。
     *
     * @param lockKey   锁的 key（建议加上业务前缀，如 "order:submit:userId"）
     * @param waitTime  等待锁的最大秒数
     * @param leaseTime 锁的租期（自动释放时间，秒），防止死锁
     * @param supplier  业务逻辑
     * @return 业务逻辑返回值；获取锁失败或 Redis 不可用返回 null
     */
    public <T> T tryLock(String lockKey, int waitTime, int leaseTime, Supplier<T> supplier) {
        RedissonClient redissonClient = redissonClientProvider.getIfAvailable();
        // 为 null = 启动时连不上 Redis
        // 这里明确失败而不是抛 NPE：返回 null 由调用方按「锁未获取」处理
        if (redissonClient == null) {
            log.error("[Redisson] Redis 未连接，无法获取分布式锁, key={}。请启动 Redis 后重启应用", lockKey);
            return null;
        }
        RLock lock = redissonClient.getLock(lockKey);
        boolean acquired;
        try {
            acquired = lock.tryLock(waitTime, leaseTime, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[Redisson] 获取锁被中断, key={}", lockKey);
            return null;
        }
        if (!acquired) {
            log.warn("[Redisson] 获取锁失败, key={}", lockKey);
            return null;
        }
        try {
            return supplier.get();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}