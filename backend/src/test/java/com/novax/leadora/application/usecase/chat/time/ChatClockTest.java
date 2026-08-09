package com.novax.leadora.application.usecase.chat.time;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The calendar boundaries — the moments this class is most likely to be wrong on, and the ones that
 * previously could only be checked by waiting for them to arrive.
 *
 * <p>Every test pins the instant with {@link Clock#fixed} and leaves the business calendar at
 * Vietnam, which is the split the class is built around: the clock says when, the zone says against
 * which calendar. The instants are written in UTC (the {@code Z} suffix) precisely because that is
 * where the original bug lived — a UTC instant late in the day is already tomorrow in Vietnam.
 */
class ChatClockTest {

    private static final String VN = "Asia/Ho_Chi_Minh";

    /** A clock stopped at the given UTC instant, reading the Vietnam business calendar. */
    private static ChatClock at(String utcInstant) {
        return at(utcInstant, VN);
    }

    private static ChatClock at(String utcInstant, String businessZone) {
        ChatClock clock = new ChatClock(Clock.fixed(Instant.parse(utcInstant), ZoneOffset.UTC));
        ReflectionTestUtils.setField(clock, "businessZone", businessZone);
        return clock;
    }

    private static LocalDate d(String iso) {
        return LocalDate.parse(iso);
    }

    @Nested
    @DisplayName("The instant comes from the injected clock, the calendar from the zone")
    class Wiring {

        @Test
        @DisplayName("a pinned clock decides the date, not the host")
        void usesTheInjectedInstant() {
            assertThat(at("2019-04-17T03:00:00Z").today()).isEqualTo(d("2019-04-17"));
        }

        /**
         * The bug this whole class exists for: 22:00 UTC is already 05:00 the next morning in
         * Vietnam, so a "today" taken from the container's UTC clock answers for the wrong day —
         * for seven hours out of every twenty-four.
         */
        @Test
        @DisplayName("a late UTC instant is already tomorrow in the business calendar")
        void utcEveningIsVietnamTomorrow() {
            ChatClock clock = at("2026-08-09T22:00:00Z");
            assertThat(clock.today()).isEqualTo(d("2026-08-10"));
            assertThat(clock.now().getOffset()).isEqualTo(ZoneOffset.ofHours(7));
            assertThat(clock.now().getHour()).isEqualTo(5);
        }

        @Test
        @DisplayName("the same instant is a different day in a different business calendar")
        void sameInstantDifferentCalendar() {
            assertThat(at("2026-08-09T22:00:00Z", VN).today()).isEqualTo(d("2026-08-10"));
            assertThat(at("2026-08-09T22:00:00Z", "UTC").today()).isEqualTo(d("2026-08-09"));
        }
    }

    @Nested
    @DisplayName("Midnight")
    class Midnight {

        /** 16:59:59Z is 23:59:59 in Vietnam — the last second of the business day. */
        @Test
        @DisplayName("the last second of a day still belongs to that day")
        void lastSecondOfTheDay() {
            ChatClock clock = at("2026-08-09T16:59:59Z");
            assertThat(clock.today()).isEqualTo(d("2026-08-09"));
            assertThat(clock.anchors().get("today").from()).isEqualTo(d("2026-08-09"));
        }

        /** One second later: 17:00:00Z is 00:00:00 the next day in Vietnam. */
        @Test
        @DisplayName("one second later every anchor has moved on")
        void firstSecondOfTheNextDay() {
            ChatClock clock = at("2026-08-09T17:00:00Z");
            assertThat(clock.today()).isEqualTo(d("2026-08-10"));
            assertThat(clock.anchors().get("today").from()).isEqualTo(d("2026-08-10"));
            assertThat(clock.anchors().get("yesterday").from()).isEqualTo(d("2026-08-09"));
        }

        @Test
        @DisplayName("a rolling window slides with the day rather than growing")
        void rollingWindowSlides() {
            assertThat(at("2026-08-09T16:59:59Z").anchors().get("last_7_days").from())
                    .isEqualTo(d("2026-08-03"));
            assertThat(at("2026-08-09T17:00:00Z").anchors().get("last_7_days").from())
                    .isEqualTo(d("2026-08-04"));
        }
    }

    @Nested
    @DisplayName("Week boundary")
    class Weeks {

        /**
         * 9 August 2026 is a Sunday and 10 August a Monday, so this pair crosses an ISO week.
         * A week that started on Sunday — the other common convention — would fail both of these.
         */
        @Test
        @DisplayName("Sunday still belongs to the week that began the previous Monday")
        void sundayEndsTheWeek() {
            var anchors = at("2026-08-09T10:00:00Z").anchors();
            assertThat(anchors.get("this_week").from()).isEqualTo(d("2026-08-03"));
            assertThat(anchors.get("last_week").from()).isEqualTo(d("2026-07-27"));
            assertThat(anchors.get("last_week").to()).isEqualTo(d("2026-08-02"));
        }

        @Test
        @DisplayName("Monday starts a new week, and the week just ended becomes last week")
        void mondayStartsTheWeek() {
            var anchors = at("2026-08-10T10:00:00Z").anchors();
            assertThat(anchors.get("this_week").from()).isEqualTo(d("2026-08-10"));
            assertThat(anchors.get("last_week").from()).isEqualTo(d("2026-08-03"));
            assertThat(anchors.get("last_week").to()).isEqualTo(d("2026-08-09"));
        }

        /** "This week" runs to today, not to the coming Sunday: it cannot cover the future. */
        @Test
        @DisplayName("this week ends today, not at the end of the week")
        void thisWeekDoesNotRunIntoTheFuture() {
            assertThat(at("2026-08-12T10:00:00Z").anchors().get("this_week").to())
                    .isEqualTo(d("2026-08-12"));
        }
    }

    @Nested
    @DisplayName("Month, quarter and year boundaries")
    class Calendars {

        @Test
        @DisplayName("the last day of the year and the first day of the next")
        void newYear() {
            var dec = at("2025-12-31T10:00:00Z").anchors();
            assertThat(dec.get("this_year").from()).isEqualTo(d("2025-01-01"));
            assertThat(dec.get("this_year").to()).isEqualTo(d("2025-12-31"));
            assertThat(dec.get("this_quarter").from()).isEqualTo(d("2025-10-01"));

            var jan = at("2026-01-01T10:00:00Z").anchors();
            assertThat(jan.get("this_year").from()).isEqualTo(d("2026-01-01"));
            assertThat(jan.get("last_year").from()).isEqualTo(d("2025-01-01"));
            assertThat(jan.get("last_year").to()).isEqualTo(d("2025-12-31"));
            // The quarter before Q1 2026 is Q4 2025 — the case a naive minusMonths(3) on the
            // current date, rather than on the quarter start, gets wrong.
            assertThat(jan.get("last_quarter").from()).isEqualTo(d("2025-10-01"));
            assertThat(jan.get("last_quarter").to()).isEqualTo(d("2025-12-31"));
        }

        @Test
        @DisplayName("every quarter starts on the right month")
        void quarterStarts() {
            assertThat(at("2026-02-14T10:00:00Z").anchors().get("this_quarter").from())
                    .isEqualTo(d("2026-01-01"));
            assertThat(at("2026-05-14T10:00:00Z").anchors().get("this_quarter").from())
                    .isEqualTo(d("2026-04-01"));
            assertThat(at("2026-08-14T10:00:00Z").anchors().get("this_quarter").from())
                    .isEqualTo(d("2026-07-01"));
            assertThat(at("2026-11-14T10:00:00Z").anchors().get("this_quarter").from())
                    .isEqualTo(d("2026-10-01"));
        }

        /**
         * On the 31st, a month computed by adding months to <em>today</em> instead of to the first
         * of the month lands on the wrong date. January and March are the pair that exposes it.
         */
        @Test
        @DisplayName("the 31st does not distort the month it is in, nor the month before")
        void endOfMonthArithmetic() {
            var jan31 = at("2026-01-31T10:00:00Z").anchors();
            assertThat(jan31.get("this_month").from()).isEqualTo(d("2026-01-01"));
            assertThat(jan31.get("this_month").to()).isEqualTo(d("2026-01-31"));

            var mar31 = at("2026-03-31T10:00:00Z").anchors();
            assertThat(mar31.get("last_month").from()).isEqualTo(d("2026-02-01"));
            assertThat(mar31.get("last_month").to()).isEqualTo(d("2026-02-28"));
        }

        @Test
        @DisplayName("February has 29 days in a leap year")
        void leapYear() {
            var feb = at("2028-02-15T10:00:00Z").anchors();
            assertThat(feb.get("this_month").to()).isEqualTo(d("2028-02-29"));

            // And the day itself exists, rather than being clamped to the 28th.
            assertThat(at("2028-02-29T10:00:00Z").today()).isEqualTo(d("2028-02-29"));
        }

        @Test
        @DisplayName("a named month knows its own length")
        void namedMonthLength() {
            ChatClock clock = at("2026-08-10T10:00:00Z");
            assertThat(clock.month(2026, 2).to()).isEqualTo(d("2026-02-28"));
            assertThat(clock.month(2028, 2).to()).isEqualTo(d("2028-02-29"));
            assertThat(clock.month(2026, 4).to()).isEqualTo(d("2026-04-30"));
        }
    }

    @Nested
    @DisplayName("The block handed to the model")
    class PromptBlock {

        @Test
        @DisplayName("it states the pinned date, the weekday and every anchor")
        void carriesTheResolvedDates() {
            String block = at("2026-08-09T17:00:00Z").promptBlock();

            assertThat(block)
                    .contains("Now: 2026-08-10T00:00+07:00 (Monday)")
                    .contains("today = 2026-08-10 .. 2026-08-10")
                    .contains("yesterday = 2026-08-09 .. 2026-08-09")
                    .contains("Asia/Ho_Chi_Minh");
        }

        /**
         * The prompt and the SQL predicates must come from one source. If the block could name a
         * date the queries did not use, the assistant would state a period it did not answer for.
         */
        @Test
        @DisplayName("it names exactly the anchors the resolver works from")
        void agreesWithTheAnchors() {
            ChatClock clock = at("2026-08-10T10:00:00Z");
            String block = clock.promptBlock();

            clock.anchors().forEach((key, range) ->
                    assertThat(block).contains(key + " = " + range.from() + " .. " + range.to()));
        }
    }
}
