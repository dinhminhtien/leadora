package com.novax.leadora.common.util;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared helpers for the UC-23 reports: percentage maths and the mapping of JPQL {@code GROUP BY}
 * result tuples into lookup maps.
 *
 * <p>Date-range resolution moved to {@link ReportRangeFactory}: it needs the configured business
 * time zone, so it cannot stay static.
 */
public final class ReportingUtils {

    private ReportingUtils() {
    }

    /**
     * Safely computes a percentage rounded to two decimal places. A non-positive denominator yields
     * {@code 0.0} rather than NaN/Infinity — every report renders these straight into the UI.
     */
    public static double calculateRate(long part, long wholePart) {
        if (wholePart <= 0) {
            return 0.0;
        }
        return Math.round((part * 10000.0 / wholePart)) / 100.0;
    }

    /** Rounds to two decimal places; used for hour/day averages. */
    public static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /**
     * Maps {@code SELECT <key>, count(x) ... GROUP BY <key>} tuples into a map. Rows with a null key
     * are kept — an unassigned record is still a record, and dropping it here is what makes a
     * per-owner breakdown stop adding up to the headline total.
     */
    @SuppressWarnings({"unchecked", "null"})
    public static <K> Map<K, Long> countByKey(List<Object[]> rows) {
        Map<K, Long> result = new LinkedHashMap<>();
        for (Object[] row : rows) {
            K key = (K) row[0];
            result.merge(key, toLong(row[1]), Long::sum);
        }
        return result;
    }

    /** Null-safe lookup — an absent group means zero, not a missing entry to guard at every call. */
    public static <K> long countOf(Map<K, Long> counts, K key) {
        Long value = counts.get(key);
        return value == null ? 0L : value;
    }

    /** Sums selected groups, e.g. "all the statuses that count as a confirmed booking". */
    @SafeVarargs
    public static <K> long sumOf(Map<K, Long> counts, K... keys) {
        long total = 0;
        for (K key : keys) {
            total += countOf(counts, key);
        }
        return total;
    }

    public static long toLong(Object value) {
        return (value instanceof Number number) ? number.longValue() : 0L;
    }

    public static BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return (value instanceof Number number) ? BigDecimal.valueOf(number.doubleValue()) : BigDecimal.ZERO;
    }
}
