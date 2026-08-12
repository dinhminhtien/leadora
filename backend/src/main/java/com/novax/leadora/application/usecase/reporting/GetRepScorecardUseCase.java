package com.novax.leadora.application.usecase.reporting;

import com.novax.leadora.api.dto.response.RepScorecardResponse;
import com.novax.leadora.api.dto.response.RepScorecardResponse.LostReason;
import com.novax.leadora.api.dto.response.RepScorecardResponse.RepMetrics;
import com.novax.leadora.api.dto.response.RepScorecardResponse.RepScorecard;
import com.novax.leadora.api.dto.response.RepScorecardResponse.RepScore;
import com.novax.leadora.api.dto.response.RepScorecardResponse.TeamBaseline;
import com.novax.leadora.application.usecase.reporting.scoring.ScoringEngine;
import com.novax.leadora.application.usecase.reporting.scoring.ScoringProperties;
import com.novax.leadora.application.usecase.sla.SlaOutcome;
import com.novax.leadora.application.usecase.sla.SlaOutcomeClassifier;
import com.novax.leadora.common.util.ReportRange;
import com.novax.leadora.common.util.ReportRangeFactory;
import com.novax.leadora.common.util.ReportingUtils;
import com.novax.leadora.infrastructure.persistence.entity.enums.DealStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.SlaStatus;
import com.novax.leadora.infrastructure.persistence.repository.DealRepository;
import com.novax.leadora.infrastructure.persistence.repository.RepScorecardRepository;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * UC-23.6 — Rep Performance Scorecard.
 *
 * <p>Five round trips: the Sales Performance statement (reused verbatim, so outcome and efficiency
 * cannot disagree with UC-23.1), plus velocity, discipline, quality and the raw SLA rows.
 *
 * <h2>Decisions worth knowing about before reading a score</h2>
 *
 * <p><b>The period is never open-ended.</b> Every other UC-23 report defaults to all of history;
 * here that would be meaningless — a revenue target scaled across "1970 to 2100" is not a target,
 * and a rep who left in March would be compared with one who joined in July. An unbounded request
 * is narrowed to the last 30 days.
 *
 * <p><b>Unassigned records are excluded.</b> They appear in UC-23.1 as an explicit
 * "(Unassigned)" row so the totals reconcile; a scorecard cannot rank a person who does not exist,
 * so those records are simply absent here. The consequence is real and worth stating: the scorecard
 * columns sum to the report's totals only after the unassigned row is subtracted.
 *
 * <p><b>The score is not the deliverable.</b> Raw counts, the team baseline, the weights used, a
 * confidence flag and an explicit list of what could not be measured all travel with it, because a
 * number out of 100 about a person's work is exactly the kind of output that gets quoted without
 * its caveats.
 */
@Service
@RequiredArgsConstructor
public class GetRepScorecardUseCase {

    /** How far back an unbounded request is narrowed to. */
    private static final int DEFAULT_PERIOD_DAYS = 30;
    private static final int TOP_LOST_REASONS = 3;
    private static final double DAYS_PER_MONTH = 30.0;

    private static final Set<String> ACCEPTED_QUOTATIONS = Set.of(
            QuotationStatus.ACCEPTED.name(), QuotationStatus.CONVERTED.name());

    private final DealRepository dealRepository;
    private final RepScorecardRepository scorecardRepository;
    private final UserRepository userRepository;
    private final ReportRangeFactory reportRangeFactory;
    private final ScoringEngine scoringEngine;
    private final ScoringProperties props;

