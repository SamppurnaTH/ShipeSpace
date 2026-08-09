package com.shipspace;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for ShipSpace MVP Backend.
 * MongoDB auditing is enabled via MongoAuditingConfig (separate class so WebMvcTest can exclude it).
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableCaching
@EnableAsync
@EnableScheduling
@EnableRetry
public class ShipSpaceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShipSpaceApplication.class, args);
    }
}
