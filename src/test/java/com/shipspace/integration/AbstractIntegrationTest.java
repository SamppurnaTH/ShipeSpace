package com.shipspace.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for integration tests using Testcontainers.
 *
 * Phase 6: Provides real MongoDB and Redis containers shared across all
 * integration test subclasses. Containers are started once per JVM (static)
 * to minimize test suite startup time.
 *
 * Why Testcontainers over embedded MongoDB:
 * - Embedded MongoDB (flapdoodle) does not support transactions (requires replica set)
 * - Testcontainers MongoDB can be started as a replica set for full transaction support
 * - Redis Testcontainers gives a real Redis instance for Lua script testing
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer(
            DockerImageName.parse("mongo:6.0"))
            .withReuse(true);

    // Redis via testcontainers-redis
    @Container
    static final com.redis.testcontainers.RedisContainer REDIS =
            new com.redis.testcontainers.RedisContainer(
                    DockerImageName.parse("redis:7-alpine"))
            .withReuse(true);

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("spring.redis.host", REDIS::getHost);
        registry.add("spring.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.redis.password", () -> "");
        registry.add("jwt.secret", () -> "integration-test-secret-key-minimum-32-chars!!");
        registry.add("jwt.expiration", () -> "86400000");
        registry.add("jwt.refresh-expiration", () -> "604800000");
        registry.add("github.oauth.client-id", () -> "test-client-id");
        registry.add("github.oauth.client-secret", () -> "test-client-secret");
        registry.add("SWAGGER_ENABLED", () -> "false");
        registry.add("cache.enabled", () -> "true");
    }
}
