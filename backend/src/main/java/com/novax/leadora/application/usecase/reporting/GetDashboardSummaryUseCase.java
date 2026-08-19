package com.novax.leadora.application.usecase.reporting;

import com.novax.leadora.api.dto.response.DashboardSummaryResponse;
import com.novax.leadora.api.dto.response.DashboardSummaryResponse.LeaderboardEntry;
import com.novax.leadora.api.dto.response.DashboardSummaryResponse.StageSummary;
import com.novax.leadora.application.usecase.deal.DealMapper;
import com.novax.leadora.infrastructure.persistence.entity.*;
import com.novax.leadora.infrastructure.persistence.entity.enums.*;
import com.novax.leadora.infrastructure.persistence.repository.*;
import com.novax.leadora.infrastructure.persistence.specification.DealSpecification;
import com.novax.leadora.infrastructure.persistence.specification.LeadSpecification;
import com.novax.leadora.infrastructure.persistence.specification.TaskSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Aggregates all dashboard KPI metrics on the server side.
 * The frontend should ONLY display the pre-computed values returned here
 * — no business logic or aggregation should happen in the browser.
 */
@Service
@RequiredArgsConstructor
public class GetDashboardSummaryUseCase {

    private final LeadRepository leadRepository;
    private final DealRepository dealRepository;
    private final TaskRepository taskRepository;
    private final SlaTrackingRepository slaTrackingRepository;
    private final InteractTimelineRepository interactTimelineRepository;
    private final DealMapper dealMapper;

    /**
     * Stage display names in pipeline order.
     * Must match the mapping used in
     * {@link com.novax.leadora.application.usecase.deal.DealMapper}.
     */
    private static final List<String> PIPELINE_STAGES = List.of(
            "Inquiry", "Site Visit", "Proposal", "Negotiation", "Contract", "Confirmed");

