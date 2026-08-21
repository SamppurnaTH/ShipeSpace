package com.shipspace.config;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis infrastructure configuration.
 * Cache manager TTLs are configured in CacheConfig.
 * This class provides the RedisTemplate bean used by RateLimitingService.
 */
@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${spring.data.redis.url:${REDIS_URL:}}")
    private String redisUrl;

    /**
     * Explicit LettuceConnectionFactory so we can disable eager connection
     * validation — prevents startup hang when Redis is slow to accept connections.
     */
    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config = redisUrl != null && !redisUrl.isBlank()
                ? redisConfigFromUrl(redisUrl)
                : new RedisStandaloneConfiguration(redisHost, redisPort);
        if ((redisUrl == null || redisUrl.isBlank()) && redisPassword != null && !redisPassword.isBlank()) {
            config.setPassword(redisPassword);
        }
        LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
        factory.setValidateConnection(false); // don't block startup on Redis availability
        return factory;
    }

    private RedisStandaloneConfiguration redisConfigFromUrl(String value) {
        URI uri = URI.create(value);
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(
                uri.getHost(),
                uri.getPort() > 0 ? uri.getPort() : 6379);
        String userInfo = uri.getUserInfo();
        if (userInfo != null && !userInfo.isBlank()) {
            String[] parts = userInfo.split(":", 2);
            if (parts.length == 2) {
                config.setUsername(parts[0]);
                config.setPassword(parts[1]);
            } else {
                config.setPassword(parts[0]);
            }
        }
        return config;
    }

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
