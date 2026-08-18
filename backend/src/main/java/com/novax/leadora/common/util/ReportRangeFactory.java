package com.novax.leadora.common.util;

import com.novax.leadora.common.exception.BusinessRuleException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Turns the {@code dateFrom} / {@code dateTo} query parameters of the UC-23 reports into an absolute
 * instant range.
 *
 * <p>The conversion happens in the <b>business time zone</b>, not UTC. Timestamps are stored as
 * {@code timestamptz}, so "31 July" is ambiguous until a zone is chosen: resolving it in UTC for a
 * UTC+7 business silently shifts every day boundary by seven hours, which drops records created
 * between midnight and 07:00 local time out of the day the manager asked for. The zone is
 * overridable through {@code leadora.reporting.zone} for deployments in another region.
 */
@Component
public class ReportRangeFactory {

    /** Lower sentinel for an open-ended "from": far enough back to precede any real record. */
    private static final OffsetDateTime BEGINNING_OF_TIME =
            OffsetDateTime.of(1970, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    /** Upper sentinel for an open-ended "to". */
    private static final OffsetDateTime END_OF_TIME =
            OffsetDateTime.of(2100, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    private final ZoneId zone;

    public ReportRangeFactory(@Value("${leadora.reporting.zone:Asia/Ho_Chi_Minh}") String zoneId) {
        this.zone = ZoneId.of(zoneId);
    }

    /**
     * @throws BusinessRuleException when the range is inverted — returning an empty report for
     *                               {@code dateFrom > dateTo} reads as "no data in this period",
     *                               which is a different and misleading answer.
     */
    public ReportRange resolve(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessRuleException("The start date must not be after the end date.");
        }
        OffsetDateTime start = (from != null)
                ? from.atStartOfDay(zone).toOffsetDateTime()
                : BEGINNING_OF_TIME;
        OffsetDateTime endExclusive = (to != null)
                ? to.plusDays(1).atStartOfDay(zone).toOffsetDateTime()
                : END_OF_TIME;
        return new ReportRange(from, to, start, endExclusive);
    }

    /** The zone day boundaries are resolved in — exposed so reports can label what they measured. */
    public ZoneId zone() {
        return zone;
    }
}