    @Cacheable(value = "dashboard-summary", key = "#actor.userId", unless = "#result == null")
    @Transactional(readOnly = true)
    public DashboardSummaryResponse execute(UserEntity actor) {
        boolean unscoped = canSeeAll(actor);
        UUID userId = actor.getUserId();
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime sevenDaysAgo = now.minusDays(7);
        OffsetDateTime fourteenDaysAgo = now.minusDays(14);

        // ── Lead KPIs ─────────────────────────────────────────────────────────
        Specification<LeadEntity> totalLeadsSpec = LeadSpecification.filter(null, null, null, null, null, null,
                unscoped, userId, false, false);
        long totalLeads = leadRepository.count(totalLeadsSpec);

        Specification<LeadEntity> lostLeadsSpec = LeadSpecification.filter(null, LeadStatus.LOST, null, null, null,
                null, unscoped, userId, false, false);
        long lostLeads = leadRepository.count(lostLeadsSpec);

        Specification<LeadEntity> convertedLeadsSpec = LeadSpecification.filter(null, LeadStatus.CONVERTED, null, null,
                null, null, unscoped, userId, false, false);
        long convertedLeads = leadRepository.count(convertedLeadsSpec);

        long activeLeads = totalLeads - lostLeads - convertedLeads;

        // Lead WoW growth. Counted in the database over the two windows rather than by loading
        // every lead in scope and filtering in memory - the only thing the whole list was used for.
        long recentLeads = leadRepository.count(LeadSpecification.filter(null, null, null, null,
                sevenDaysAgo, now, unscoped, userId, false, false));
        long prevLeads = leadRepository.count(LeadSpecification.filter(null, null, null, null,
                fourteenDaysAgo, sevenDaysAgo, unscoped, userId, false, false));
        Double activeLeadsGrowthPct = growthPct(recentLeads, prevLeads);

        // ── Deal KPIs ─────────────────────────────────────────────────────────
        List<DealEntity> allDeals = dealRepository.findAll(DealSpecification.filter(null, null, unscoped, userId));

        List<DealEntity> activeDeals = allDeals.stream()
                .filter(d -> d.getStatus() == DealStatus.OPEN)
                .toList();
        BigDecimal activeDealsValue = activeDeals.stream()
                .map(d -> d.getExpectedRevenue() != null ? d.getExpectedRevenue() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, (a, b) -> a.add(b));

        BigDecimal totalDealsValue = allDeals.stream()
                .map(d -> d.getExpectedRevenue() != null ? d.getExpectedRevenue() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, (a, b) -> a.add(b));

        // Weighted pipeline = Σ(deal.value × stage_probability / 100)
        BigDecimal weightedPipelineValue = allDeals.stream()
                .map(d -> {
                    BigDecimal value = d.getExpectedRevenue() != null ? d.getExpectedRevenue() : BigDecimal.ZERO;
                    int prob = dealMapper.calculateProbability(d.getPipelineStage(), d.getStatus());
                    return value.multiply(BigDecimal.valueOf(prob)).divide(BigDecimal.valueOf(100), 2,
                            RoundingMode.HALF_UP);
                })
                .reduce(BigDecimal.ZERO, (a, b) -> a.add(b));

        // Win Rate & Conversion
        long dealsWon = allDeals.stream().filter(d -> d.getStatus() == DealStatus.WON).count();
        long dealsLost = allDeals.stream().filter(d -> d.getStatus() == DealStatus.LOST).count();
        long closedDeals = dealsWon + dealsLost;
        Double winRatePct = closedDeals == 0
                ? null
                : Math.round((double) dealsWon / closedDeals * 1000.0) / 10.0;
        String winRateBenchmarkLabel = winRatePct == null ? null : winRateLabel(winRatePct);

        // Avg Deal Size & MoM Growth
        BigDecimal avgDealSize;
        if (!activeDeals.isEmpty()) {
            avgDealSize = activeDealsValue.divide(BigDecimal.valueOf(activeDeals.size()), 0, RoundingMode.HALF_UP);
        } else if (!allDeals.isEmpty()) {
            avgDealSize = totalDealsValue.divide(BigDecimal.valueOf(allDeals.size()), 0, RoundingMode.HALF_UP);
        } else {
            avgDealSize = null; // no deals at all: there is no average, which is not the same as one
        }

        // A real month-on-month comparison, from the deals already loaded above: the average size
        // of deals opened in the last 30 days against the 30 before that.
        OffsetDateTime thirtyDaysAgo = now.minusDays(30);
        OffsetDateTime sixtyDaysAgo = now.minusDays(60);
        Double avgDealSizeGrowthPct = growthPct(
                averageValueBetween(allDeals, thirtyDaysAgo, now),
                averageValueBetween(allDeals, sixtyDaysAgo, thirtyDaysAgo));

        // ── Task KPIs ─────────────────────────────────────────────────────────
        Specification<TaskEntity> pendingTasksSpec = (root, query, cb) -> cb.and(
                cb.notEqual(root.get("status"), TaskStatus.COMPLETED),
                cb.notEqual(root.get("status"), TaskStatus.CANCELLED));
        if (!unscoped) {
            pendingTasksSpec = pendingTasksSpec.and(TaskSpecification.assignedTo(userId));
        }
        long pendingTasks = taskRepository.count(pendingTasksSpec);

        Specification<TaskEntity> overdueTasksSpec = TaskSpecification.isOverdue();
        if (!unscoped) {
            overdueTasksSpec = overdueTasksSpec.and(TaskSpecification.assignedTo(userId));
        }
        long overdueTasks = taskRepository.count(overdueTasksSpec);

        // ── SLA Compliance & Response Speed ──────────────────────────────────
        // Scoped like every other figure on this screen. It used to be findAll(): a Sales Staff
        // was shown the whole company's SLA compliance on a dashboard that hides colleagues'
        // leads, deals and tasks two blocks above.
        List<Object[]> slaRows = slaTrackingRepository.findDashboardRows(unscoped ? null : userId.toString());
        int totalSla = slaRows.size();
        int compliantCount = 0;
        double totalHrs = 0;
        int resolvedCount = 0;

        for (Object[] row : slaRows) {
            SlaStatus status = row[0] == null ? null : SlaStatus.valueOf(row[0].toString());
            OffsetDateTime startedAt = toOffset(row[1]);
            OffsetDateTime deadlineAt = toOffset(row[2]);
            OffsetDateTime resolvedAt = toOffset(row[3]);

            if (status == SlaStatus.RESOLVED) {
                resolvedCount++;
                if (resolvedAt != null && deadlineAt != null && !resolvedAt.isAfter(deadlineAt)) {
                    compliantCount++;
                }
                if (resolvedAt != null && startedAt != null) {
                    double hrs = Duration.between(startedAt, resolvedAt).toMinutes() / 60.0;
                    if (hrs >= 0) {
                        totalHrs += hrs;
                    }
                }
            } else if (status == SlaStatus.ACTIVE && deadlineAt != null && !now.isAfter(deadlineAt)) {
                compliantCount++;
            }
        }
        Double slaComplianceRatePct = totalSla == 0
                ? null
                : Math.round((double) compliantCount / totalSla * 1000.0) / 10.0;
        Double avgResponseHours = resolvedCount == 0
                ? null
                : Math.round((totalHrs / resolvedCount) * 10.0) / 10.0;

        // ── Sales Funnel ──────────────────────────────────────────────────────
        List<StageSummary> funnelStages = new ArrayList<>();
        for (String stageName : PIPELINE_STAGES) {
            long count = 0;
            BigDecimal value = BigDecimal.ZERO;

            for (DealEntity deal : allDeals) {
                String dealStageName = dealMapper.mapStageToString(deal.getPipelineStage(), deal.getStatus());
                if (stageName.equals(dealStageName)) {
                    count++;
                    value = value.add(deal.getExpectedRevenue() != null ? deal.getExpectedRevenue() : BigDecimal.ZERO);
                }
            }

            funnelStages.add(StageSummary.builder()
                    .stage(stageName)
                    .count(count)
                    .value(value)
                    .build());
        }

        // ── Team Activity Leaderboard ────────────────────────────────────────
        // Named colleagues, so it is a Manager/Admin block: a scoped caller gets nothing rather
        // than a one-row "leaderboard" of themselves, and never other people's activity counts.
        // Counted with a GROUP BY instead of streaming every interaction row ever recorded.
        List<LeaderboardEntry> leaderboard = unscoped
                ? interactTimelineRepository.findTopUserActivity(PageRequest.of(0, 5)).stream()
                        .map(row -> LeaderboardEntry.builder()
                                .name((String) row[0])
                                .actionCount(((Number) row[1]).longValue())
                                .build())
                        .collect(Collectors.toList())
                : List.of();
        // Deliberately left empty when there is no activity. It used to fall back to a single
        // invented row - the current user, credited with 14 actions they had not performed.

        List<DashboardSummaryResponse.MonthlyForecast> monthlyForecasts = new ArrayList<>();
        java.time.format.DateTimeFormatter monthFormatter = java.time.format.DateTimeFormatter.ofPattern("MMM",
                Locale.US);
        LocalDate currentDate = LocalDate.now();
        for (int i = 5; i >= 0; i--) {
            LocalDate targetDate = currentDate.minusMonths(i);
            int year = targetDate.getYear();
            int monthValue = targetDate.getMonthValue();
            String monthName = targetDate.format(monthFormatter);
            if (i == 0) {
                monthName = monthName + " (Current)";
            }

            BigDecimal monthWeightedValue = allDeals.stream()
                    .filter(d -> d.getExpectedCloseDate() != null
                            && d.getExpectedCloseDate().getYear() == year
                            && d.getExpectedCloseDate().getMonthValue() == monthValue)
                    .map(d -> {
                        BigDecimal value = d.getExpectedRevenue() != null ? d.getExpectedRevenue() : BigDecimal.ZERO;
                        int prob = dealMapper.calculateProbability(d.getPipelineStage(), d.getStatus());
                        return value.multiply(BigDecimal.valueOf(prob)).divide(BigDecimal.valueOf(100), 2,
                                RoundingMode.HALF_UP);
                    })
                    .reduce(BigDecimal.ZERO, (a, b) -> a.add(b));

            monthlyForecasts.add(DashboardSummaryResponse.MonthlyForecast.builder()
                    .month(monthName)
                    .value(monthWeightedValue)
                    .build());
        }

        return DashboardSummaryResponse.builder()
                .activeLeadsCount(activeLeads)
                .totalLeadsCount(totalLeads)
                .activeLeadsGrowthPct(activeLeadsGrowthPct)
                .activeDealsCount(activeDeals.size())
                .activeDealsValue(activeDealsValue)
                .weightedPipelineValue(weightedPipelineValue)
                .totalDealsValue(totalDealsValue)
                .pendingTasksCount(pendingTasks)
                .overdueTasksCount(overdueTasks)
                .slaComplianceRatePct(slaComplianceRatePct)
                .avgResponseHours(avgResponseHours)
                .avgDealSize(avgDealSize)
                .avgDealSizeGrowthPct(avgDealSizeGrowthPct)
                .winRatePct(winRatePct)
                .winRateBenchmarkLabel(winRateBenchmarkLabel)
                .funnelStages(funnelStages)
                .leaderboard(leaderboard)
                .monthlyForecasts(monthlyForecasts)
                .build();
    }

