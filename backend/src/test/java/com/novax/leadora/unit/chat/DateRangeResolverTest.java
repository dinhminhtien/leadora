package com.novax.leadora.unit.chat;
import com.novax.leadora.application.usecase.chat.intent.*;

import com.novax.leadora.application.usecase.chat.time.ChatClock;
import com.novax.leadora.application.usecase.chat.time.ChatDateRange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The period a question is asking about.
 *
 * <p>Uses a real {@link ChatClock} rather than a mock: the whole point of the class is calendar
 * arithmetic, and a mock would assert that the resolver calls the clock rather than that it gets
 * the dates right. The clock is pinned to a fixed timezone; the tests are written relative to
 * "today" so they do not rot overnight.
 */
class DateRangeResolverTest {

    private ChatClock clock;
    private DateRangeResolver resolver;

    @BeforeEach
    void setUp() {
        // A real (moving) clock: these tests assert relative to today, so they stay valid
        // whenever they run. The fixed-instant boundary cases live in ChatClockTest.
        clock = new ChatClock(Clock.systemUTC());
        ReflectionTestUtils.setField(clock, "businessZone", "Asia/Ho_Chi_Minh");
        resolver = new DateRangeResolver(clock);
    }

    private LocalDate today() {
        return clock.today();
    }

    @Nested
    @DisplayName("Named periods")
    class Named {

        @ParameterizedTest(name = "\"{0}\" means today")
        @ValueSource(strings = {
                "cho tôi xem lead mới tạo ngày hôm nay",
                "lead hom nay co bao nhieu",
                "how many leads were created today",
                "deal hôm nay?"
        })
        void resolvesToday(String question) {
            ChatDateRange r = resolver.detect(question);
            assertThat(r.from()).isEqualTo(today());
            assertThat(r.to()).isEqualTo(today());
        }

        @Test
        @DisplayName("\"hôm qua\" is yesterday, not today")
        void yesterday() {
            ChatDateRange r = resolver.detect("có bao nhiêu lead hôm qua");
            assertThat(r.from()).isEqualTo(today().minusDays(1));
            assertThat(r.to()).isEqualTo(today().minusDays(1));
        }

        @Test
        @DisplayName("\"tháng này\" starts on the 1st and ends on the last day of the month")
        void thisMonth() {
            ChatDateRange r = resolver.detect("doanh số tháng này");
            assertThat(r.from()).isEqualTo(today().withDayOfMonth(1));
            assertThat(r.to()).isEqualTo(today().withDayOfMonth(1).plusMonths(1).minusDays(1));
        }

        @Test
        @DisplayName("\"tháng trước\" is the previous calendar month, not the last 30 days")
        void lastMonth() {
            LocalDate start = today().withDayOfMonth(1).minusMonths(1);
            ChatDateRange r = resolver.detect("báo cáo tháng trước");
            assertThat(r.from()).isEqualTo(start);
            assertThat(r.to()).isEqualTo(start.plusMonths(1).minusDays(1));
        }

        @Test
        @DisplayName("a week runs Monday to today, not a rolling seven days")
        void thisWeek() {
            ChatDateRange r = resolver.detect("lead tuần này");
            assertThat(r.from().getDayOfWeek()).isEqualTo(java.time.DayOfWeek.MONDAY);
            assertThat(r.to()).isEqualTo(today());
        }
    }

    @Nested
    @DisplayName("Counted and written periods")
    class Explicit {

        @Test
        @DisplayName("\"7 ngày qua\" includes today, so it spans 7 days not 8")
        void lastNDays() {
            ChatDateRange r = resolver.detect("lead trong 7 ngày qua");
            assertThat(r.from()).isEqualTo(today().minusDays(6));
            assertThat(r.to()).isEqualTo(today());
        }

        /**
         * "3 tháng qua" must not be swallowed by the bare "tháng qua" anchor, which means 30 days.
         */
        @Test
        @DisplayName("a counted month span beats the bare \"tháng qua\" anchor")
        void countedMonthsBeatTheAnchor() {
            ChatDateRange r = resolver.detect("doanh thu 3 tháng qua");
            assertThat(r.from()).isEqualTo(today().minusMonths(3));
        }

        /**
         * The same trap one unit down: "2 tuần qua" used to match the bare "tuần qua" anchor and
         * answer for seven days. A period recognised as the wrong period is reported with full
         * confidence, which is worse than one that is not recognised at all.
         */
        @ParameterizedTest(name = "{0} week(s) means {0}x7 days")
        @ValueSource(ints = {1, 2, 3, 6})
        void countedWeeksBeatTheAnchor(int weeks) {
            ChatDateRange r = resolver.detect("lead " + weeks + " tuần qua");
            assertThat(r.from()).isEqualTo(today().minusDays(weeks * 7L - 1));
            assertThat(r.to()).isEqualTo(today());
        }

