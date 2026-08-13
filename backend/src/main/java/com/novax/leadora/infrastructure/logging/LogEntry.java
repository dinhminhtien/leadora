package com.novax.leadora.infrastructure.logging;

import java.time.Instant;

public record LogEntry(
    Instant timestamp,
    String level,
    String threadName,
    String loggerName,
    String message,
    String exception,
    String userId,
    String userEmail,
    String correlationId
) {}