    /**
     * The key carries today's date because the default window slides. With only the two arguments
     * in it, an unbounded request keys as a constant while meaning "the last 30 days" — so the
     * entry written at 23:58 kept serving yesterday's window after midnight.
     */
    @Cacheable(value = "rep-scorecard",
            key = "#dateFrom + '_' + #dateTo + '_' + T(java.time.LocalDate).now()",
            unless = "#result == null")
    @Transactional(readOnly = true)
    public RepScorecardResponse execute(LocalDate dateFrom, LocalDate dateTo) {
        // Each end falls back on its own. Replacing both whenever either was missing meant a manager
        // who filled in only "from" silently got the last 30 days here while every other tab honoured
        // their start date — two reports on one screen describing different periods.
        LocalDate to = (dateTo != null) ? dateTo : LocalDate.now(reportRangeFactory.zone());
        LocalDate from = (dateFrom != null) ? dateFrom : to.minusDays(DEFAULT_PERIOD_DAYS - 1L);

        ReportRange range = reportRangeFactory.resolve(from, to);
        double months = (ChronoUnit.DAYS.between(from, to) + 1) / DAYS_PER_MONTH;

        Map<UUID, Acc> byUser = new LinkedHashMap<>();
        readOutcomeAndEfficiency(range, byUser);
        readVelocity(range, byUser);
        readDiscipline(range, byUser);
        readQuality(range, byUser);
        readSla(range, byUser);

        Set<UUID> scoredUsers = scoredUserIds();
        List<Scored> scored = new ArrayList<>();
        for (Map.Entry<UUID, Acc> entry : byUser.entrySet()) {
            if (entry.getKey() == null || !entry.getValue().hasAnything()) {
                continue; // unassigned records, and owners the joins touched but who did nothing
            }
            if (!scoredUsers.contains(entry.getKey())) {
                continue; // not a sales rep — see scoredUserIds()
            }
            scored.add(new Scored(entry.getKey(), entry.getValue()));
        }

        TeamBaseline team = baseline(byUser, scoredUsers, scored.size());
        for (Scored s : scored) {
            s.score = scoringEngine.score(s.metrics, team, months);
        }

        // Sorted before the rows are built, so rank can be a field of an immutable response object
        // rather than something stamped on afterwards.
        scored.sort(Comparator.comparing(
                (Scored s) -> s.score.getTotal(),
                Comparator.nullsLast(Comparator.reverseOrder())));

        // The active-days gate is only meaningful where the signal exists at all. It counts days
        // with an audit entry, and records created outside the application — imports, seeds, direct
        // SQL — leave none, so a whole team can show zero active days while plainly having sold
        // things. Gating on that would rank nobody and quietly turn "we have no attendance data"
        // into "nobody worked": the same missing-is-not-zero mistake the axes and the data gaps
        // exist to avoid. Where at least one rep has the signal, it is real and the gate applies.
        boolean activityTracked = scored.stream().anyMatch(s -> s.metrics.getActiveDays() > 0);

        List<RepScorecard> reps = new ArrayList<>();
        List<Double> totals = new ArrayList<>();
        int rank = 0;
        for (Scored s : scored) {
            totals.add(s.score.getTotal());
            boolean ranked = s.score.getTotal() != null
                    && (!activityTracked || s.metrics.getActiveDays() >= props.getMinActiveDays());
            reps.add(RepScorecard.builder()
                    .userId(s.userId)
                    .name(s.name)
                    .metrics(s.metrics)
                    .score(s.score)
                    .lowConfidence(s.metrics.getSampleSize() < props.getMinSample())
                    .ranked(ranked)
                    .rank(ranked ? ++rank : null)
                    .dataGaps(dataGaps(s.metrics, activityTracked))
                    .topLostReasons(s.lostReasons)
                    .build());
        }

        ScoringProperties.Weights w = props.getWeights();
        return RepScorecardResponse.builder()
                .dateFrom(from)
                .dateTo(to)
                .timezone(reportRangeFactory.zone().getId())
                .periodMonths(ReportingUtils.round2(months))
                .weights(RepScorecardResponse.Weights.builder()
                        .outcome(w.getOutcome()).efficiency(w.getEfficiency()).velocity(w.getVelocity())
                        .discipline(w.getDiscipline()).quality(w.getQuality()).build())
                .team(team.toBuilder().medianScore(ScoringEngine.median(totals)).build())
                .reps(reps)
                .build();
    }

    /**
     * The users this report is allowed to score.
     *
     * <p>Every query behind the scorecard groups by whoever owns a record — and the ACTIVE_DAYS
     * branch groups by whoever wrote an audit entry, which is <em>every user who logs in</em>.
     * Without this filter a receptionist who signed in on twenty days arrived on a sales
     * leaderboard with 0 revenue and 0 deals, scored 0 out of 100 against a quota nobody had ever
     * set for them, inflated {@code repCount}, pulled the median down and skewed the pooled
     * baselines that every thin sample is shrunk toward.
     *
     * <p>Applied in Java rather than as a role join in each of the five statements: one place to be
     * right, and the roles are configurable.
     */
    private Set<UUID> scoredUserIds() {
        Set<UUID> ids = new HashSet<>();
        for (String role : props.getScoredRoles()) {
            userRepository.findByRoleName(role).forEach(user -> ids.add(user.getUserId()));
        }
        return ids;
    }

