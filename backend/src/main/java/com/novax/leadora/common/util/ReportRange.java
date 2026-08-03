package com.novax.leadora.common.util;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * A resolved reporting period.
 *
 * <p>{@link #start()} is inclusive and {@link #endExclusive()} is exclusive — a half-open interval.
 * That is deliberate: the previous {@code <= endOfDay(23:59:59.999999999)} form relied on a
 * nanosecond literal that PostgreSQL rounds up to the next whole second when it narrows the value to
 * its microsecond precision, so a row created at exactly 00:00:00 of the following day could land
 * inside the range. {@code createdAt < startOfNextDay} has no such edge.
 *
 * @param from         the requested start date, or null for "since the beginning"
 * @param to           the requested end date, or null for "up to now"
 * @param start        inclusive lower bound
 * @param endExclusive exclusive upper bound
 */
public record ReportRange(LocalDate from, LocalDate to, OffsetDateTime start, OffsetDateTime endExclusive) {
}
