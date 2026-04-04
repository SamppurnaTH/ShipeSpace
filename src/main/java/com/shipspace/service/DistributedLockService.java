package com.shipspace.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Thin wrapper around Redis SETNX for distributed job locking.
 * Guarantees only one instance of a scheduled job runs at a time.
 *
 * Usage:
 *   lockService.runWithLock("my-job", Duration.ofMinutes(10), () -> { ... });
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DistributedLockService {

    private static final String KEY_PREFIX = "lock:job:";
    private final StringRedisTemplate redisTemplate;

    /**
     * Attempt to acquire the lock and run the task.
     * If the lock is already held, the task is skipped.
     * If Redis is unavailable, the task is skipped (fail-safe — better to skip
     * a scheduled job than to run it on every instance simultaneously).
     */
    public boolean runWithLock(String jobName, Duration ttl, Runnable task) {
        String key = KEY_PREFIX + jobName;
        Boolean acquired;
        try {
            acquired = redisTemplate.opsForValue().setIfAbsent(key, "1", ttl);
        } catch (Exception e) {
            log.error("Job '{}' skipped — Redis unavailable for lock acquisition: {}", jobName, e.getMessage());
            return false;
        }
        if (!Boolean.TRUE.equals(acquired)) {
            log.info("Job '{}' skipped — another instance holds the lock", jobName);
            return false;
        }
        try {
            task.run();
            return true;
        } finally {
            try {
                redisTemplate.delete(key);
            } catch (Exception e) {
                log.warn("Failed to release lock for job '{}': {}", jobName, e.getMessage());
                // Lock will auto-expire via TTL — safe to ignore
            }
            log.debug("Lock released for job '{}'", jobName);
        }
    }

    /**
     * Attempt to acquire the lock and run a task that returns a value.
     */
    public <T> T runWithLock(String jobName, Duration ttl, Supplier<T> task, T skipValue) {
        String key = KEY_PREFIX + jobName;
        Boolean acquired;
        try {
            acquired = redisTemplate.opsForValue().setIfAbsent(key, "1", ttl);
        } catch (Exception e) {
            log.error("Job '{}' skipped — Redis unavailable: {}", jobName, e.getMessage());
            return skipValue;
        }
        if (!Boolean.TRUE.equals(acquired)) {
            log.info("Job '{}' skipped — another instance holds the lock", jobName);
            return skipValue;
        }
        try {
            return task.get();
        } finally {
            try {
                redisTemplate.delete(key);
            } catch (Exception e) {
                log.warn("Failed to release lock for job '{}': {}", jobName, e.getMessage());
            }
        }
    }
}
