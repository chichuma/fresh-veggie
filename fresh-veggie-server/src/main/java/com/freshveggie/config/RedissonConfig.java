package com.freshveggie.config;

import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 配置：连接项目 Redis 实例。
 *
 * yml 中可配：
 *   spring.data.redis.host / port / database / password
 *   密码从 ${REDIS_PASSWORD:} 读取，本地默认空。
 */
@Configuration
@Slf4j
public class RedissonConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String host;

    @Value("${spring.data.redis.port:6379}")
    private int port;

    @Value("${spring.data.redis.database:0}")
    private int database;

    @Value("${spring.data.redis.password:}")
    private String password;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        String address = "redis://" + host + ":" + port;
        config.useSingleServer()
                .setAddress(address)
                .setDatabase(database)
                .setPassword(password.isEmpty() ? null : password)
                .setConnectionMinimumIdleSize(5)
                .setConnectionPoolSize(20);

        log.info("[Redisson] init redisson client, {}/{}", address, database);
        try {
            // 正常建连。连得上就正常返回；连不上会在这里抛出，便于拿到精准的失败原因。
            RedissonClient client = Redisson.create(config);
            log.info("[Redisson] Redis 连接成功: {}/{}", address, database);
            return client;
        } catch (RuntimeException e) {
            printStartupError(address, e);

            Config lazyConfig = new Config();
            lazyConfig.useSingleServer()
                    .setAddress(address)
                    .setDatabase(database)
                    .setPassword(password.isEmpty() ? null : password)
                    .setConnectionMinimumIdleSize(5)
                    .setConnectionPoolSize(20);
            lazyConfig.setLazyInitialization(true);
            log.warn("[Redisson] 已启用懒初始化兜底：应用可启动，但 Redis 相关功能不可用");
            return Redisson.create(lazyConfig);
        }
    }

    /**
     * 用 stderr 直接打印横幅：保证在 IDEA 控制台里一眼可见，
     */
    private void printStartupError(String address, RuntimeException e) {
        String cause = rootCauseMessage(e);
        String line = "=".repeat(78);
        System.err.println();
        System.err.println(line);
        System.err.println("  [ERROR] Redis 未连接，应用已降级启动");
        System.err.println("  地址   : " + address + " (db=" + database + ")");
        System.err.println("  原因   : " + cause);
        System.err.println("  影响   : 依赖分布式锁/缓存的功能不可用（下单、商品缓存、幂等校验）");
        System.err.println("  处理   : 启动 Redis 后重启应用。Windows 可执行 Start-Service Redis");
        System.err.println("  注意   : 接口文档不受影响，可正常访问 /swagger-ui/index.html");
        System.err.println(line);
        System.err.println();
        log.error("Redis 未连接，已降级启动: {} ({})", address, cause);
    }

    /** 取异常链最底层的原因，避免把 ExecutionException 这种包装信息报给用户 */
    private String rootCauseMessage(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String msg = root.getMessage();
        return root.getClass().getSimpleName() + (msg == null ? "" : ": " + msg);
    }
}