        @Test
        @DisplayName("the bare \"tuần qua\" still means seven days")
        void bareWeekAnchorUnchanged() {
            assertThat(resolver.detect("lead tuần qua").from()).isEqualTo(today().minusDays(6));
        }

        @Test
        @DisplayName("\"tháng 7\" is July of the current year")
        void monthOfYear() {
            ChatDateRange r = resolver.detect("lead tháng 7 có bao nhiêu");
            assertThat(r.from()).isEqualTo(LocalDate.of(today().getYear(), 7, 1));
            assertThat(r.to()).isEqualTo(LocalDate.of(today().getYear(), 7, 31));
        }

        /**
         * Stripped of diacritics "thắng" (won) and "tháng" (month) are the same six letters. Without
         * the guard, a question about won deals was silently narrowed to a month.
         */
        /**
         * Vietnamese counts with a classifier after the number, so the collision is not limited to
         * CRM nouns: "thắng 5 vụ" and "thắng 2 lần" were narrowing whole answers to May and
         * February, and saying so confidently.
         */
        @ParameterizedTest(name = "\"{0}\" is not read as a month")
        @ValueSource(strings = {
                "ai thắng 7 deal nhiều nhất",
                "đã thắng 5 vụ lớn",
                "thắng 2 lần liên tiếp",
                "thắng 3 giao dịch lớn"})
        void winCountsAreNotMonths(String question) {
            assertThat(resolver.detect(question).isAllTime()).isTrue();
        }

        /** The genuine month reference must still work. */
        @Test
        @DisplayName("\"tháng 5\" is still May")
        void aRealMonthStillMatches() {
            assertThat(resolver.detect("doanh số tháng 5").from())
                    .isEqualTo(LocalDate.of(today().getYear(), 5, 1));
        }

        /**
         * Anchors used to be matched on a literal surrounding space, which ordinary punctuation
         * defeated — a comma after a leading time phrase is common in Vietnamese.
         */
        @ParameterizedTest(name = "punctuation does not hide the period: {0}")
        @ValueSource(strings = {
                "Hôm nay, có bao nhiêu lead?",
                "Liệt kê lead hôm nay.",
                "hôm nay!",
                "(hôm nay) có gì mới"})
        void punctuationDoesNotDefeatTheAnchor(String question) {
            assertThat(resolver.detect(question).from()).isEqualTo(today());
        }

        /** The boundary check must still refuse a match inside a longer token. */
        @Test
        @DisplayName("\"17 ngày qua\" is 17 days, not the 7-day anchor")
        void doesNotMatchInsideALongerNumber() {
            assertThat(resolver.detect("lead 17 ngày qua").from())
                    .isEqualTo(today().minusDays(16));
        }

        @Test
        @DisplayName("one written date means that day")
        void singleIsoDate() {
            ChatDateRange r = resolver.detect("lead ngày 2026-03-05");
            assertThat(r.from()).isEqualTo(LocalDate.of(2026, 3, 5));
            assertThat(r.to()).isEqualTo(LocalDate.of(2026, 3, 5));
        }

        @Test
        @DisplayName("two written dates mean the span between them")
        void isoDateSpan() {
            ChatDateRange r = resolver.detect("từ 2026-03-01 đến 2026-03-31 có bao nhiêu deal");
            assertThat(r.from()).isEqualTo(LocalDate.of(2026, 3, 1));
            assertThat(r.to()).isEqualTo(LocalDate.of(2026, 3, 31));
        }

        @Test
        @DisplayName("the Vietnamese day/month/year form is read day-first")
        void dmyIsDayFirst() {
            ChatDateRange r = resolver.detect("lead ngày 05/03/2026");
            assertThat(r.from()).isEqualTo(LocalDate.of(2026, 3, 5));
        }

        @Test
        @DisplayName("an impossible date is ignored rather than failing the turn")
        void impossibleDateIsIgnored() {
            assertThat(resolver.detect("lead ngày 31/02/2026").isAllTime()).isTrue();
        }
    }

    @Nested
    @DisplayName("Falling back to the conversation")
    class Inheritance {

        @Test
        @DisplayName("a question naming no period is not filtered at all")
        void noPeriodMeansNoFilter() {
            assertThat(resolver.detect("cho tôi xem danh sách lead").isAllTime()).isTrue();
        }

