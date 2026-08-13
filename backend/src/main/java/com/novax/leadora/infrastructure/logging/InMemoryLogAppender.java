package com.novax.leadora.infrastructure.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class InMemoryLogAppender extends AppenderBase<ILoggingEvent> {

    private static final int MAX_LIMIT = 1500;
    private static final Queue<LogEntry> BUFFER = new ConcurrentLinkedQueue<>();

    @Override
    protected void append(ILoggingEvent event) {
        if (event == null) {
            return;
        }

        String exception = null;
        IThrowableProxy throwableProxy = event.getThrowableProxy();
        if (throwableProxy != null) {
            exception = ThrowableProxyUtil.asString(throwableProxy);
        }

        java.util.Map<String, String> mdc = event.getMDCPropertyMap();
        String userId = mdc != null ? mdc.get("userId") : null;
        String userEmail = mdc != null ? mdc.get("userEmail") : null;
        String correlationId = mdc != null ? mdc.get("correlationId") : null;

        LogEntry entry = new LogEntry(
                Instant.ofEpochMilli(event.getTimeStamp()),
                event.getLevel().toString(),
                event.getThreadName(),
                event.getLoggerName(),
                event.getFormattedMessage(),
                exception,
                userId,
                userEmail,
                correlationId
        );

        BUFFER.add(entry);

        // Keep it thread-safe and capped
        while (BUFFER.size() > MAX_LIMIT) {
            BUFFER.poll();
        }
    }

    public static List<LogEntry> getLogs() {
        return new ArrayList<>(BUFFER);
    }

    public static void clear() {
        BUFFER.clear();
    }
}