    // ── Reading ───────────────────────────────────────────────────────────────

    /** UC-23.1's own statement, unfiltered: the scorecard must not invent a second "deals won". */
    private void readOutcomeAndEfficiency(ReportRange range, Map<UUID, Acc> byUser) {
        for (Object[] row : dealRepository.salesPerformanceAggregates(
                range.start(), range.endExclusive(), null, null, null, null, true)) {
            String kind = str(row[0]);
            String bucket = str(row[1]);
            Acc acc = acc(byUser, row[2], row[3]);
            long count = ReportingUtils.toLong(row[4]);
            BigDecimal amount = ReportingUtils.toBigDecimal(row[5]);

            switch (kind) {
                case SalesAggregateKinds.LEAD -> acc.leadsCreated += count;
                case SalesAggregateKinds.LEAD_CONVERTED -> acc.leadsConverted += count;
                case SalesAggregateKinds.LEAD_COHORT_CONVERTED -> acc.cohortConverted += count;
                case SalesAggregateKinds.DEAL_CLOSED -> {
                    if (DealStatus.WON.name().equals(bucket)) {
                        acc.dealsWon += count;
                        acc.wonValue = acc.wonValue.add(amount);
                    } else if (DealStatus.LOST.name().equals(bucket)) {
                        acc.dealsLost += count;
                    }
                }
                case SalesAggregateKinds.QUOTATION -> {
                    // Superseded revisions leave the denominator, matching UC-23.1 and UC-23.5.
                    if (!QuotationStatus.SUPERSEDED.name().equals(bucket)) {
                        acc.quotationsCreated += count;
                    }
                    if (ACCEPTED_QUOTATIONS.contains(bucket)) {
                        acc.quotationsAccepted += count;
                    }
                }
                case SalesAggregateKinds.BOOKING_CONFIRMED -> acc.bookingsConfirmed += count;
                case SalesAggregateKinds.REVENUE -> acc.revenue = acc.revenue.add(amount);
                default -> { /* headline-only kinds carry no per-rep meaning here */ }
            }
        }
    }

    private void readVelocity(ReportRange range, Map<UUID, Acc> byUser) {
        for (Object[] row : scorecardRepository.velocityByOwner(range.start(), range.endExclusive())) {
            Acc acc = acc(byUser, row[1], row[2]);
            long samples = ReportingUtils.toLong(row[3]);
            Double value = nullableDouble(row[4]);
            switch (str(row[0])) {
                case "FIRST_RESPONSE_HOURS" -> {
                    acc.firstResponseHours = value;
                    acc.firstResponseSamples = samples;
                }
                case "LEAD_TO_CONVERSION_DAYS" -> acc.leadToConversionDays = value;
                case "QUOTATION_TURNAROUND_HOURS" -> acc.quotationTurnaroundHours = value;
                case "DEAL_CYCLE_DAYS" -> acc.dealCycleDays = value;
                default -> { /* unknown metric: ignore rather than corrupt a neighbour */ }
            }
        }
    }

    private void readDiscipline(ReportRange range, Map<UUID, Acc> byUser) {
        for (Object[] row : scorecardRepository.disciplineByOwner(
                range.start(), range.endExclusive(),
                reportRangeFactory.zone().getId(), props.getForecastToleranceDays())) {
            Acc acc = acc(byUser, row[1], row[2]);
            long count = ReportingUtils.toLong(row[3]);
            Double value = nullableDouble(row[4]);
            switch (str(row[0])) {
                case "TASKS_TOTAL" -> acc.tasksTotal += count;
                case "TASKS_COMPLETED" -> acc.tasksCompleted += count;
                case "TASKS_OVERDUE" -> acc.tasksOverdue += count;
                case "TASKS_ON_TIME" -> acc.tasksOnTime += count;
                case "TASKS_LATE" -> acc.tasksLate += count;
                case "DISCOUNT_PCT" -> acc.avgDiscountPercent = count > 0 ? value : null;
                case "QUOTATION_ROOTS" -> acc.quotationRoots += count;
                case "QUOTATION_REVISIONS" -> acc.quotationRevisions += count;
                case "COLLECTION_TOTAL" -> acc.collectionTotal += count;
                case "COLLECTION_ONTIME" -> acc.collectionOnTime += count;
                case "FORECAST_TOTAL" -> acc.forecastTotal += count;
                case "FORECAST_HIT" -> acc.forecastHit += count;
                case "ACTIVE_DAYS" -> acc.activeDays += count;
                default -> { /* unknown metric */ }
            }
        }
    }

