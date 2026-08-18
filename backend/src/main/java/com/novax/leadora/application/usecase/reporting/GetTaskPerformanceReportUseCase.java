package com.novax.leadora.application.usecase.reporting;

import com.novax.leadora.api.dto.response.TaskPerformanceReportResponse;
import com.novax.leadora.api.dto.response.TaskPerformanceReportResponse.CountRow;
import com.novax.leadora.api.dto.response.TaskPerformanceReportResponse.StaffRow;
import com.novax.leadora.application.usecase.sla.SlaOutcome;
import com.novax.leadora.application.usecase.sla.SlaOutcomeClassifier;
import com.novax.leadora.common.util.ReportRange;
import com.novax.leadora.common.util.ReportRangeFactory;
import com.novax.leadora.common.util.ReportingUtils;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.SlaStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.TaskPriority;
import com.novax.leadora.infrastructure.persistence.entity.enums.TaskStatus;
import com.novax.leadora.infrastructure.persistence.repository.TaskPerformanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * UC-23.2 — View Follow-up Task Performance Report.
 *
 * <p>Scope follows the caller: Sales Manager / Admin see the whole team, everyone else sees only
 * the tasks assigned to them. The scope is applied in SQL, so it cannot be bypassed by a caller
 * that forgets to filter afterwards.
 *
 * <p>Two round trips: the consolidated aggregate, and the raw SLA rows that
 * {@link SlaOutcomeClassifier} classifies — the same code UC-23.3 uses, so the two reports can
 * never quote different compliance figures for the same period.
 *
 * <h2>Three questions, three answers</h2>
 *
 * <p>The report used to publish a single {@code overdue} count that meant "still open and past its
 * deadline". Read as punctuality — which is how a figure called overdue is read — it was wrong in a
 * specific and flattering direction: a task finished three days late left the OPEN status and
 * stopped being counted, so a period in which every task was late scored 0%, and the number
 * improved as the backlog was cleared rather than as the team became punctual.
 *
 * <p>It is now three separate figures: work <b>raised</b> (created_at), work <b>resolved</b> and
 * whether it beat its deadline (completed_at), and the <b>open queue</b> — which of this period's
 * tasks are still running late, evaluated against the clock. The queue figure stays scoped to the
 * raised cohort so its rate has a denominator it belongs to.
 */
@Service
@RequiredArgsConstructor
public class GetTaskPerformanceReportUseCase {

    private static final int MAX_STAFF = 50;
    /** Roles that may see team-wide task performance; everyone else is scoped to their own tasks. */
    private static final Set<String> FULL_SCOPE_ROLES = Set.of("MANAGER", "ADMIN");
    private static final String UNASSIGNED_LABEL = "(Unassigned)";

    // Discriminators emitted by the consolidated query.
    private static final String RAISED = "RAISED";
    private static final String RAISED_PRIORITY = "RAISED_PRIORITY";
    private static final String RAISED_ACTIVITY = "RAISED_ACTIVITY";
    private static final String RAISED_LINKAGE = "RAISED_LINKAGE";
    private static final String RAISED_COMPLETED_UNDATED = "RAISED_COMPLETED_UNDATED";
    private static final String RESOLVED = "RESOLVED";
    private static final String OPEN_OVERDUE = "OPEN_OVERDUE";
    private static final String OPEN_OVERDUE_PRIORITY = "OPEN_OVERDUE_PRIORITY";

    private static final String ON_TIME = "ON_TIME";
    private static final String LATE = "LATE";
    private static final String UNDATED = "UNDATED";
    private static final String ORPHAN = "ORPHAN";

