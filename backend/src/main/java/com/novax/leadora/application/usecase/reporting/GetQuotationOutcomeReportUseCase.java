package com.novax.leadora.application.usecase.reporting;

import com.novax.leadora.api.dto.response.QuotationOutcomeReportResponse;
import com.novax.leadora.api.dto.response.QuotationOutcomeReportResponse.CountRow;
import com.novax.leadora.api.dto.response.QuotationOutcomeReportResponse.StaffRow;
import com.novax.leadora.api.dto.response.QuotationOutcomeReportResponse.StatusRow;
import com.novax.leadora.application.usecase.quotation.QuotationOutcome;
import com.novax.leadora.application.usecase.quotation.QuotationOutcomeClassifier;
import com.novax.leadora.common.util.ReportRange;
import com.novax.leadora.common.util.ReportRangeFactory;
import com.novax.leadora.common.util.ReportingUtils;
import com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus;
import com.novax.leadora.infrastructure.persistence.repository.QuotationOutcomeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * UC-23.5 — View Quotation Outcome Report.
 *
 * <p>The report is built from one consolidated aggregate and folded twice: once into the headline
 * totals and once per preparer, so the staff table <em>is</em> the headline before it is summed
 * rather than a second query that has to be kept in agreement with the first.
 *
 * <h2>Two questions, two populations</h2>
 *
 * <p>Anchoring everything on {@code created_at} made the report answer only "what became of the
 * quotations written this month", and answer it early — a quotation raised on the 30th is not a
 * loss, it is unanswered. Worse, the work that actually happened in the month was invisible: an
 * approval or a conversion on a quotation written in a previous period counted nowhere.
 *
 * <p>So the cohort figures keep the created_at axis and the activity figures sit on the timestamp of
 * the event itself. The two are not meant to reconcile, and the UI labels which is which.
 *
 * <h2>What the status column cannot be asked</h2>
 *
 * <p>{@code quotations.status} holds one value at a time and is overwritten as the quotation
 * advances, so it can report where things stand but never what happened. Three figures are read
 * from durable records instead: approvals from {@code approved_at}, customer answers from
 * {@code quotation_customer_responses} — the {@code quotations.customer_response} column is
 * populated on a negligible fraction of rows and would report near-zero acceptance — and approval
 * decisions from {@code quotation_approval_history}, which is the only place a revision request is
 * recorded at all.
 */
@Service
@RequiredArgsConstructor
public class GetQuotationOutcomeReportUseCase {

    private static final int MAX_STAFF = 50;
    private static final String UNATTRIBUTED_LABEL = "(No preparer recorded)";

    // Discriminators emitted by the consolidated query.
    private static final String COHORT_FACT = "COHORT_FACT";
    private static final String COHORT_APPROVAL = "COHORT_APPROVAL";
    private static final String COHORT_DISCOUNT = "COHORT_DISCOUNT";
    private static final String COHORT_SEND = "COHORT_SEND";
    private static final String COHORT_EXPIRY = "COHORT_EXPIRY";
    private static final String ACT_DECISION = "ACT_DECISION";
    private static final String ACT_APPROVED_STAMP = "ACT_APPROVED_STAMP";
    private static final String ACT_REPLY = "ACT_REPLY";
    private static final String ACT_REPLY_TIMED = "ACT_REPLY_TIMED";
    private static final String ACT_LOST_REASON = "ACT_LOST_REASON";
    private static final String ACT_CLOSURE = "ACT_CLOSURE";
    private static final String ACT_SENT = "ACT_SENT";
    private static final String ACT_CONVERTED = "ACT_CONVERTED";

    private static final String APPROVED = "APPROVED";
    private static final String NEVER_APPROVED = "NEVER_APPROVED";
    private static final String SENT = "SENT";
    private static final String NEVER_SENT = "NEVER_SENT";
    private static final String TIMED = "TIMED";
    private static final String REVISION_REQUESTED = "REVISION_REQUESTED";
    private static final String EXPIRED_NO_REPLY = "EXPIRED_NO_REPLY";
    private static final String EXPIRED_AFTER_REPLY = "EXPIRED_AFTER_REPLY";
    private static final String EXPIRED_NEVER_SENT = "EXPIRED_NEVER_SENT";