    private void readQuality(ReportRange range, Map<UUID, Acc> byUser) {
        for (Object[] row : scorecardRepository.qualityByOwner(range.start(), range.endExclusive())) {
            Acc acc = acc(byUser, row[1], row[2]);
            String label = row[3] == null ? null : row[3].toString();
            long count = ReportingUtils.toLong(row[4]);
            Double value = nullableDouble(row[5]);
            switch (str(row[0])) {
                case "CSAT" -> {
                    acc.csat = count > 0 ? value : null;
                    acc.csatSamples = count;
                }
                case "CSAT_ATTITUDE" -> acc.csatAttitude = count > 0 ? value : null;
                case "CSAT_SPEED" -> acc.csatSpeed = count > 0 ? value : null;
                case "CSAT_ACCURACY" -> acc.csatAccuracy = count > 0 ? value : null;
                case "LOST_REASON" -> {
                    if (label != null) {
                        acc.lostReasons.merge(label, count, Long::sum);
                    }
                }
                default -> { /* unknown metric */ }
            }
        }
    }

    /**
     * SLA rows are classified here with {@link SlaOutcomeClassifier}, the same code UC-23.3 uses,
     * so the two reports can never quote different compliance rates for the same period.
     */
    private void readSla(ReportRange range, Map<UUID, Acc> byUser) {
        OffsetDateTime now = OffsetDateTime.now();
        for (Object[] row : scorecardRepository.slaRowsByOwner(range.start(), range.endExclusive())) {
            if (row[0] == null) {
                continue; // an SLA whose subject has no owner cannot be anybody's score
            }
            Acc acc = acc(byUser, row[0], row[1]);
            SlaOutcome outcome = SlaOutcomeClassifier.classify(
                    slaStatus(row[2]), offsetDateTime(row[3]), offsetDateTime(row[4]),
                    offsetDateTime(row[5]), now);
            if (outcome.isDecided()) {
                acc.slaDecided++;
                if (outcome == SlaOutcome.RESOLVED_ON_TIME) {
                    acc.slaOnTime++;
                }
            }
        }
    }

    // ── Team baseline ─────────────────────────────────────────────────────────

    /**
     * Pooled rates — numerators and denominators summed across the team, not the mean of each rep's
     * percentage. Averaging percentages lets a rep with one lead move the team's conversion rate as
     * much as a rep with fifty, and it is that number every thin sample is pulled toward.
     */
    private TeamBaseline baseline(Map<UUID, Acc> byUser, Set<UUID> scoredUsers, int repCount) {
        long leads = 0;
        long cohort = 0;
        long won = 0;
        long closed = 0;
        long quotes = 0;
        long accepted = 0;
        long slaDecided = 0;
        long slaOnTime = 0;
        long tasks = 0;
        long tasksDone = 0;
        long tasksOnTime = 0;
        long tasksJudged = 0;
        long collections = 0;
        long collectionsOnTime = 0;
        long forecasts = 0;
        long forecastHits = 0;
        double csatSum = 0;
        long csatCount = 0;

        for (Map.Entry<UUID, Acc> entry : byUser.entrySet()) {
            if (entry.getKey() == null || !scoredUsers.contains(entry.getKey())) {
                continue;
            }
            Acc a = entry.getValue();
            leads += a.leadsCreated;
            cohort += a.cohortConverted;
            won += a.dealsWon;
            closed += a.dealsWon + a.dealsLost;
            quotes += a.quotationsCreated;
            accepted += a.quotationsAccepted;
            slaDecided += a.slaDecided;
            slaOnTime += a.slaOnTime;
            tasks += a.tasksTotal;
            tasksDone += a.tasksCompleted;
            tasksOnTime += a.tasksOnTime;
            tasksJudged += a.tasksOnTime + a.tasksLate;
            collections += a.collectionTotal;
            collectionsOnTime += a.collectionOnTime;
            forecasts += a.forecastTotal;
            forecastHits += a.forecastHit;
            if (a.csat != null && a.csatSamples > 0) {
                csatSum += a.csat * a.csatSamples;
                csatCount += a.csatSamples;
            }
        }

        return TeamBaseline.builder()
                .repCount(repCount)
                .leadConversionRate(rate(cohort, leads))
                .winRate(rate(won, closed))
                .quotationAcceptanceRate(rate(accepted, quotes))
                .slaComplianceRate(rate(slaOnTime, slaDecided))
                .taskCompletionRate(rate(tasksDone, tasks))
                .taskPunctualityRate(rate(tasksOnTime, tasksJudged))
                .collectionOnTimeRate(rate(collectionsOnTime, collections))
                .forecastAccuracyRate(rate(forecastHits, forecasts))
                .csat(csatCount == 0 ? null : ReportingUtils.round2(csatSum / csatCount))
                .build();
    }

