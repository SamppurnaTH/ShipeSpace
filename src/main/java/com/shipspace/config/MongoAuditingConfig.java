package com.shipspace.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

/**
 * Dedicated configuration for MongoDB auditing.
 * Kept separate from ShipSpaceApplication so WebMvcTest slices can exclude it
 * (excludeFilters = @ComponentScan.Filter(classes = MongoAuditingConfig.class)).
 */
@Configuration
@EnableMongoAuditing
public class MongoAuditingConfig {
}
