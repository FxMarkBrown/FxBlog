package top.fxmarkbrown.blog.config;

import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import top.fxmarkbrown.blog.common.CacheNames;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Spring Cache 配置
 */
@Configuration
public class CacheConfig {

    @Bean
    public CacheManager cacheManager(RedisTemplate<String, Object> redisTemplate, RedisSerializer<Object> redisValueSerializer) {
        if (redisTemplate.getConnectionFactory() == null) {
            throw new IllegalStateException("RedisConnectionFactory 未配置");
        }
        RedisCacheConfiguration defaultConfig = createConfiguration(Duration.ofMinutes(30), redisValueSerializer);

        Map<String, RedisCacheConfiguration> cacheConfigurations = new LinkedHashMap<>();
        cacheConfigurations.put(CacheNames.SYS_CONFIG, createConfiguration(Duration.ofHours(6), redisValueSerializer));
        cacheConfigurations.put(CacheNames.SYS_DICT, createConfiguration(Duration.ofHours(6), redisValueSerializer));
        cacheConfigurations.put(CacheNames.WEB_CONFIG, createConfiguration(Duration.ofMinutes(30), redisValueSerializer));
        cacheConfigurations.put(CacheNames.WEATHER_EFFECT, createConfiguration(Duration.ofMinutes(10), redisValueSerializer));
        cacheConfigurations.put(CacheNames.PUBLIC_NOTICE, createConfiguration(Duration.ofMinutes(30), redisValueSerializer));
        cacheConfigurations.put(CacheNames.PUBLIC_TAG, createConfiguration(Duration.ofHours(1), redisValueSerializer));
        cacheConfigurations.put(CacheNames.PUBLIC_FRIEND, createConfiguration(Duration.ofHours(1), redisValueSerializer));
        cacheConfigurations.put(CacheNames.PUBLIC_MESSAGE, createConfiguration(Duration.ofMinutes(30), redisValueSerializer));
        cacheConfigurations.put(CacheNames.PUBLIC_ARTICLE_ARCHIVE, createConfiguration(Duration.ofMinutes(30), redisValueSerializer));
        cacheConfigurations.put(CacheNames.PUBLIC_ARTICLE_CATEGORIES, createConfiguration(Duration.ofMinutes(30), redisValueSerializer));
        cacheConfigurations.put(CacheNames.PUBLIC_ARTICLE_CAROUSEL, createConfiguration(Duration.ofMinutes(30), redisValueSerializer));
        cacheConfigurations.put(CacheNames.PUBLIC_ARTICLE_RECOMMEND, createConfiguration(Duration.ofMinutes(30), redisValueSerializer));
        cacheConfigurations.put(CacheNames.PUBLIC_HOME_CONFIG, createConfiguration(Duration.ofMinutes(5), redisValueSerializer));
        cacheConfigurations.put(CacheNames.PUBLIC_ARTICLE_LIST, createConfiguration(Duration.ofSeconds(30), redisValueSerializer));
        cacheConfigurations.put(CacheNames.PUBLIC_CATEGORY_ALL, createConfiguration(Duration.ofMinutes(30), redisValueSerializer));

        return RedisCacheManager.builder(redisTemplate.getConnectionFactory())
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware()
                .build();
    }

    private RedisCacheConfiguration createConfiguration(Duration ttl, RedisSerializer<Object> redisValueSerializer) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .computePrefixWith(CacheNames::redisPrefix)
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(redisValueSerializer))
                .entryTtl(ttl)
                .disableCachingNullValues();
    }
}
