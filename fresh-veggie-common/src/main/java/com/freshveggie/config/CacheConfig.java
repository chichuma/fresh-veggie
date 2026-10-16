package com.freshveggie.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine 本地缓存配置。
 *
 * <h3>本类是 {@code @Cacheable} 的唯一后端</h3>
 * 2026-09-28 之前，本类的 {@code caffeineCacheManager} 与
 * {@code AliOssConfig.redisCacheManager} 同时向容器注册了 {@code CacheManager}
 * 类型的 bean，缓存切面注入了 Redis 那个（只认识显式创建过的缓存），
 * 导致 {@code @Cacheable(cacheNames="deliverySlotCache")} 每次都抛
 * 「Cannot find cache named 'deliverySlotCache'」—— 整个下单链路不可用。
 * 现已删除那个 Redis bean，本类成为唯一后端。**不要再往容器里加第二个
 * {@code CacheManager} 类型的 bean。**
 *
 * <h3>⚠️ 三个缓存名的实际使用情况（2026-10-01 核实）</h3>
 * <table border="1">
 *   <tr><th>缓存名</th><th>状态</th></tr>
 *   <tr><td>{@link #CACHE_SLOT} "deliverySlotCache"</td>
 *       <td><b>正在使用</b> —— {@code DeliverySlotServiceImpl} 的
 *           {@code @Cacheable} / {@code @CachePut} / {@code @CacheEvict} 共 7 处引用</td></tr>
 *   <tr><td>{@link #CACHE_CATEGORY} "categoryCache"</td>
 *       <td><b>预留，当前未使用</b> —— 全项目没有任何 {@code @Cacheable} 指向它</td></tr>
 *   <tr><td>{@link #CACHE_HOT_PRODUCT} "hotProductCache"</td>
 *       <td><b>预留，当前未使用</b> —— 同上</td></tr>
 * </table>
 *
 * <p><b>注意别被名字误导：商品/分类的数据当前并不走这两个 Caffeine 缓存。</b>
 * C 端「按分类查商品」用的是
 * {@code ProductServiceImpl#getProductVoListByCategoryId} 里<b>手写的 Redis Hash</b>
 * （key = {@code veg:shop:category:products}，字段 = categoryId）。
 * 也就是说商品缓存是**分布式**的（多实例共享），而这里预置的两个是**进程内**缓存。
 * 将来若要把商品缓存迁到 Caffeine，需要先确认能否接受"多实例各存一份"的语义变化。
 *
 * <p>这两个名字保留而不删，是为了避免将来加缓存时又要改一遍配置；
 * 它们当前不占用内存（Caffeine 的 cache 是懒创建的，没有 {@code @Cacheable}
 * 访问就不会实例化）。
 */
@Configuration
public class CacheConfig {

    /** 商品分类缓存（预留，当前未使用 —— 见类注释） */
    public static final String CACHE_CATEGORY = "categoryCache";

    /** 热门商品列表缓存（预留，当前未使用 —— 见类注释） */
    public static final String CACHE_HOT_PRODUCT = "hotProductCache";

    /** 配送时段缓存（**正在使用**，被 DeliverySlotServiceImpl 的缓存注解引用） */
    public static final String CACHE_SLOT = "deliverySlotCache";

    /**
     * 全项目唯一的 {@link CacheManager}。
     *
     * <p>{@code @Primary} 只是保险 —— 当前容器里只有这一个，
     * 但万一将来有人加了第二个，有它至少能让注入不至于随机。
     * 真正可靠的做法仍是"容器里只保留一个"，因为
     * {@code CacheAspectSupport} 按类型解析时的选择行为依赖解析顺序。
     */
    @Bean
    @Primary
    public CacheManager caffeineCacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder()
                // 容量上限（防止内存溢出）
                .maximumSize(10_000)
                // 写入后过期时间
                .expireAfterWrite(30, TimeUnit.MINUTES)
                // 统计命中率
                .recordStats());
        // 预注册缓存名。注意 setCacheNames 会关闭「动态创建」——
        // 未在此登记的名字，@Cacheable 引用时会抛「Cannot find cache named」。
        // 新增缓存时记得同时加到这里。
        manager.setCacheNames(java.util.Arrays.asList(CACHE_CATEGORY, CACHE_HOT_PRODUCT, CACHE_SLOT));
        return manager;
    }
}
