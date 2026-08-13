package com.novax.leadora.infrastructure.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import jakarta.annotation.PostConstruct;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LogAppenderConfig {

    @PostConstruct
    public void init() {
        try {
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
            Logger rootLogger = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);

            InMemoryLogAppender appender = new InMemoryLogAppender();
            appender.setContext(context);
            appender.setName("IN_MEMORY_APPENDER");
            appender.start();

            rootLogger.addAppender(appender);
        } catch (Exception e) {
            System.err.println("Failed to initialize custom log appender: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
