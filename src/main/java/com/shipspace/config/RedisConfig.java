package com.shipspace.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis configuration for caching in the ShipSpace API.
 * Configures cache manager and serialization for feed caching.
 * 
 * Implements caching strategy for trending feeds to optimize performance.
 * Requirements: 7.5, 11.1
 */
@Configuration
@EnableCaching
public class RedisConfig {

    @Value("${feed.trending.cache-ttl-minutes:15}")
    private int trendingCacheTtlMinutes;

    @Value("${feed.personalized.cache-ttl-minutes:5}")
    private int personalizedCacheTtlMinutes;

    @Value("${feed.user-interest-profile.cache-ttl-minutes:5}")
    private int userInterestProfileCacheTtlMinutes;

    @Value("${feed.content-quality-score.cache-ttl-minutes:60}")
    private int contentQualityScoreCacheTtlMinutes;

    /**
     * Configure Redis cache manager with specific cache configurations.
     * 
     * Cache names and TTLs:
     * - trendingFeed: Caches trending feed results (configurable TTL, default 15 minutes)
     * - trendingFeedWithTimeWindow: Caches time-windowed trending feeds (configurable TTL)
     * - personalizedFeed: Caches personalized feed results (default 5 minutes)
     * - userInterestProfile: Caches user interest profiles (default 5 minutes)
     * - contentQualityScore: Caches content quality scores (default 60 minutes)
     * - default: All other caches use 15 minute TTL
     * 
     * Requirements: 8.1, 8.2, 8.3, 8.5
     * 
     * @param connectionFactory Redis connection factory
     * @return configured RedisCacheManager
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // Default cache configuration
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(15))
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer())
            )
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new GenericJackson2JsonRedisSerializer()
                )
            )
            .disableCachingNullValues();

        // Specific cache configurations for different feed types
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        
        // Trending feed cache - uses configurable TTL from application.yml
        cacheConfigurations.put("trendingFeed", 
            defaultConfig.entryTtl(Duration.ofMinutes(trendingCacheTtlMinutes)));
        
        // Trending feed with time window cache - uses same TTL as trending feed
        cacheConfigurations.put("trendingFeedWithTimeWindow", 
            defaultConfig.entryTtl(Duration.ofMinutes(trendingCacheTtlMinutes)));

        // Personalized feed cache - 5 minute TTL for fresh recommendations
        // Requirements: 8.1, 8.5
        cacheConfigurations.put("personalizedFeed",
            defaultConfig.entryTtl(Duration.ofMinutes(personalizedCacheTtlMinutes)));

        // User interest profile cache - 5 minute TTL for behavioral data
        // Requirements: 8.2
        cacheConfigurations.put("userInterestProfile",
            defaultConfig.entryTtl(Duration.ofMinutes(userInterestProfileCacheTtlMinutes)));

        // Content quality score cache - 1 hour TTL for engagement metrics
        // Requirements: 8.3
        cacheConfigurations.put("contentQualityScore",
            defaultConfig.entryTtl(Duration.ofMinutes(contentQualityScoreCacheTtlMinutes)));

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(cacheConfigurations)
            .build();
    }
}
