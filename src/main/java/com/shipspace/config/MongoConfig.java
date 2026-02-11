package com.shipspace.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * MongoDB configuration for the ShipSpace API.
 * Enables MongoDB repositories and auditing features.
 */
@Configuration
@EnableMongoRepositories(basePackages = "com.shipspace.repository")
@EnableMongoAuditing
public class MongoConfig {
    // MongoDB configuration is primarily handled through application.yml
    // This class enables repository scanning and auditing features
}
