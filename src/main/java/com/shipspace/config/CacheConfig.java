package com.shipspace.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
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
 * Cache configuration for production readiness.
 * 
 * <p>Configures multi-level caching:
 * <ul>
 *   <li>Local cache (ConcurrentMapCacheManager) for fast access</li>
 *   <li>Redis cache for distributed caching</li>
 * </ul>
 * 
 * <p>Cache configurations:
 * <ul>
 *   <li>rank-summary: 60 seconds (changes frequently)</li>
 *   <li>user-profile: 120 seconds (moderate change)</li>
 *   <li>project-data: 300 seconds (stable)</li>
 *   <li>feed-cache: 15 minutes (trending data)</li>
 * </ul>
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Value("${spring.cache.redis.time-to-live:300}")
    private long defaultTtl;

    @Value("${cache.rank-summary.ttl-seconds:60}")
    private long rankSummaryTtl;

    @Value("${cache.user-profile.ttl-seconds:120}")
    private long userProfileTtl;

    @Value("${cache.enabled:true}")
    private boolean cacheEnabled;

    /**
     * Redis-based cache manager for distributed caching.
     * 
     * <p>Uses JSON serialization for cache values and string keys
     * for human-readable cache inspection.
     */
    @Bean
    @Primary
    public CacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {
        if (!cacheEnabled) {
            return new ConcurrentMapCacheManager();
        }

        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(defaultTtl))
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer())
                )
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer())
                )
                .disableCachingNullValues()
                .prefixCacheNameWith("shipspace:");

        // Configure cache-specific TTLs
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        
        // Rank summary - short TTL due to frequent updates
        cacheConfigurations.put("rank-summary", 
                config.entryTtl(Duration.ofSeconds(rankSummaryTtl)));
        
        // User profile - moderate TTL
        cacheConfigurations.put("user-profile", 
                config.entryTtl(Duration.ofSeconds(userProfileTtl)));
        
        // Project data - longer TTL
        cacheConfigurations.put("project-data", 
                config.entryTtl(Duration.ofSeconds(300)));
        
        // Feed cache - longer TTL for trending data
        cacheConfigurations.put("feed-cache", 
                config.entryTtl(Duration.ofMinutes(15)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }

    /**
     * Local cache manager for development/testing.
     * 
     * <p>Provides fast in-memory caching without Redis dependency.
     * Use when Redis is unavailable or for local development.
     */
    @Bean
    public CacheManager localCacheManager() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager();
        cacheManager.setCacheNames(java.util.Arrays.asList(
                "rank-summary",
                "user-profile",
                "project-data",
                "feed-cache"
        ));
        return cacheManager;
    }

    /**
     * Cache statistics for monitoring.
     */
    @Bean
    public CacheStatistics cacheStatistics(CacheManager cacheManager) {
        return new CacheStatistics(cacheManager);
    }

    /**
     * Helper class for cache statistics.
     */
    public static class CacheStatistics {
        private final CacheManager cacheManager;

        public CacheStatistics(CacheManager cacheManager) {
            this.cacheManager = cacheManager;
        }

        public String getCacheInfo(String cacheName) {
            var cache = cacheManager.getCache(cacheName);
            if (cache == null) {
                return "Cache not found: " + cacheName;
            }
            
            // Note: Full statistics require CacheStatisticsMBean
            return String.format("Cache: %s, Available: true", cacheName);
        }

        public void evictCache(String cacheName) {
            var cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
            }
        }

        public void evictCacheKey(String cacheName, Object key) {
            var cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.evict(key);
            }
        }
    }
}
