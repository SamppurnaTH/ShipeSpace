package com.shipspace.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Async thread pool configuration.
 *
 * Two pools:
 * - taskExecutor: general async work (notifications, audit, image uploads).
 *   Sized for moderate concurrency; CallerRunsPolicy provides back-pressure.
 * - behavioralTrackingExecutor: high-volume, low-priority signal tracking.
 *   Larger queue, DiscardOldestPolicy — it's acceptable to drop old signals
 *   under extreme load rather than block HTTP threads.
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * General-purpose async executor.
     * Used for notifications, audit logging, and image upload callbacks.
     * Core=10, Max=20, Queue=500 — sized for a social platform with moderate engagement.
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("async-task-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * Behavioral tracking executor — high volume, low priority.
     * Signals are best-effort; dropping old ones under load is acceptable.
     * Core=5, Max=10, Queue=1000 — absorbs bursts without blocking HTTP threads.
     */
    @Bean(name = "behavioralTrackingExecutor")
    public Executor behavioralTrackingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("behavioral-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardOldestPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
