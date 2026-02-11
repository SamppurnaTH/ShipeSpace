package com.shipspace;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Basic application context test to verify Spring Boot configuration.
 */
@SpringBootTest
@ActiveProfiles("test")
class ShipSpaceApplicationTests {

    @Test
    void contextLoads() {
        // This test verifies that the Spring application context loads successfully
        // with all configurations and dependencies properly wired
    }
}
