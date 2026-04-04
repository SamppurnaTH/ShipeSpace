package com.shipspace.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
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

    // Phase 1 fix: corrected property path. spring.cache.redis.time-to-live is in
    // milliseconds per Spring Boot convention, so we divide by 1000 for Duration.ofSeconds.
    @Value("${spring.cache.redis.time-to-live:300000}")
    private long defaultTtlMs;

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
            // Return the bounded Caffeine cache when Redis caching is disabled
            return localCacheManager();
        }

        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMillis(defaultTtlMs))
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

        // Phase 1 fix: trendingFeed and trendingFeedWithTimeWindow were missing from
        // the Redis config, causing them to fall through to the default TTL (300s).
        // The prefixCacheNameWith("shipspace:") above prevents key collisions:
        // Redis keys become "shipspace:trendingFeed::0:20" vs
        // "shipspace:trendingFeedWithTimeWindow::24h:0:20" — no overlap.
        cacheConfigurations.put("trendingFeed",
                config.entryTtl(Duration.ofMinutes(15)));
        cacheConfigurations.put("trendingFeedWithTimeWindow",
                config.entryTtl(Duration.ofMinutes(15)));

        // User interest profile - 5-minute TTL (updated by behavioral tracking)
        cacheConfigurations.put("userInterestProfile",
                config.entryTtl(Duration.ofMinutes(5)));

        // Content quality score - 1-hour TTL (updated by background job)
        cacheConfigurations.put("contentQualityScore",
                config.entryTtl(Duration.ofHours(1)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }

    /**
     * Local cache manager using Caffeine — bounded size and TTL.
     * Used when Redis is unavailable or cache.enabled=false.
     * Prevents unbounded heap growth from the previous ConcurrentMapCacheManager.
     */
    @Bean
    public CacheManager localCacheManager() {
        com.github.benmanes.caffeine.cache.Caffeine<Object, Object> caffeine =
                com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
                        .maximumSize(1000)
                        .expireAfterWrite(java.time.Duration.ofSeconds(300));

        org.springframework.cache.caffeine.CaffeineCacheManager manager =
                new org.springframework.cache.caffeine.CaffeineCacheManager();
        manager.setCaffeine(caffeine);
        manager.setCacheNames(java.util.Arrays.asList(
                "rank-summary", "user-profile", "project-data",
                "feed-cache", "trendingFeed", "trendingFeedWithTimeWindow",
                "userInterestProfile", "contentQualityScore"
        ));
        return manager;
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