    private boolean canSeeAll(UserEntity actor) {
        if (actor.getRole() == null || actor.getRole().getRoleName() == null) {
            return false;
        }
        String roleName = actor.getRole().getRoleName().trim().toUpperCase();
        return "MANAGER".equals(roleName) || "ADMIN".equals(roleName) || "OWNER".equals(roleName);
    }

    /**
     * Percentage change between two periods, or {@code null} when the earlier one is empty.
     *
     * <p>The null is the point. Growth against a base of zero is undefined - every "+12.5%" and
     * "+8% MoM" this class used to emit came from exactly that case, where nothing had been
     * measured at all. A tile showing a dash is a true statement; one showing a made-up
     * percentage is not, and it is indistinguishable from a real one.
     */
    private static Double growthPct(double current, double previous) {
        if (previous <= 0) {
            return null;
        }
        return Math.round((current - previous) / previous * 1000.0) / 10.0;
    }

    /** Mean expected revenue of the deals opened in {@code [from, to)}; 0 when there are none. */
    private static double averageValueBetween(List<DealEntity> deals, OffsetDateTime from, OffsetDateTime to) {
        List<DealEntity> inWindow = deals.stream()
                .filter(d -> d.getCreatedAt() != null
                        && !d.getCreatedAt().isBefore(from)
                        && d.getCreatedAt().isBefore(to))
                .toList();
        if (inWindow.isEmpty()) {
            return 0;
        }
        BigDecimal sum = inWindow.stream()
                .map(d -> d.getExpectedRevenue() != null ? d.getExpectedRevenue() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.doubleValue() / inWindow.size();
    }

    /**
     * A word for the win rate, against fixed in-house thresholds.
     *
     * <p>Not "Top 10%": that named a rank among other companies, and there is no industry
     * benchmark anywhere in this system to have ranked against. These describe the number the
     * user is already looking at, and nothing else.
     */
    private static String winRateLabel(double winRatePct) {
        if (winRatePct >= 35.0) {
            return "High";
        }
        return winRatePct >= 20.0 ? "Moderate" : "Low";
    }

    /** JDBC hands a native timestamp back as {@code Timestamp} or {@code OffsetDateTime}. */
    private static OffsetDateTime toOffset(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof OffsetDateTime offset) {
            return offset;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant().atOffset(OffsetDateTime.now().getOffset());
        }
        if (value instanceof java.time.Instant instant) {
            return instant.atOffset(OffsetDateTime.now().getOffset());
        }
        return null;
    }

}
