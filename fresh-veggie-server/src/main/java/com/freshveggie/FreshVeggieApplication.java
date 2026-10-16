package com.freshveggie;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 启动类。
 * <p>几个 @EnableXxx 注解缺一不可,缺了不会报错、功能直接静默失效:
 * <ul>
 *   <li>{@code @EnableTransactionManagement} —— @Transactional 生效</li>
 *   <li>{@code @EnableScheduling} —— ShopTask 里的 @Scheduled 定时任务生效</li>
 *   <li>{@code @EnableCaching} —— CacheConfig 的 CaffeineCacheManager + @Cacheable 生效</li>
 *   <li>{@code @EnableAsync} —— AliOssUtil 上的 @Async 异步上传生效</li>
 * </ul>
 */
@SpringBootApplication
@EnableTransactionManagement // 开启注解方式的事务管理
@EnableScheduling            // 开启定时任务(@Scheduled)
@EnableCaching               // 开启缓存注解(@Cacheable / @CacheEvict)
@EnableAsync                 // 开启异步方法(@Async)
public class FreshVeggieApplication {
    public static void main(String[] args) {
        SpringApplication.run(FreshVeggieApplication.class, args);
    }
}