        /**
         * The failure this prevents is silent: the user believes they are still looking at today
         * and is shown every record ever created.
         */
        @Test
        @DisplayName("a follow-up inherits the period from an earlier turn")
        void followUpInheritsThePeriod() {
            ChatDateRange r = resolver.resolve("ok, liệt kê chi tiết hơn",
                    List.of("cho tôi xem lead mới tạo hôm nay"));
            assertThat(r.from()).isEqualTo(today());
        }

        @Test
        @DisplayName("a period named in this turn overrides the earlier one")
        void currentTurnWins() {
            ChatDateRange r = resolver.resolve("còn tháng trước thì sao",
                    List.of("lead hôm nay"));
            assertThat(r.from()).isEqualTo(today().withDayOfMonth(1).minusMonths(1));
        }

        /**
         * Inheritance has to be undoable. Without this the first period named in a session pins
         * every later turn, so "tổng số lead từ trước đến nay" returned today's count — and the
         * Period line then told the model to present it as covering today. A wrong period is a
         * wrong number, not merely extra rows.
         */
        @ParameterizedTest(name = "\"{0}\" clears an inherited period")
        @ValueSource(strings = {
                "tổng số lead từ trước đến nay",
                "cho tôi con số từ trước tới nay",
                "bỏ lọc ngày đi",
                "how many leads of all time"})
        void anExplicitAllTimeClearsTheInheritedPeriod(String question) {
            assertThat(resolver.resolve(question, List.of("lead hôm nay")).isAllTime()).isTrue();
        }

        /** A period named in the same sentence still wins over the reset vocabulary. */
        @Test
        @DisplayName("\"tổng số lead hôm nay\" is still today, not all time")
        void anExplicitPeriodBeatsTheReset() {
            assertThat(resolver.resolve("tổng số lead hôm nay", List.of()).from())
                    .isEqualTo(today());
        }

        @Test
        @DisplayName("no period anywhere in the session means no filter")
        void nothingToInherit() {
            assertThat(resolver.resolve("liệt kê tiếp", List.of("danh sách deal")).isAllTime())
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("Boundaries are taken in the business timezone")
    class Boundaries {

        /**
         * The bug this guards against: {@code OffsetDateTime.now()} follows the JVM's zone, which is
         * UTC in a container, so a record created at 06:00 Vietnam time falls on the previous UTC
         * day and "today" answers for the wrong date.
         */
        @Test
        @DisplayName("a day starts at 00:00 +07:00, not at 00:00 UTC")
        void dayStartsInBusinessZone() {
            ChatDateRange r = resolver.detect("lead hôm nay");
            assertThat(r.start(clock.zone()).getOffset()).isEqualTo(java.time.ZoneOffset.ofHours(7));
            assertThat(r.start(clock.zone()).getHour()).isZero();
        }

        @Test
        @DisplayName("the last day is included up to its final instant")
        void endOfDayIsInclusive() {
            ChatDateRange r = resolver.detect("lead hôm nay");
            assertThat(r.end(clock.zone()).getHour()).isEqualTo(23);
            assertThat(r.end(clock.zone()).getMinute()).isEqualTo(59);
        }

        /**
         * An open-ended side resolves to a far-past / far-future sentinel, never to null.
         *
         * <p>Null was what the queries used to receive, guarded by {@code (:from IS NULL OR ...)}.
         * PostgreSQL decides a parameter's type when it prepares the statement, and a parameter
         * whose only use is {@code ? IS NULL} gives it nothing to work from, so every listing
         * failed with <i>could not determine data type of parameter</i> — before any value was
         * bound, therefore always. Retrieval swallows failures, so the assistant simply answered
         * "no data" to every question. A real timestamp leaves nothing to infer.
         */
        @Test
        @DisplayName("an unfiltered range still yields usable bounds, never null")
        void allTimeStillHasBounds() {
            ChatDateRange all = ChatDateRange.allTime();
            assertThat(all.isAllTime()).isTrue();
            assertThat(all.start(clock.zone())).isNotNull();
            assertThat(all.end(clock.zone())).isNotNull();
            // Wide enough that no real record can fall outside it.
            assertThat(all.start(clock.zone()).getYear()).isLessThanOrEqualTo(1970);
            assertThat(all.end(clock.zone()).getYear()).isGreaterThanOrEqualTo(2100);
            assertThat(all.start(clock.zone())).isBefore(all.end(clock.zone()));
        }
    }
}
