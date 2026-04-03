package com.shipspace;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for ShipSpace MVP Backend.
 */
@SpringBootApplication
@EnableMongoAuditing
@EnableCaching
@EnableAsync
@EnableScheduling
@EnableRetry
public class ShipSpaceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShipSpaceApplication.class, args);
    }
}