    /**
     * What could not be measured, in words. A metric that is missing and a metric that is bad look
     * identical once averaged into an axis, so the difference is stated rather than left to be
     * inferred from a gap in a chart.
     */
    private static List<String> dataGaps(RepMetrics m, boolean activityTracked) {
        List<String> gaps = new ArrayList<>();
        if (!activityTracked) {
            gaps.add("No audit activity was recorded for anyone this period, so days worked are "
                    + "unknown and were not used to decide who could be ranked.");
        }
        if (m.getSlaDecided() == 0) {
            gaps.add("No SLA reached an outcome in this period — compliance is not scored.");
        }
        if (m.getTasksTotal() == 0) {
            gaps.add("No follow-up task fell due in this period.");
        }
        if (m.getCsatSamples() == 0) {
            gaps.add("No customer feedback was received — the quality axis is not scored.");
        }
        if (m.getCollectionTotal() == 0) {
            gaps.add("No payment carried a due date, so collection punctuality is unknown.");
        }
        if (m.getForecastTotal() == 0) {
            gaps.add("No closed deal carried an expected close date, so forecast accuracy is unknown.");
        }
        if (m.getLeadsCreated() > 0 && m.getFirstResponseSamples() < m.getLeadsCreated()) {
            gaps.add(String.format("First response is measurable for %d of %d leads.",
                    m.getFirstResponseSamples(), m.getLeadsCreated()));
        }
        return gaps;
    }

    // ── Plumbing ──────────────────────────────────────────────────────────────

    private Acc acc(Map<UUID, Acc> byUser, Object ownerId, Object ownerName) {
        UUID key = toUuid(ownerId);
        Acc acc = byUser.computeIfAbsent(key, id -> new Acc());
        if (acc.name == null && ownerName instanceof String name && !name.isBlank()) {
            acc.name = name;
        }
        return acc;
    }

    private static Double rate(long part, long whole) {
        return whole <= 0 ? null : ReportingUtils.calculateRate(part, whole);
    }

    private static String str(Object value) {
        return value == null ? "" : value.toString();
    }

