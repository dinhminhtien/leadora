package com.novax.leadora.common.util;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Utility class for reporting use cases to centralize date boundary conversions
 * and rate computation calculations, resolving code duplication anti-patterns.
 */
public final class ReportingUtils {

    private ReportingUtils() {
    }

    /**
     * Converts a LocalDate to OffsetDateTime at the start of day (UTC).
     * If the input is null, returns Epoch Start (1970-01-01).
     */
    public static OffsetDateTime getStartOfDayOrEpoch(LocalDate date) {
        return date != null ? date.atStartOfDay().atOffset(ZoneOffset.UTC)
                : OffsetDateTime.of(1970, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    }

    /**
     * Converts a LocalDate to OffsetDateTime at the end of day (UTC).
     * If the input is null, returns Far Future (2100-12-31).
     */
    public static OffsetDateTime getEndOfDayOrFuture(LocalDate date) {
        return date != null ? date.atTime(LocalTime.MAX).atOffset(ZoneOffset.UTC)
                : OffsetDateTime.of(2100, 12, 31, 23, 59, 59, 999999999, ZoneOffset.UTC);
    }

    /**
     * Safely computes a percentage rate rounded to two decimal places.
     */
    public static double calculateRate(long part, long wholePart) {
        if (wholePart <= 0) {
            return 0.0;
        }
        return Math.round((part * 10000.0 / wholePart)) / 100.0;
    }
}
