package com.freshveggie.config;

import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.auth.CredentialsProviderFactory;
import com.aliyun.oss.common.auth.DefaultCredentialProvider;
import com.aliyun.oss.common.comm.SignVersion;
import com.aliyun.oss.model.CreateBucketRequest;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.freshveggie.json.JacksonObjectMapper;
import com.freshveggie.properties.AliOssProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@EnableConfigurationProperties(AliOssProperties.class)
public class AliOssConfig {

    @Bean
    @ConditionalOnProperty(prefix = "veggie.alioss", name = "access-key-id")
    public OSS createOssClient(AliOssProperties aliOssProperties) {
        ClientBuilderConfiguration clientBuilderConfiguration = new ClientBuilderConfiguration();
        clientBuilderConfiguration.setSignatureVersion(SignVersion.V4);
        DefaultCredentialProvider credentialProvider = CredentialsProviderFactory.newDefaultCredentialProvider(aliOssProperties.getAccessKeyId(), aliOssProperties.getAccessKeySecret());

        OSS ossClient = OSSClientBuilder.create()
                .endpoint(aliOssProperties.getEndpoint())
                .region(aliOssProperties.getRegion())
                .credentialsProvider(credentialProvider)
                .build();

        // 判断存储空间是否存在
        String bucketName = aliOssProperties.getBucketName();
        boolean exist = ossClient.doesBucketExist(bucketName);
        if (!exist) {
            // 如果不存在则创建存储空间
            CreateBucketRequest createBucketRequest = new CreateBucketRequest(bucketName);
            ossClient.createBucket(createBucketRequest);
        }

        return ossClient;
    }

    @Configuration
    public static class RedisConfig {

        @Bean
        public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
            RedisTemplate<String, Object> template = new RedisTemplate<>();
            template.setConnectionFactory(factory);

            StringRedisSerializer stringSerializer = new StringRedisSerializer();

            // key采用String序列化
            template.setKeySerializer(stringSerializer);
            template.setHashKeySerializer(stringSerializer);

            // 为 Redis 单独创建一个新的 ObjectMapper，避免污染 Spring MVC 全局 ObjectMapper
            ObjectMapper om = new JacksonObjectMapper();
            // 注册Java8时间模块
            om.registerModule(new JavaTimeModule());
            om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
            // 启用默认的类型信息序列化，支持多态反序列化
            om.activateDefaultTyping(LaissezFaireSubTypeValidator.instance, ObjectMapper.DefaultTyping.NON_FINAL);
            // 禁用写入日期为timestamps，改为ISO-8601字符串日期格式
            om.disable(SerializationFeature.WRITE_DATE_KEYS_AS_TIMESTAMPS);

            // value采用Jackson序列化（构造器传入 ObjectMapper，替代已废弃的 setObjectMapper）
            Jackson2JsonRedisSerializer<Object> jacksonSerializer = new Jackson2JsonRedisSerializer<>(om, Object.class);

            template.setValueSerializer(jacksonSerializer);
            template.setHashValueSerializer(jacksonSerializer);

            template.afterPropertiesSet();
            return template;
        }

        /*
         * ============================================================
         * 这里原本有一个 `redisCacheManager(RedisConnectionFactory)` bean，
         * 已于 2026-09-28 删除。原因如下（不要凭"顺手补一个"再加回来）：
         *
         * 它与 CacheConfig.caffeineCacheManager() 同时向容器注册
         * CacheManager 类型的 bean。Spring 缓存切面注入了 Redis 这一个，
         * 而它是用 cacheDefaults() 构建的 —— 只认识显式创建过的缓存。
         * 而 @Cacheable 用的名字（如 "deliverySlotCache"）并不在 Redis 那边，
         * 于是【每一次】调用都抛：
         *
         *   java.lang.IllegalArgumentException:
         *     Cannot find cache named 'deliverySlotCache' for Builder[
         *       public com.freshveggie.entity.DeliverySlot
         *       com.freshveggie.service.impl.DeliverySlotServiceImpl.checkDeliverySlot(java.lang.Long)]
         *
         * 后果是 checkDeliverySlot 100% 失败、整个下单链路不可用。
         * 2026-09-28 的压测把它暴露出来（此前没有并发流量，一直没被发现）。
         *
         * 注意：给 Caffeine 那个 bean 加 @Primary 并不能稳定解决 ——
         * CacheAspectSupport 按类型取唯一的 CacheResolver/CacheManager，
         * 两个候选共存时行为依赖解析顺序，不可靠。
         *
         * 【现状】CacheConfig 的 CaffeineCacheManager 是唯一的 CacheManager，
         * 是 @Cacheable 的唯一后端。经全项目检索确认，被删的这个 bean
         * 没有任何引用点（没有 CacheManager 注入、没有 @Qualifier 引用）。
         *
         * 【将来若要 Redis 分布式缓存】不要在这里再加一个 CacheManager
         * 类型的 bean。正确做法是让 CacheConfig 里那个 bean 用
         * @ConditionalOnProperty 做开关，二选一，保证容器里始终只有一个。
         * ============================================================
         */
    }
}