    /** Discount bands, in the order they are always displayed. */
    private static final List<String> DISCOUNT_ORDER = List.of("D0", "D1_10", "D11_20", "D21_PLUS");

    // The bands cut on <= 10 and <= 20, so the labels name the cut points rather than whole
    // percents: a 10.5% discount belongs to the second band and "11–20%" would deny it.
    private static final Map<String, String> LABELS = Map.of(
            "D0", "No discount",
            "D1_10", "Up to 10%",
            "D11_20", "Over 10%, up to 20%",
            "D21_PLUS", "Over 20%");

    private final QuotationOutcomeRepository quotationOutcomeRepository;
    private final ReportRangeFactory reportRangeFactory;

    /**
     * The report is team-wide for everyone who can reach it — the endpoint is MANAGER/ADMIN only —
     * so one cache entry per period serves every caller and the key needs no scope component.
     */
    @Cacheable(value = "quotation-outcome-report", key = "#from + '_' + #to", unless = "#result == null")
    @Transactional(readOnly = true)
    public QuotationOutcomeReportResponse execute(LocalDate from, LocalDate to) {
        ReportRange range = reportRangeFactory.resolve(from, to);

        Tally totals = new Tally();
        Map<UUID, Acc> byUser = new LinkedHashMap<>();

        for (Object[] row : quotationOutcomeRepository.quotationOutcomeAggregates(
                range.start(), range.endExclusive())) {
            String metric = str(row[0]);
            String bucket = str(row[1]);
            long count = ReportingUtils.toLong(row[4]);
            BigDecimal value = ReportingUtils.toBigDecimal(row[5]);

            totals.add(metric, bucket, count, value);
            fold(acc(byUser, row[2], row[3]), metric, bucket, count, value);
        }

        Cohort cohort = new Cohort(totals.buckets(COHORT_FACT), totals.moneyByBucket(COHORT_FACT));
        long total = cohort.live;
        long won = cohort.count(QuotationOutcome.WON);
        long lost = cohort.count(QuotationOutcome.LOST);
        long abandoned = cohort.count(QuotationOutcome.ABANDONED);
        long stillOpen = cohort.count(QuotationOutcome.OPEN);
        long cohortConverted = cohort.status(QuotationStatus.CONVERTED);

        long decisionsApproved = totals.count(ACT_DECISION, APPROVED);
        long decisionsRejected = totals.count(ACT_DECISION, QuotationStatus.REJECTED.name());
        long decisionsRevision = totals.count(ACT_DECISION, REVISION_REQUESTED);
        long decisions = decisionsApproved + decisionsRejected + decisionsRevision;

        long replies = totals.count(ACT_REPLY);
        long repliesAccepted = totals.count(ACT_REPLY, QuotationStatus.ACCEPTED.name());
        long repliesTimed = totals.count(ACT_REPLY_TIMED, TIMED);
        long approvalsStamped = totals.count(ACT_APPROVED_STAMP, APPROVED);
        long cohortSent = totals.count(COHORT_SEND, SENT);

        return QuotationOutcomeReportResponse.builder()
                .dateFrom(from)
                .dateTo(to)
                .timezone(reportRangeFactory.zone().getId())
                .total(total)
                .revisedAway(cohort.count(QuotationOutcome.SUPERSEDED))
                .won(won)
                .lost(lost)
                .abandoned(abandoned)
                .stillOpen(stillOpen)
                .wonValue(cohort.money(QuotationOutcome.WON))
                .lostValue(cohort.money(QuotationOutcome.LOST))
                .abandonedValue(cohort.money(QuotationOutcome.ABANDONED))
                .openValue(cohort.money(QuotationOutcome.OPEN))
                .winRate(rateOrNull(won, won + lost))
                .cohortConverted(cohortConverted)
                .conversionRate(ReportingUtils.calculateRate(cohortConverted, total))
                .cohortApproved(totals.count(COHORT_APPROVAL, APPROVED))
                .cohortNeverApproved(totals.count(COHORT_APPROVAL, NEVER_APPROVED))
                .cohortSent(cohortSent)
                .cohortNeverSent(totals.count(COHORT_SEND, NEVER_SENT))
                .avgHoursToSend(totals.average(COHORT_SEND, SENT))
                .expiredNoReply(totals.count(COHORT_EXPIRY, EXPIRED_NO_REPLY))
                .expiredAfterReply(totals.count(COHORT_EXPIRY, EXPIRED_AFTER_REPLY))
                .expiredNeverSent(totals.count(COHORT_EXPIRY, EXPIRED_NEVER_SENT))
                .discountBands(totals.rows(COHORT_DISCOUNT, DISCOUNT_ORDER))
                .byStatus(buildStatusRows(cohort))
                .decisions(decisions)
                .decisionsApproved(decisionsApproved)
                .decisionsRejected(decisionsRejected)
                .decisionsRevisionRequested(decisionsRevision)
                .firstPassApprovalRate(rateOrNull(decisionsApproved, decisions))
                .approvalsStamped(approvalsStamped)
                .avgHoursToApprove(totals.average(ACT_APPROVED_STAMP, APPROVED))
                .replies(replies)
                .repliesAccepted(repliesAccepted)
                .repliesRejected(totals.count(ACT_REPLY, QuotationStatus.REJECTED.name()))
                .repliesInterested(totals.count(ACT_REPLY, QuotationStatus.INTERESTED.name()))
                .repliesNeedRevision(totals.count(ACT_REPLY, "NEED_REVISION"))
                .replyAcceptanceRate(rateOrNull(repliesAccepted, replies))
                .avgHoursToReply(totals.average(ACT_REPLY_TIMED, TIMED))
                .repliesTimed(repliesTimed)
                .sentInPeriod(totals.count(ACT_SENT, SENT))
                .convertedInPeriod(totals.count(ACT_CONVERTED))
                .convertedValue(totals.money(ACT_CONVERTED, "CONVERTED"))
                .closedInPeriod(totals.count(ACT_CLOSURE, QuotationStatus.CLOSED.name()))
                .expiredInPeriod(totals.count(ACT_CLOSURE, QuotationStatus.EXPIRED.name()))
                .lostReasons(sortedByCount(totals.rows(ACT_LOST_REASON, null)))
                .dataGaps(dataGaps(totals, total, cohortSent, replies, repliesTimed,
                        decisions, approvalsStamped,
                        totals.count(ACT_REPLY, QuotationStatus.REJECTED.name()), abandoned))
                .staff(buildStaff(byUser))
                .staffTruncated(namedPreparers(byUser) > MAX_STAFF)
                .build();
    }