    private static Double nullableDouble(Object value) {
        return (value instanceof Number number) ? ReportingUtils.round2(number.doubleValue()) : null;
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

    /** A rep frozen at the point of scoring, so ranking can sort before any response row is built. */
    private static final class Scored {
        final UUID userId;
        final String name;
        final RepMetrics metrics;
        final List<LostReason> lostReasons;
        RepScore score;

        Scored(UUID userId, Acc acc) {
            this.userId = userId;
            this.name = acc.name != null ? acc.name : userId.toString();
            this.metrics = acc.toMetrics();
            this.lostReasons = acc.topLostReasons();
        }
    }

    /** One rep's running totals while the five result sets are folded together. */
    private static final class Acc {
        String name;

        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal wonValue = BigDecimal.ZERO;
        long dealsWon;
        long dealsLost;
        long bookingsConfirmed;
        long leadsConverted;
        long leadsCreated;
        long cohortConverted;
        long quotationsCreated;
        long quotationsAccepted;

        Double firstResponseHours;
        long firstResponseSamples;
        Double leadToConversionDays;
        Double quotationTurnaroundHours;
        Double dealCycleDays;

        long slaDecided;
        long slaOnTime;
        long tasksTotal;
        long tasksCompleted;
        long tasksOverdue;
        long tasksOnTime;
        long tasksLate;
        Double avgDiscountPercent;
        long quotationRoots;
        long quotationRevisions;
        long collectionTotal;
        long collectionOnTime;
        long forecastTotal;
        long forecastHit;
        long activeDays;

        Double csat;
        long csatSamples;
        Double csatAttitude;
        Double csatSpeed;
        Double csatAccuracy;
        final Map<String, Long> lostReasons = new LinkedHashMap<>();

        boolean hasAnything() {
            // tasksOnTime/tasksLate sit on the completed_at axis, so a rep whose only activity was
            // finishing work raised in an earlier period has measurable data and no tasksTotal.
            return leadsCreated > 0 || dealsWon > 0 || dealsLost > 0 || quotationsCreated > 0
                    || bookingsConfirmed > 0 || tasksTotal > 0 || slaDecided > 0
                    || tasksOnTime > 0 || tasksLate > 0
                    || csatSamples > 0 || activeDays > 0
                    || revenue.compareTo(BigDecimal.ZERO) != 0;
        }

        List<LostReason> topLostReasons() {
            return lostReasons.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(TOP_LOST_REASONS)
                    .map(e -> LostReason.builder().reason(e.getKey()).count(e.getValue()).build())
                    .toList();
        }

        RepMetrics toMetrics() {
            long dealsClosed = dealsWon + dealsLost;
            return RepMetrics.builder()
                    .revenue(revenue)
                    .wonValue(wonValue)
                    .dealsWon(dealsWon)
                    .dealsLost(dealsLost)
                    .bookingsConfirmed(bookingsConfirmed)
                    .leadsConverted(leadsConverted)
                    .leadsCreated(leadsCreated)
                    .cohortConverted(cohortConverted)
                    .leadConversionRate(rate(cohortConverted, leadsCreated))
                    .dealsClosed(dealsClosed)
                    .winRate(rate(dealsWon, dealsClosed))
                    .quotationsCreated(quotationsCreated)
                    .quotationsAccepted(quotationsAccepted)
                    .quotationAcceptanceRate(rate(quotationsAccepted, quotationsCreated))
                    .firstResponseHours(firstResponseHours)
                    .firstResponseSamples(firstResponseSamples)
                    .firstResponseCoveragePct(rate(firstResponseSamples, leadsCreated))
                    .leadToConversionDays(leadToConversionDays)
                    .quotationTurnaroundHours(quotationTurnaroundHours)
                    .dealCycleDays(dealCycleDays)
                    .slaDecided(slaDecided)
                    .slaOnTime(slaOnTime)
                    .slaComplianceRate(rate(slaOnTime, slaDecided))
                    .tasksTotal(tasksTotal)
                    .tasksCompleted(tasksCompleted)
                    .tasksOverdue(tasksOverdue)
                    .taskCompletionRate(rate(tasksCompleted, tasksTotal))
                    .taskOverdueRate(rate(tasksOverdue, tasksTotal))
                    .tasksOnTime(tasksOnTime)
                    .tasksLate(tasksLate)
                    .taskPunctualityRate(rate(tasksOnTime, tasksOnTime + tasksLate))
                    .avgDiscountPercent(avgDiscountPercent)
                    .quotationRoots(quotationRoots)
                    .quotationRevisions(quotationRevisions)
                    .revisionsPerQuotation(quotationRoots <= 0 ? null
                            : ReportingUtils.round2((double) quotationRevisions / quotationRoots))
                    .collectionTotal(collectionTotal)
                    .collectionOnTime(collectionOnTime)
                    .collectionOnTimeRate(rate(collectionOnTime, collectionTotal))
                    .forecastTotal(forecastTotal)
                    .forecastHit(forecastHit)
                    .forecastAccuracyRate(rate(forecastHit, forecastTotal))
                    .csat(csat)
                    .csatSamples(csatSamples)
                    .csatAttitude(csatAttitude)
                    .csatSpeed(csatSpeed)
                    .csatAccuracy(csatAccuracy)
                    .activeDays(activeDays)
                    // Settled outcomes only. Leads raised and quotations drafted say how busy a rep
                    // was, not how much of their performance has actually been decided yet.
                    .sampleSize(dealsClosed + bookingsConfirmed + leadsConverted)
                    .build();
        }
    }
}
