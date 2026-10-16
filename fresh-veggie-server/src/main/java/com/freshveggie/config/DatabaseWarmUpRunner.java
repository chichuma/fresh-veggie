package com.freshveggie.config;

import com.freshveggie.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动后主动预热数据库连接池。
 *
 * <h3>为什么需要它</h3>
 * 本项目实测发现：应用报告"启动完成"时，数据库连接池<strong>还没建立</strong>。
 * 真实日志（2026-09-24）：
 *
 * <pre>
 * 14:50:33.098  Started FreshVeggieApplication in 5.53 seconds   ← 应用认为启动完了
 * 14:51:00.013  HikariCP - Starting...                          ← 27 秒后才建池
 * 14:51:00.200  HikariCP - Start completed
 * </pre>
 *
 * 注意 14:51:00 那几行的线程名是 {@code [MessageBroker-1]} ——
 * 是<strong>定时任务</strong>触发了第一次数据库访问，才顺带把连接池建起来。
 * 也就是说，在"启动完成"到"第一次真实数据库访问"之间，连接池是空的。
 *
 * <h3>这会带来什么问题</h3>
 * <ul>
 *   <li><b>冷启动后的第一批请求</b>要现场建连接池 + 建物理连接，
 *       这批请求的延迟会明显偏高，甚至撞上 {@code connection-timeout} 而失败。</li>
 *   <li><b>生产环境滚动发布</b>时，新实例刚"启动完成"就接入流量，
 *       头几个请求变慢，可能被健康检查判为不健康而反复重启。</li>
 *   <li><b>压测</b>时如果紧跟启动开跑，前几秒的数据会失真。</li>
 * </ul>
 *
 * <h3>做法</h3>
 * 用 {@link ApplicationRunner} 在容器就绪后立刻执行一次轻量查询。
 * 顺带也验证了数据库连通性 —— 起不来会打 ERROR，比"第一个用户请求失败"更早暴露问题。
 *
 * <p>查询用 {@code selectCount} 而不是 {@code SELECT 1}：复用项目已有的 Mapper，
 * 不额外引入 JdbcTemplate 依赖；代价只是一次 count，对空表几乎无开销。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseWarmUpRunner implements ApplicationRunner {

    private final UserMapper userMapper;

    @Override
    public void run(ApplicationArguments args) {
        long start = System.currentTimeMillis();
        try {
            Long count = userMapper.selectCount(null);
            long cost = System.currentTimeMillis() - start;
            log.info("[预热] 数据库连接池已建立，用户表 {} 行，耗时 {} ms", count, cost);
        } catch (Exception e) {
            // 不抛异常：数据库不可用时应用仍应启动（与 Redis 的降级策略保持一致），
            // 但必须打 ERROR 让问题尽早可见
            log.error("[预热] 数据库不可用，连接池未能建立。后续涉及数据库的请求会失败。", e);
        }
    }
}
