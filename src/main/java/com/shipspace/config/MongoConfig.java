package com.shipspace.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * MongoDB repository configuration.
 *
 * Explicit production indexes are owned by MongoIndexInitializer so startup has
 * one index source of truth.
 *
 * Transaction note: @Transactional on MongoDB requires a replica set. On a
 * standalone instance the annotation is best-effort and will not provide
 * rollback semantics.
 */
@Slf4j
@Configuration
@EnableMongoRepositories(basePackages = "com.shipspace.repository")
@RequiredArgsConstructor
public class MongoConfig {
}