    /** Display names for the buckets the query emits. */
    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("CALL", "Call"),
            Map.entry("EMAIL", "Email"),
            Map.entry("MEETING", "Meeting"),
            Map.entry("SITE_VISIT", "Site visit"),
            Map.entry("FOLLOW_UP", "Follow-up"),
            Map.entry("TASK", "General task"),
            Map.entry("UNSPECIFIED", "Unspecified"),
            Map.entry("D1_3", "1–3 days late"),
            Map.entry("D4_7", "4–7 days late"),
            Map.entry("D8_PLUS", "8+ days late"),
            Map.entry("LOW", "Low"),
            Map.entry("MEDIUM", "Medium"),
            Map.entry("HIGH", "High"));

    /** The order aging bands are shown in, so a chart never reorders itself between periods. */
    private static final List<String> AGING_ORDER = List.of("D1_3", "D4_7", "D8_PLUS");
    private static final List<String> PRIORITY_ORDER =
            List.of(TaskPriority.HIGH.name(), TaskPriority.MEDIUM.name(), TaskPriority.LOW.name());

    private final TaskPerformanceRepository taskPerformanceRepository;
    private final ReportRangeFactory reportRangeFactory;

    /**
     * The cache key uses {@link #scopeKey(UserEntity)} rather than the raw user id: every team-wide
     * viewer sees identical figures, so they share one entry instead of one per manager, while every
     * scoped user keeps a private one. Deriving the key and the SQL filter from the same rule is
     * what stops a drift between them from serving one person's tasks to another.
     *
     * @param actor the authenticated caller — determines whether the report is team-wide or own-only
     */
    @Cacheable(value = "task-performance-report",
            key = "T(com.novax.leadora.application.usecase.reporting.GetTaskPerformanceReportUseCase)"
                    + ".scopeKey(#actor) + '_' + #from + '_' + #to",
            unless = "#result == null")
    @Transactional(readOnly = true)
    public TaskPerformanceReportResponse execute(UserEntity actor, LocalDate from, LocalDate to) {
        ReportRange range = reportRangeFactory.resolve(from, to);
        OffsetDateTime now = OffsetDateTime.now();

        boolean teamWide = canSeeAllTasks(actor);
        UUID scopedUserId = teamWide ? null : (actor == null ? null : actor.getUserId());
        String scopedParam = scopedUserId == null ? null : scopedUserId.toString();

        Totals totals = new Totals();
        Map<UUID, Acc> byUser = new LinkedHashMap<>();

        for (Object[] row : taskPerformanceRepository.taskPerformanceAggregates(
                scopedParam, range.start(), range.endExclusive(), now)) {
            String metric = str(row[0]);
            String bucket = str(row[1]);
            long count = ReportingUtils.toLong(row[4]);
            double value = toDouble(row[5]);

            totals.add(metric, bucket, count, value);
            fold(acc(byUser, row[2], row[3]), metric, bucket, count, value);
        }
        readSla(scopedParam, range, now, totals, byUser);

        long total = totals.count(RAISED);
        long completedFromCohort = totals.count(RAISED, TaskStatus.COMPLETED.name());
        long open = totals.count(RAISED, TaskStatus.OPEN.name());
        long orphan = totals.count(RAISED_LINKAGE, ORPHAN);

        long resolvedOnTime = totals.count(RESOLVED, ON_TIME);
        long resolvedLate = totals.count(RESOLVED, LATE);
        long resolvedNoDeadline = totals.count(RESOLVED, UNDATED);
        long undated = totals.count(RAISED_COMPLETED_UNDATED, UNDATED);

        long openOverdue = totals.count(OPEN_OVERDUE);

        return TaskPerformanceReportResponse.builder()
                .dateFrom(from)
                .dateTo(to)
                .timezone(reportRangeFactory.zone().getId())
                .totalTasks(total)
                .completed(completedFromCohort)
                .open(open)
                .cancelled(totals.count(RAISED, TaskStatus.CANCELLED.name()))
                .completionRate(ReportingUtils.calculateRate(completedFromCohort, total))
                .priorityLow(totals.count(RAISED_PRIORITY, TaskPriority.LOW.name()))
                .priorityMedium(totals.count(RAISED_PRIORITY, TaskPriority.MEDIUM.name()))
                .priorityHigh(totals.count(RAISED_PRIORITY, TaskPriority.HIGH.name()))
                .activityMix(totals.rows(RAISED_ACTIVITY, null))
                .orphanTasks(orphan)
                .orphanRate(ReportingUtils.calculateRate(orphan, total))
                .resolvedTotal(resolvedOnTime + resolvedLate + resolvedNoDeadline)
                .resolvedOnTime(resolvedOnTime)
                .resolvedLate(resolvedLate)
                .resolvedNoDeadline(resolvedNoDeadline)
                // Denominator is the tasks whose punctuality can actually be established: one
                // without a deadline is not evidence either way, and counting it as on time is the
                // same flattering bias this report was rewritten to remove.
                .punctualityRate(resolvedOnTime + resolvedLate == 0 ? null
                        : ReportingUtils.calculateRate(resolvedOnTime, resolvedOnTime + resolvedLate))
                .avgCycleHours(totals.average(RESOLVED))
                .completedUndated(undated)
                .punctualityCoverage(ReportingUtils.calculateRate(
                        completedFromCohort - undated, completedFromCohort))
                .openOverdue(openOverdue)
                .overdueRate(ReportingUtils.calculateRate(openOverdue, open))
                .avgDaysOverdue(totals.average(OPEN_OVERDUE))
                .overdueAging(totals.rows(OPEN_OVERDUE, AGING_ORDER))
                .overdueByPriority(totals.rows(OPEN_OVERDUE_PRIORITY, PRIORITY_ORDER))
                .slaDecided(totals.slaDecided)
                .slaOnTime(totals.slaOnTime)
                .slaComplianceRate(ReportingUtils.calculateRate(totals.slaOnTime, totals.slaDecided))
                .ownScope(!teamWide)
                .staff(buildStaff(byUser))
                .build();
    }

    /** Folds one result row into a single staff member's running totals. */
    private static void fold(Acc acc, String metric, String bucket, long count, double value) {
        switch (metric) {
            case RAISED -> {
                acc.total += count;
                if (TaskStatus.COMPLETED.name().equals(bucket)) {
                    acc.completed += count;
                }
            }
            case RESOLVED -> {
                if (ON_TIME.equals(bucket)) {
                    acc.resolvedOnTime += count;
                } else if (LATE.equals(bucket)) {
                    acc.resolvedLate += count;
                }
                acc.cycleHoursWeighted += value * count;
                acc.cycleSamples += count;
            }
            case OPEN_OVERDUE -> acc.openOverdue += count;
            default -> { /* the other kinds are headline-only */ }
        }
    }

    /**
     * SLA rows are classified here with {@link SlaOutcomeClassifier} rather than in SQL, so "was
     * this on time" is defined once for the whole application.
     */
    private void readSla(String scopedParam, ReportRange range, OffsetDateTime now,
                         Totals totals, Map<UUID, Acc> byUser) {
        for (Object[] row : taskPerformanceRepository.taskSlaRows(
                scopedParam, range.start(), range.endExclusive())) {
            SlaOutcome outcome = SlaOutcomeClassifier.classify(
                    slaStatus(row[2]), offsetDateTime(row[3]), offsetDateTime(row[4]),
                    offsetDateTime(row[5]), now);
            if (!outcome.isDecided()) {
                continue;
            }
            totals.slaDecided++;
            if (outcome == SlaOutcome.RESOLVED_ON_TIME) {
                totals.slaOnTime++;
            }
            if (row[0] != null) {
                acc(byUser, row[0], row[1]);
            }
        }
    }

    /**
     * The per-staff breakdown.
     *
     * <p>Sorted by punctuality rather than by volume. Ranking on task count rewards whoever raised
     * the most follow-ups, which is a measure of how work was recorded rather than of how it went;
     * staff with nothing datable sort last instead of appearing at either extreme.
     */
    private List<StaffRow> buildStaff(Map<UUID, Acc> byUser) {
        List<StaffRow> named = new ArrayList<>();
        StaffRow unassigned = null;

        for (Map.Entry<UUID, Acc> entry : byUser.entrySet()) {
            Acc acc = entry.getValue();
            if (!acc.hasAnything()) {
                continue;
            }
            boolean isUnassigned = entry.getKey() == null;
            long judged = acc.resolvedOnTime + acc.resolvedLate;
            StaffRow row = StaffRow.builder()
                    .name(isUnassigned ? UNASSIGNED_LABEL : acc.name)
                    .total(acc.total)
                    .completed(acc.completed)
                    .completionRate(ReportingUtils.calculateRate(acc.completed, acc.total))
                    .openOverdue(acc.openOverdue)
                    .resolvedLate(acc.resolvedLate)
                    .resolvedOnTime(acc.resolvedOnTime)
                    .punctualityRate(judged == 0 ? null
                            : ReportingUtils.calculateRate(acc.resolvedOnTime, judged))
                    .avgCycleHours(acc.cycleSamples == 0 ? null
                            : ReportingUtils.round2(acc.cycleHoursWeighted / acc.cycleSamples))
                    .unassigned(isUnassigned)
                    .build();
            if (isUnassigned) {
                unassigned = row;
            } else {
                named.add(row);
            }
        }

        named.sort(Comparator
                .comparing((StaffRow r) -> r.getPunctualityRate(), Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing((StaffRow r) -> r.getTotal(), Comparator.reverseOrder()));
        List<StaffRow> rows = new ArrayList<>(
                named.size() > MAX_STAFF ? named.subList(0, MAX_STAFF) : named);
        // Kept out of the cap so the breakdown always reconciles with the headline total.
        if (unassigned != null) {
            rows.add(unassigned);
        }
        return rows;
    }

    /**
     * The cache partition for a caller: one shared entry for every team-wide viewer, one private
     * entry per scoped user. Public and static because the {@code @Cacheable} SpEL expression calls
     * it — that is what keeps the key and the query filter derived from the same rule.
     */
    public static String scopeKey(UserEntity user) {
        return canSeeAllTasks(user) ? "all" : String.valueOf(user == null ? "anonymous" : user.getUserId());
    }

    private static boolean canSeeAllTasks(UserEntity user) {
        String role = (user != null && user.getRole() != null && user.getRole().getRoleName() != null)
                ? user.getRole().getRoleName().trim().toUpperCase()
                : "";
        return FULL_SCOPE_ROLES.contains(role);
    }

    // ── Plumbing ──────────────────────────────────────────────────────────────

    private static Acc acc(Map<UUID, Acc> byUser, Object ownerId, Object ownerName) {
        UUID key = toUuid(ownerId);
        Acc acc = byUser.computeIfAbsent(key, id -> new Acc());
        if (acc.name == null) {
            acc.name = (ownerName instanceof String name && !name.isBlank())
                    ? name
                    : (key == null ? UNASSIGNED_LABEL : key.toString());
        }
        return acc;
    }

    private static String str(Object value) {
        return value == null ? "" : value.toString();
    }

    private static double toDouble(Object value) {
        return (value instanceof Number number) ? number.doubleValue() : 0.0;
    }

    private static SlaStatus slaStatus(Object value) {
        if (value instanceof SlaStatus status) {
            return status;
        }
        try {
            return value == null ? null : SlaStatus.valueOf(value.toString());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /** A native query hands timestamps back as whatever the driver mapped timestamptz to. */
    private static OffsetDateTime offsetDateTime(Object value) {
        if (value instanceof OffsetDateTime odt) {
            return odt;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant().atOffset(OffsetDateTime.now().getOffset());
        }
        if (value instanceof java.time.Instant instant) {
            return instant.atOffset(OffsetDateTime.now().getOffset());
        }
        return null;
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

    /** Headline counts, summed across owners, keyed by metric and bucket. */
    private static final class Totals {
        private final Map<String, Long> counts = new LinkedHashMap<>();
        private final Map<String, Double> weighted = new LinkedHashMap<>();
        private final Map<String, Long> samples = new LinkedHashMap<>();
        long slaDecided;
        long slaOnTime;

        void add(String metric, String bucket, long count, double value) {
            counts.merge(key(metric, bucket), count, (a, b) -> a + b);
            // Averages arrive per group, so they are re-weighted by group size before being pooled.
            weighted.merge(metric, value * count, (a, b) -> a + b);
            samples.merge(metric, count, (a, b) -> a + b);
        }

        long count(String metric) {
            String prefix = metric + "|";
            return counts.entrySet().stream()
                    .filter(e -> e.getKey().startsWith(prefix))
                    .mapToLong(e -> e.getValue())
                    .sum();
        }

        long count(String metric, String bucket) {
            return counts.getOrDefault(key(metric, bucket), 0L);
        }

        double average(String metric) {
            long n = samples.getOrDefault(metric, 0L);
            return n == 0 ? 0.0 : ReportingUtils.round2(weighted.getOrDefault(metric, 0.0) / n);
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
                found.keySet().forEach(k -> {
                    if (!keys.contains(k)) {
                        keys.add(k);
                    }
                });
            } else {
                keys.addAll(found.keySet());
            }
            List<CountRow> rows = new ArrayList<>();
            for (String k : keys) {
                rows.add(CountRow.builder()
                        .key(k)
                        .label(LABELS.getOrDefault(k, k))
                        .count(found.getOrDefault(k, 0L))
                        .build());
            }
            return rows;
        }

        private static String key(String metric, String bucket) {
            return metric + "|" + bucket;
        }
    }

    /** One staff member's running totals while the result rows are folded together. */
    private static final class Acc {
        String name;
        long total;
        long completed;
        long openOverdue;
        long resolvedOnTime;
        long resolvedLate;
        double cycleHoursWeighted;
        long cycleSamples;

        boolean hasAnything() {
            return total > 0 || openOverdue > 0 || resolvedOnTime > 0 || resolvedLate > 0;
        }
    }
}