    /** Folds one result row into a single preparer's running totals. */
    private static void fold(Acc acc, String metric, String bucket, long count, BigDecimal value) {
        switch (metric) {
            case COHORT_FACT -> {
                QuotationOutcome outcome = QuotationOutcomeClassifier.classifyBucket(bucket);
                if (!outcome.isLive()) {
                    return; // a replaced revision is not this preparer's separate opportunity
                }
                acc.prepared += count;
                switch (outcome) {
                    case WON -> {
                        acc.won += count;
                        acc.wonValue = acc.wonValue.add(value);
                    }
                    case LOST -> acc.lost += count;
                    case ABANDONED -> acc.abandoned += count;
                    default -> { /* still open — not yet anyone's win or loss */ }
                }
            }
            case COHORT_SEND -> {
                if (SENT.equals(bucket)) {
                    acc.sent += count;
                    // Averages arrive per group, so they are re-weighted by group size before pooling.
                    acc.sendHoursWeighted += value.doubleValue() * count;
                    acc.sendSamples += count;
                }
            }
            default -> { /* the activity metrics are headline-only */ }
        }
    }

    /** The cohort's status breakdown, in enum order so a chart never reorders between periods. */
    private List<StatusRow> buildStatusRows(Cohort cohort) {
        List<StatusRow> rows = new ArrayList<>();
        for (QuotationStatus status : QuotationStatus.values()) {
            long count = cohort.status(status);
            if (count > 0) {
                rows.add(StatusRow.builder()
                        .status(status.name())
                        .label(label(status))
                        .count(count)
                        .build());
            }
        }
        return rows;
    }

