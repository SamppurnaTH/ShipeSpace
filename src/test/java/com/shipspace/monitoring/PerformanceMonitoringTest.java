package com.shipspace.monitoring;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for performance monitoring and logging under various conditions.
 * Tests logging behavior under load and concurrent operations for monitoring.
 * 
 * Requirements tested: 9.5, 11.5
 */
class PerformanceMonitoringTest {

    private Logger logger;
    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger("com.shipspace.performance");
        logger.setLevel(Level.INFO);
        
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        listAppender.stop();
        logger.detachAppender(listAppender);
    }

    @Test
    void logging_HandlesHighVolumeMessages() {
        // Arrange
        int messageCount = 1000;

        // Act
        for (int i = 0; i < messageCount; i++) {
            logger.info("Message {}", i);
        }

        // Assert
        List<ILoggingEvent> logsList = listAppender.list;
        assertThat(logsList).hasSize(messageCount);
    }

    @Test
    void logging_MaintainsOrderUnderLoad() {
        // Arrange
        int messageCount = 100;

        // Act
        for (int i = 0; i < messageCount; i++) {
            logger.info("Message {}", i);
        }

        // Assert
        List<ILoggingEvent> logsList = listAppender.list;
        for (int i = 0; i < messageCount; i++) {
            assertThat(logsList.get(i).getFormattedMessage()).isEqualTo("Message " + i);
        }
    }

    @Test
    void logging_HandlesLargeMessages() {
        // Arrange
        String largeMessage = "X".repeat(10000);

        // Act
        logger.info("Large message: {}", largeMessage);

        // Assert
        List<ILoggingEvent> logsList = listAppender.list;
        assertThat(logsList).hasSize(1);
        assertThat(logsList.get(0).getFormattedMessage()).contains(largeMessage);
    }

    @Test
    void logging_PerformanceWithParameterSubstitution() {
        // Arrange
        long startTime = System.nanoTime();

        // Act
        for (int i = 0; i < 100; i++) {
            logger.info("User {} performed action {} at time {}", "user" + i, "action" + i, System.currentTimeMillis());
        }

        // Assert
        long endTime = System.nanoTime();
        long durationMs = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);
        
        List<ILoggingEvent> logsList = listAppender.list;
        assertThat(logsList).hasSize(100);
        // Logging should be fast - less than 100ms for 100 messages
        assertThat(durationMs).isLessThan(100);
    }

    @Test
    void logging_HandlesRapidSuccessiveMessages() {
        // Arrange & Act
        IntStream.range(0, 50).forEach(i -> {
            logger.info("Rapid message {}", i);
        });

        // Assert
        List<ILoggingEvent> logsList = listAppender.list;
        assertThat(logsList).hasSize(50);
    }

    @Test
    void logging_TimestampsAreMonotonicallyIncreasing() {
        // Act
        for (int i = 0; i < 10; i++) {
            logger.info("Message {}", i);
        }

        // Assert
        List<ILoggingEvent> logsList = listAppender.list;
        for (int i = 1; i < logsList.size(); i++) {
            assertThat(logsList.get(i).getTimeStamp())
                    .isGreaterThanOrEqualTo(logsList.get(i - 1).getTimeStamp());
        }
    }

    @Test
    void logging_HandlesExceptionLoggingPerformance() {
        // Arrange
        long startTime = System.nanoTime();

        // Act
        for (int i = 0; i < 50; i++) {
            Exception e = new RuntimeException("Test exception " + i);
            logger.error("Error occurred", e);
        }

        // Assert
        long endTime = System.nanoTime();
        long durationMs = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);
        
        List<ILoggingEvent> logsList = listAppender.list;
        assertThat(logsList).hasSize(50);
        // Exception logging should still be reasonably fast
        assertThat(durationMs).isLessThan(200);
    }

    @Test
    void logging_HandlesMultipleLevelsUnderLoad() {
        // Act
        for (int i = 0; i < 100; i++) {
            if (i % 4 == 0) {
                logger.error("Error {}", i);
            } else if (i % 4 == 1) {
                logger.warn("Warn {}", i);
            } else if (i % 4 == 2) {
                logger.info("Info {}", i);
            } else {
                logger.debug("Debug {}", i);
            }
        }

        // Assert - Debug should be filtered out (logger level is INFO)
        List<ILoggingEvent> logsList = listAppender.list;
        assertThat(logsList).hasSize(75); // 25 ERROR + 25 WARN + 25 INFO
    }

    @Test
    void logging_HandlesComplexObjectLogging() {
        // Arrange
        Object complexObject = new Object() {
            @Override
            public String toString() {
                return "ComplexObject{field1=value1, field2=value2, field3=value3}";
            }
        };

        // Act
        logger.info("Processing object: {}", complexObject);

        // Assert
        List<ILoggingEvent> logsList = listAppender.list;
        assertThat(logsList).hasSize(1);
        assertThat(logsList.get(0).getFormattedMessage()).contains("ComplexObject");
    }

    @Test
    void logging_HandlesNullSafely() {
        // Act
        logger.info("Null value: {}", (Object) null);
        logger.info("Multiple nulls: {} and {}", null, null);

        // Assert
        List<ILoggingEvent> logsList = listAppender.list;
        assertThat(logsList).hasSize(2);
        assertThat(logsList.get(0).getFormattedMessage()).contains("null");
        assertThat(logsList.get(1).getFormattedMessage()).contains("null");
    }

    @Test
    void logging_MemoryEfficientWithManyMessages() {
        // Arrange
        Runtime runtime = Runtime.getRuntime();
        runtime.gc(); // Suggest garbage collection
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

        // Act
        for (int i = 0; i < 1000; i++) {
            logger.info("Message {}", i);
        }

        // Assert
        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
        long memoryUsed = memoryAfter - memoryBefore;
        
        // Memory usage should be reasonable (less than 10MB for 1000 messages)
        assertThat(memoryUsed).isLessThan(10 * 1024 * 1024);
    }

    @Test
    void logging_HandlesSpecialCharacters() {
        // Act
        logger.info("Special chars: {} {} {}", "日本語", "émojis 🚀", "symbols @#$%");

        // Assert
        List<ILoggingEvent> logsList = listAppender.list;
        assertThat(logsList).hasSize(1);
        assertThat(logsList.get(0).getFormattedMessage()).contains("日本語");
        assertThat(logsList.get(0).getFormattedMessage()).contains("émojis 🚀");
        assertThat(logsList.get(0).getFormattedMessage()).contains("symbols @#$%");
    }

    @Test
    void logging_HandlesEmptyParameterArray() {
        // Act
        logger.info("Message with no params");

        // Assert
        List<ILoggingEvent> logsList = listAppender.list;
        assertThat(logsList).hasSize(1);
        assertThat(logsList.get(0).getMessage()).isEqualTo("Message with no params");
    }

    @Test
    void logging_HandlesVeryLongParameterList() {
        // Act
        logger.info("Many params: {} {} {} {} {} {} {} {} {} {}", 
                1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

        // Assert
        List<ILoggingEvent> logsList = listAppender.list;
        assertThat(logsList).hasSize(1);
        assertThat(logsList.get(0).getFormattedMessage()).contains("1");
        assertThat(logsList.get(0).getFormattedMessage()).contains("10");
    }
}
