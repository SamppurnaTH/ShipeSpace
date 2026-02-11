package com.shipspace;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for ShipSpace MVP Backend.
 * 
 * ShipSpace is a platform for technical builders to share project updates,
 * build social connections, and discover trending content.
 */
@SpringBootApplication
@EnableMongoAuditing
@EnableCaching
@EnableAsync
@EnableScheduling
public class ShipSpaceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShipSpaceApplication.class, args);
    }
}
