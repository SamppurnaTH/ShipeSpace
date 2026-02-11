package com.shipspace.monitoring;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for logging configuration and monitoring functionality.
 * Tests logging behavior, levels, and output for monitoring and debugging.
 * 
 * Requirements tested: 9.5, 11.5
 */
class LoggingConfigurationTest {

    private Logger logger;
    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void setUp() {
        // Get logger for testing
        logger = (Logger) LoggerFactory.getLogger("com.shipspace.test");
        
        // Create and start a ListAppender to capture log events
        listAppender = new ListAppender<>();
        listAppender.start();
        
        // Add appender to logger
        logger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        // Clean up
        listAppender.stop();
        logger.detachAppender(listAppender);
    }

    @Test
    void logging_CapturesInfoLevel() {
        // Arrange
        logger.setLevel(Level.INFO);
        String message = "Test INFO message";

        // Act
        logger.info(message);

        // Assert
        List<ILoggingEvent> logsList = listAppender.list;
        assertThat(logsList).hasSize(1);
        assertThat(logsList.get(0).getMessage()).isEqualTo(message);
        assertThat(logsList.get(0).getLevel()).isEqualTo(Level.INFO);
    }

    @Test
    void logging_CapturesDebugLevel() {
        // Arrange
        logger.setLevel(Level.DEBUG);
        String message = "Test DEBUG message";

        // Act
        logger.debug(message);

        // Assert
        List<ILoggingEvent> logsList = listAppender.list;
        assertThat(logsList).hasSize(1);
        assertThat(logsList.get(0).getMessage()).isEqualTo(message);
        assertThat(logsList.get(0).getLevel()).isEqualTo(Level.DEBUG);
    }

    @Test
    void logging_CapturesErrorLevel() {
        // Arrange
        logger.setLevel(Level.ERROR);
        String message = "Test ERROR message";

        // Act
        logger.error(message);

        // Assert
        List<ILoggingEvent> logsList = listAppender.list;
        assertThat(logsList).hasSize(1);
        assertThat(logsList.get(0).getMessage()).isEqualTo(message);
        assertThat(logsList.get(0).getLevel()).isEqualTo(Level.ERROR);
    }

    @Test
    void logging_CapturesWarnLevel() {
        // Arrange
        logger.setLevel(Level.WARN);
        String message = "Test WARN message";

        // Act
        logger.warn(message);

        // Assert
        List<ILoggingEvent> logsList = listAppender.list;
        assertThat(logsList).hasSize(1);
        assertThat(logsList.get(0).getMessage()).isEqualTo(message);
        assertThat(logsList.get(0).getLevel()).isEqualTo(Level.WARN);
    }

    @Test
    void logging_FiltersLowerLevels() {
        // Arrange
        logger.setLevel(Level.WARN);

        // Act
        logger.debug("Debug message");
        logger.info("Info message");
        logger.warn("Warn message");
        logger.error("Error message");

        // Assert - Only WARN and ERROR should be captured
        List<ILoggingEvent> logsList = listAppender.list;
        assertThat(logsList).hasSize(2);
        assertThat(logsList.get(0).getLevel()).isEqualTo(Level.WARN);
        assertThat(logsList.get(1).getLevel()).isEqualTo(Level.ERROR);
    }

    @Test
    void logging_CapturesExceptionStackTrace() {
        // Arrange
        logger.setLevel(Level.ERROR);
        Exception exception = new RuntimeException("Test exception");

        // Act
        logger.error("Error occurred", exception);

        // Assert
        List<ILoggingEvent> logsList = listAppender.list;
        assertThat(logsList).hasSize(1);
        assertThat(logsList.get(0).getMessage()).isEqualTo("Error occurred");
        assertThat(logsList.get(0).getThrowableProxy()).isNotNull();
        assertThat(logsList.get(0).getThrowableProxy().getMessage()).isEqualTo("Test exception");
    }

    @Test
    void logging_CapturesLoggerName() {
        // Arrange
        logger.setLevel(Level.INFO);

        // Act
        logger.info("Test message");

        // Assert
        List<ILoggingEvent> logsList = listAppender.list;
        assertThat(logsList).hasSize(1);
        assertThat(logsList.get(0).getLoggerName()).isEqualTo("com.shipspace.test");
    }

    @Test
    void logging_CapturesTimestamp() {
        // Arrange
        logger.setLevel(Level.INFO);
        long beforeLog = System.currentTimeMillis();

        // Act
        logger.info("Test message");

        // Assert
        long afterLog = System.currentTimeMillis();
        List<ILoggingEvent> logsList = listAppender.list;
        assertThat(logsList).hasSize(1);
        assertThat(logsList.get(0).getTimeStamp()).isBetween(beforeLog, afterLog);
    }

    @Test
    void logging_HandlesMultipleMessages() {
        // Arrange
        logger.setLevel(Level.INFO);

        // Act
        logger.info("Message 1");
        logger.info("Message 2");
        logger.info("Message 3");

        // Assert
        List<ILoggingEvent> logsList = listAppender.list;
        assertThat(logsList).hasSize(3);
        assertThat(logsList.get(0).getMessage()).isEqualTo("Message 1");
        assertThat(logsList.get(1).getMessage()).isEqualTo("Message 2");
        assertThat(logsList.get(2).getMessage()).isEqualTo("Message 3");
    }

    @Test
    void logging_HandlesParameterizedMessages() {
        // Arrange
        logger.setLevel(Level.INFO);
        String param1 = "value1";
        int param2 = 42;

        // Act
        logger.info("Test message with params: {} and {}", param1, param2);

        // Assert
        List<ILoggingEvent> logsList = listAppender.list;
        assertThat(logsList).hasSize(1);
        assertThat(logsList.get(0).getFormattedMessage()).isEqualTo("Test message with params: value1 and 42");
    }

    @Test
    void logging_RootLoggerExists() {
        // Act
        Logger rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);

        // Assert
        assertThat(rootLogger).isNotNull();
        assertThat(rootLogger.getLevel()).isNotNull();
    }

    @Test
    void logging_ShipspacePackageLoggerExists() {
        // Act
        Logger shipspaceLogger = (Logger) LoggerFactory.getLogger("com.shipspace");

        // Assert
        assertThat(shipspaceLogger).isNotNull();
    }

    @Test
    void logging_LoggerContextIsConfigured() {
        // Act
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();

        // Assert
        assertThat(loggerContext).isNotNull();
        assertThat(loggerContext.getName()).isEqualTo("default");
    }

    @Test
    void logging_HandlesNullParameters() {
        // Arrange
        logger.setLevel(Level.INFO);

        // Act
        logger.info("Test message with null: {}", (Object) null);

        // Assert
        List<ILoggingEvent> logsList = listAppender.list;
        assertThat(logsList).hasSize(1);
        assertThat(logsList.get(0).getFormattedMessage()).isEqualTo("Test message with null: null");
    }

    @Test
    void logging_HandlesEmptyMessage() {
        // Arrange
        logger.setLevel(Level.INFO);

        // Act
        logger.info("");

        // Assert
        List<ILoggingEvent> logsList = listAppender.list;
        assertThat(logsList).hasSize(1);
        assertThat(logsList.get(0).getMessage()).isEmpty();
    }
}
