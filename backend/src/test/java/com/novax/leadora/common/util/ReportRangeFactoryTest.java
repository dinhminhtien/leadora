package com.novax.leadora.common.util;

import com.novax.leadora.common.exception.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportRangeFactoryTest {

    private final ReportRangeFactory factory = new ReportRangeFactory("Asia/Ho_Chi_Minh");

    @Test
    @DisplayName("day boundaries are resolved in the business zone, not UTC")
    void boundariesUseTheBusinessZone() {
        ReportRange range = factory.resolve(LocalDate.of(2026, 7, 31), LocalDate.of(2026, 7, 31));

        // 31 July 00:00 +07:00 is 30 July 17:00Z — a record created at 05:00 local belongs to the
        // 31st, and resolving in UTC would have pushed it into the previous day and out of range.
        assertThat(range.start().toInstant())
                .isEqualTo(OffsetDateTime.of(2026, 7, 30, 17, 0, 0, 0, ZoneOffset.UTC).toInstant());
        assertThat(range.endExclusive().toInstant())
                .isEqualTo(OffsetDateTime.of(2026, 7, 31, 17, 0, 0, 0, ZoneOffset.UTC).toInstant());
    }

    @Test
    @DisplayName("the upper bound is exclusive midnight, not 23:59:59.999999999")
    void upperBoundIsExclusiveMidnight() {
        ReportRange range = factory.resolve(null, LocalDate.of(2026, 7, 31));

        // PostgreSQL narrows a nanosecond literal to microseconds and rounds .999999999 up to the
        // next whole second, so the old inclusive form could reach into the following day.
        assertThat(range.endExclusive().getNano()).isZero();
        assertThat(range.endExclusive().toLocalDate()).isEqualTo(LocalDate.of(2026, 8, 1));
    }

    @Test
    @DisplayName("an omitted bound opens the range instead of failing")
    void omittedBoundsOpenTheRange() {
        ReportRange all = factory.resolve(null, null);

        assertThat(all.from()).isNull();
        assertThat(all.to()).isNull();
        assertThat(all.start()).isBefore(all.endExclusive());
        assertThat(all.start().getYear()).isEqualTo(1970);
        assertThat(all.endExclusive().getYear()).isEqualTo(2100);
    }

    @Test
    @DisplayName("a single day is a non-empty range")
    void singleDayRangeIsNotEmpty() {
        LocalDate day = LocalDate.of(2026, 7, 31);

        ReportRange range = factory.resolve(day, day);

        assertThat(range.start()).isBefore(range.endExclusive());
    }

    @Test
    @DisplayName("an inverted range is rejected rather than silently returning no data")
    void invertedRangeIsRejected() {
        assertThatThrownBy(() -> factory.resolve(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 7, 1)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("must not be after");
    }

    @Test
    @DisplayName("the zone is configurable for deployments outside Vietnam")
    void zoneIsConfigurable() {
        ReportRange utc = new ReportRangeFactory("UTC")
                .resolve(LocalDate.of(2026, 7, 31), LocalDate.of(2026, 7, 31));

        assertThat(utc.start().toInstant())
                .isEqualTo(OffsetDateTime.of(2026, 7, 31, 0, 0, 0, 0, ZoneOffset.UTC).toInstant());
    }
}