    /**
     * The per-preparer breakdown, sorted by win rate.
     *
     * <p>Ranking on volume would reward whoever wrote the most documents, which measures activity
     * rather than outcome; preparers with nothing decided sort last instead of at either extreme.
     */
    private List<StaffRow> buildStaff(Map<UUID, Acc> byUser) {
        List<StaffRow> named = new ArrayList<>();
        StaffRow unattributed = null;

        for (Map.Entry<UUID, Acc> entry : byUser.entrySet()) {
            Acc acc = entry.getValue();
            if (acc.prepared == 0) {
                continue;
            }
            boolean isUnattributed = entry.getKey() == null;
            StaffRow row = StaffRow.builder()
                    .name(isUnattributed ? UNATTRIBUTED_LABEL : acc.name)
                    .prepared(acc.prepared)
                    .won(acc.won)
                    .lost(acc.lost)
                    .abandoned(acc.abandoned)
                    .winRate(rateOrNull(acc.won, acc.won + acc.lost))
                    .wonValue(acc.wonValue)
                    .sent(acc.sent)
                    .avgHoursToSend(acc.sendSamples == 0 ? null
                            : ReportingUtils.round2(acc.sendHoursWeighted / acc.sendSamples))
                    .unattributed(isUnattributed)
                    .build();
            if (isUnattributed) {
                unattributed = row;
            } else {
                named.add(row);
            }
        }

        named.sort(Comparator
                .comparing(StaffRow::getWinRate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(StaffRow::getPrepared, Comparator.reverseOrder()));
        List<StaffRow> rows = new ArrayList<>(
                named.size() > MAX_STAFF ? named.subList(0, MAX_STAFF) : named);
        // Kept out of the cap: it is one row however many preparers there are, and dropping it
        // would remove work from the table without naming anyone it belonged to.
        if (unattributed != null) {
            rows.add(unattributed);
        }
        return rows;
    }

    /** Preparers with a name attached — the population the {@link #MAX_STAFF} cap applies to. */
    private static long namedPreparers(Map<UUID, Acc> byUser) {
        return byUser.entrySet().stream()
                .filter(e -> e.getKey() != null && e.getValue().prepared > 0)
                .count();
    }

    /**
     * What this period could not establish, in the words a reader needs to discount a figure by.
     *
     * <p>Every one of these is a place where the report would otherwise publish a confident number
     * over a handful of rows, or a zero that means "not recorded" rather than "did not happen".
     */
    private static List<String> dataGaps(Tally totals, long total, long cohortSent, long replies,
                                         long repliesTimed, long decisions, long approvalsStamped,
                                         long repliesRejected, long abandoned) {
        List<String> gaps = new ArrayList<>();

        if (total == 0) {
            gaps.add("No quotations were written in this period, so the cohort figures are empty "
                    + "rather than zero.");
        }
        if (total > 0 && cohortSent == 0) {
            gaps.add("None of this period's quotations records a dispatch time, so the time to send "
                    + "cannot be established.");
        }
        if (replies > 0 && repliesTimed < replies) {
            gaps.add("Reply speed covers " + repliesTimed + " of " + replies + " replies — the rest "
                    + "belong to quotations with no recorded dispatch time.");
        }
        if (approvalsStamped > 0 && decisions == 0) {
            gaps.add("No approval decisions were logged in this period, so the first-pass approval "
                    + "rate is unavailable even though " + approvalsStamped
                    + " approval(s) were stamped.");
        } else if (approvalsStamped > 0 && decisions < approvalsStamped) {
            gaps.add("The approval log holds " + decisions + " decision(s) against " + approvalsStamped
                    + " approval timestamp(s); the first-pass rate covers only the logged ones.");
        }
        // Both sides of this comparison are activity-scoped. Measuring recorded reasons against the
        // cohort's losses would divide replies logged in this period by quotations written in it —
        // two different populations, so the ratio could exceed 100% or hide a total blank.
        long reasonsGiven = totals.count(ACT_LOST_REASON);
        if (repliesRejected > 0 && reasonsGiven < repliesRejected) {
            gaps.add("A reason was recorded for " + reasonsGiven + " of " + repliesRejected
                    + " rejection(s) replied in this period, so the reason breakdown is not "
                    + "representative.");
        }
        if (abandoned > 0) {
            gaps.add(abandoned + " quotation(s) ended without ever being sent — rejected at approval "
                    + "or expired as a draft. They are outside the win rate because no customer saw "
                    + "them.");
        }
        return gaps;
    }

    private static List<CountRow> sortedByCount(List<CountRow> rows) {
        List<CountRow> sorted = new ArrayList<>(rows);
        sorted.sort(Comparator.comparingLong(CountRow::getCount).reversed());
        return sorted;
    }

    /** A rate, or null when the denominator holds nothing that could establish one. */
    private static Double rateOrNull(long part, long whole) {
        return whole <= 0 ? null : ReportingUtils.calculateRate(part, whole);
    }

    private String label(QuotationStatus status) {
        return switch (status) {
            case DRAFT -> "Draft";
            case PENDING_APPROVAL -> "Pending approval";
            case SENT -> "Sent";
            case APPROVED -> "Approved (awaiting dispatch)";
            case REJECTED -> "Rejected";
            case EXPIRED -> "Expired";
            case CLOSED -> "Closed";
            case CONVERTED -> "Converted";
            case PENDING_REVISION -> "Pending revision";
            case ACCEPTED -> "Accepted";
            case INTERESTED -> "Interested";
            case SUPERSEDED -> "Superseded (older version)";
        };
    }

    // ── Plumbing ──────────────────────────────────────────────────────────────

    private static Acc acc(Map<UUID, Acc> byUser, Object ownerId, Object ownerName) {
        UUID key = toUuid(ownerId);
        Acc acc = byUser.computeIfAbsent(key, id -> new Acc());
        if (acc.name == null) {
            acc.name = (ownerName instanceof String name && !name.isBlank())
                    ? name
                    : (key == null ? UNATTRIBUTED_LABEL : key.toString());
        }
        return acc;
    }

    private static String str(Object value) {
        return value == null ? "" : value.toString();
    }

    private static UUID toUuid(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return UUID.fromString(text);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    /** Headline figures, summed across preparers, keyed by metric and bucket. */
    private static final class Tally {
        private final Map<String, Long> counts = new LinkedHashMap<>();
        private final Map<String, BigDecimal> money = new LinkedHashMap<>();
        private final Map<String, Double> weighted = new LinkedHashMap<>();

        void add(String metric, String bucket, long count, BigDecimal value) {
            String key = key(metric, bucket);
            counts.merge(key, count, Long::sum);
            money.merge(key, value, BigDecimal::add);
            // Group averages are re-weighted by group size so pooling them stays correct.
            weighted.merge(key, value.doubleValue() * count, Double::sum);
        }

        long count(String metric) {
            String prefix = metric + "|";
            return counts.entrySet().stream()
                    .filter(e -> e.getKey().startsWith(prefix))
                    .mapToLong(Map.Entry::getValue)
                    .sum();
        }

        long count(String metric, String bucket) {
            return counts.getOrDefault(key(metric, bucket), 0L);
        }

        /** Every bucket of a metric with its count — for metrics whose bucket carries facts. */
        Map<String, Long> buckets(String metric) {
            return slice(counts, metric);
        }

        /** The same slice over the money column. */
        Map<String, BigDecimal> moneyByBucket(String metric) {
            return slice(money, metric);
        }

        private static <V> Map<String, V> slice(Map<String, V> source, String metric) {
            String prefix = metric + "|";
            Map<String, V> found = new LinkedHashMap<>();
            source.forEach((k, v) -> {
                if (k.startsWith(prefix)) {
                    found.put(k.substring(prefix.length()), v);
                }
            });
            return found;
        }

        BigDecimal money(String metric, String bucket) {
            return money.getOrDefault(key(metric, bucket), BigDecimal.ZERO);
        }

        /** The pooled average of one bucket, or null when the bucket holds no rows. */
        Double average(String metric, String bucket) {
            String key = key(metric, bucket);
            long n = counts.getOrDefault(key, 0L);
            return n == 0 ? null : ReportingUtils.round2(weighted.getOrDefault(key, 0.0) / n);
        }

        /** The buckets of one metric as display rows, in a fixed order where one is given. */
        List<CountRow> rows(String metric, List<String> order) {
            String prefix = metric + "|";
            Map<String, Long> found = new LinkedHashMap<>();
            counts.forEach((k, v) -> {
                if (k.startsWith(prefix)) {
                    found.put(k.substring(prefix.length()), v);
                }
            });
            List<String> keys = new ArrayList<>();
            if (order != null) {
                order.forEach(k -> {
                    if (found.containsKey(k)) {
                        keys.add(k);
                    }
                });
            }
            found.keySet().forEach(k -> {
                if (!keys.contains(k)) {
                    keys.add(k);
                }
            });
            List<CountRow> rows = new ArrayList<>();
            for (String k : keys) {
                rows.add(CountRow.builder()
                        .key(k)
                        .label(LABELS.getOrDefault(k, k))
                        .count(found.getOrDefault(k, 0L))
                        .value(money(metric, k))
                        .build());
            }
            return rows;
        }

        private static String key(String metric, String bucket) {
            return metric + "|" + bucket;
        }
    }

    /**
     * The period's quotations, classified once and read many times.
     *
     * <p>Every figure the cohort section publishes comes from this one pass, so the status
     * breakdown, the outcome split and the excluded count cannot drift apart — they are three views
     * of the same classification rather than three queries that have to agree.
     */
    private static final class Cohort {
        private final Map<QuotationOutcome, Long> byOutcome = new LinkedHashMap<>();
        private final Map<QuotationOutcome, BigDecimal> valueByOutcome = new LinkedHashMap<>();
        private final Map<QuotationStatus, Long> byStatus = new LinkedHashMap<>();
        /** Quotations that are an opportunity of their own — replaced revisions excluded. */
        private long live;

        Cohort(Map<String, Long> counts, Map<String, BigDecimal> money) {
            counts.forEach((bucket, count) -> {
                QuotationOutcome outcome = QuotationOutcomeClassifier.classifyBucket(bucket);
                byOutcome.merge(outcome, count, Long::sum);
                valueByOutcome.merge(outcome, money.getOrDefault(bucket, BigDecimal.ZERO),
                        BigDecimal::add);
                if (!outcome.isLive()) {
                    return;
                }
                live += count;
                QuotationStatus status = QuotationOutcomeClassifier.statusOf(bucket);
                if (status != null) {
                    byStatus.merge(status, count, Long::sum);
                }
            });
        }

        long count(QuotationOutcome outcome) {
            return byOutcome.getOrDefault(outcome, 0L);
        }

        BigDecimal money(QuotationOutcome outcome) {
            return valueByOutcome.getOrDefault(outcome, BigDecimal.ZERO);
        }

        /** Live quotations at a status. Replaced rows are out, so this sums to {@link #live}. */
        long status(QuotationStatus status) {
            return byStatus.getOrDefault(status, 0L);
        }
    }

    /** One preparer's running totals while the result rows are folded together. */
    private static final class Acc {
        String name;
        long prepared;
        long won;
        long lost;
        long abandoned;
        long sent;
        BigDecimal wonValue = BigDecimal.ZERO;
        double sendHoursWeighted;
        long sendSamples;
    }
}
