package com.shipspace.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis infrastructure configuration.
 * Cache manager TTLs are configured in CacheConfig.
 * This class provides the RedisTemplate bean used by RateLimitingService.
 */
@Configuration
public class RedisConfig {

    /**
     * RedisTemplate for string key/value operations (rate limiting counters).
     * Uses StringRedisSerializer for both keys and values so counters are
     * human-readable in Redis CLI.
     */
    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }
}
