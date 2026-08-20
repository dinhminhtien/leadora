package com.novax.leadora.application.usecase.chat.time;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.IsoFields;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The assistant's sense of "now", and the named periods a question can refer to.
 *
 * <p><b>Why this exists at all.</b> A language model has no clock. Asked for "leads created today"
 * it cannot know what today is unless the prompt says so, and until now the prompt did not — which
 * is why the system prompt had to carry a rule telling it to refuse period questions outright.
 * Supplying the date turns that refusal into an answer.
 *
 * <p><b>Why the anchors are computed here rather than by the model.</b> Calendar arithmetic is
 * exactly the kind of thing an LLM does plausibly and wrongly: which day a week starts on, how many
 * days a month has, which quarter a date falls in. Computing them with {@code java.time} makes them
 * deterministic and testable, and follows the pattern the rest of this package already uses —
 * supply facts, not instructions.
 *
 * <p><b>Why the timezone is explicit.</b> {@code OffsetDateTime.now()} follows the JVM's default
 * zone, which in a container is UTC. A lead created at 06:00 on 9 August in Ho Chi Minh City is
 * stored at 23:00 on 8 August UTC, so "today" resolved against the server clock silently answers
 * for the wrong calendar day — for seven hours out of every twenty-four. Every boundary in the chat
 * pipeline is therefore taken against {@link #zone()}, not the default.
 *
 * <p><b>Instant and calendar are separate inputs.</b> The injected {@link Clock} says <em>when</em>
 * it is; {@link #zone()} says which calendar to read that against. Keeping them apart is what makes
 * the class testable — a test pins the instant and leaves the calendar alone — and it stops the
 * container's default zone from leaking back in through the clock. See {@code TimeConfig} for the
 * bean, and for how to point it at a source other than the host.
 */
@Component
public class ChatClock {

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mmXXX");

    /** Business calendar timezone. Override per environment if the company is not in Vietnam. */
    @Value("${app.business-zone:Asia/Ho_Chi_Minh}")
    private String businessZone;

    /** Supplies the instant only; the calendar it is read against is {@link #zone()}. */
    private final Clock clock;

    public ChatClock(Clock clock) {
        this.clock = clock;
    }

    public ZoneId zone() {
        return ZoneId.of(businessZone);
    }

    /**
     * The injected instant, expressed in the business calendar.
     *
     * <p>{@code withZone} rather than the clock's own zone: a fixed clock in a test carries whatever
     * zone it was built with, and honouring that would let the test's incidental choice decide which
     * calendar day the production code sees.
     */
    public OffsetDateTime now() {
        return OffsetDateTime.now(clock.withZone(zone()));
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(zone()));
    }

    /**
     * Every period the assistant can be asked about, resolved against today.
     *
     * <p>One map serves two consumers — the prompt block below and {@code DateRangeResolver} — so
     * the dates the model is shown and the dates the queries actually use can never drift apart.
     * Insertion-ordered: the prompt reads best from narrowest to widest.
     */
    public Map<String, ChatDateRange> anchors() {
        LocalDate today = today();
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate lastMonthStart = monthStart.minusMonths(1);
        LocalDate quarterStart = today.with(IsoFields.DAY_OF_QUARTER, 1);
        LocalDate lastQuarterStart = quarterStart.minusMonths(3);
        LocalDate yearStart = today.withDayOfYear(1);

        Map<String, ChatDateRange> anchors = new LinkedHashMap<>();
        anchors.put("today", range(today, today, "today"));
        anchors.put("yesterday", range(today.minusDays(1), today.minusDays(1), "yesterday"));
        anchors.put("last_7_days", range(today.minusDays(6), today, "the last 7 days"));
        anchors.put("this_week", range(weekStart, today, "this week (from Monday)"));
        anchors.put("last_week", range(weekStart.minusWeeks(1), weekStart.minusDays(1), "last week"));
        anchors.put("last_30_days", range(today.minusDays(29), today, "the last 30 days"));
        anchors.put("this_month", range(monthStart, monthStart.plusMonths(1).minusDays(1), "this month"));
        anchors.put("last_month",
                range(lastMonthStart, lastMonthStart.plusMonths(1).minusDays(1), "last month"));
        anchors.put("this_quarter",
                range(quarterStart, quarterStart.plusMonths(3).minusDays(1), "this quarter"));
        anchors.put("last_quarter",
                range(lastQuarterStart, lastQuarterStart.plusMonths(3).minusDays(1), "last quarter"));
        anchors.put("this_year", range(yearStart, yearStart.plusYears(1).minusDays(1), "this year"));
        anchors.put("last_year", range(yearStart.minusYears(1), yearStart.minusDays(1), "last year"));
        return anchors;
    }

    /** A specific calendar month of a given year, e.g. "tháng 7". */
    public ChatDateRange month(int year, int month) {
        LocalDate start = LocalDate.of(year, month, 1);
        return range(start, start.plusMonths(1).minusDays(1),
                start.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + year);
    }

    /** A single calendar day. */
    public ChatDateRange day(LocalDate date) {
        return range(date, date, date.format(ISO_DATE));
    }

    /** An explicit span between two days, inclusive at both ends. */
    public ChatDateRange between(LocalDate from, LocalDate to) {
        LocalDate lo = from.isAfter(to) ? to : from;
        LocalDate hi = from.isAfter(to) ? from : to;
        return range(lo, hi, lo.format(ISO_DATE) + " to " + hi.format(ISO_DATE));
    }

    /** The last {@code days} days including today. */
    public ChatDateRange lastDays(int days) {
        LocalDate today = today();
        return range(today.minusDays(Math.max(1, days) - 1L), today, "the last " + days + " days");
    }

    /**
     * The block handed to the model so it can talk about dates at all.
     *
     * <p>It is shown even when the question names no period: the model still needs today's date to
     * read a stored {@code created} timestamp as "two days ago" rather than as an opaque string,
     * and to notice that a quotation's {@code valid until} has passed.
     */
    public String promptBlock() {
        OffsetDateTime now = now();
        StringBuilder sb = new StringBuilder("=== CURRENT TIME (business timezone ")
                .append(businessZone).append(") ===\n");
        sb.append("Now: ").append(now.format(STAMP))
                .append(" (").append(now.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH))
                .append(")\n");
        sb.append("Resolved periods — use these exact dates, do not compute your own:\n");
        anchors().forEach((key, r) -> sb.append("  ").append(key).append(" = ")
                .append(r.from().format(ISO_DATE)).append(" .. ").append(r.to().format(ISO_DATE))
                .append('\n'));
        return sb.toString();
    }

    private static ChatDateRange range(LocalDate from, LocalDate to, String label) {
        return new ChatDateRange(from, to, label);
    }
}
