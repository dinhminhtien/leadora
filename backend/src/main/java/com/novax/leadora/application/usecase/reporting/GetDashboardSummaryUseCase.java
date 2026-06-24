package com.novax.leadora.application.usecase.reporting;

import com.novax.leadora.api.dto.response.DashboardSummaryResponse;
import com.novax.leadora.api.dto.response.DashboardSummaryResponse.StageSummary;
import com.novax.leadora.infrastructure.persistence.entity.DealEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.*;
import com.novax.leadora.infrastructure.persistence.repository.DealRepository;
import com.novax.leadora.infrastructure.persistence.repository.LeadRepository;
import com.novax.leadora.infrastructure.persistence.repository.TaskRepository;
import com.novax.leadora.application.usecase.deal.DealMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.*;

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
    private final DealMapper dealMapper;

    /**
     * Stage display names in pipeline order.
     * Must match the mapping used in
     * {@link com.novax.leadora.application.usecase.deal.DealMapper}.
     */
    private static final List<String> PIPELINE_STAGES = List.of(
            "Inquiry", "Site Visit", "Proposal", "Negotiation", "Contract", "Confirmed");

    @Transactional(readOnly = true)
    public DashboardSummaryResponse execute() {

        // ── Lead KPIs ─────────────────────────────────────────────────────────
        long totalLeads = leadRepository.count();
        long activeLeads = totalLeads
                - leadRepository.countByStatus(LeadStatus.LOST)
                - leadRepository.countByStatus(LeadStatus.CONVERTED);

        // ── Deal KPIs ─────────────────────────────────────────────────────────
        List<DealEntity> allDeals = dealRepository.findAll();

        List<DealEntity> activeDeals = dealRepository.findByStatus(DealStatus.OPEN);
        BigDecimal activeDealsValue = activeDeals.stream()
                .map(d -> d.getExpectedRevenue() != null ? d.getExpectedRevenue() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalDealsValue = allDeals.stream()
                .map(d -> d.getExpectedRevenue() != null ? d.getExpectedRevenue() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Weighted pipeline = Σ(deal.value × stage_probability / 100)
        BigDecimal weightedPipelineValue = allDeals.stream()
                .map(d -> {
                    BigDecimal value = d.getExpectedRevenue() != null ? d.getExpectedRevenue() : BigDecimal.ZERO;
                    int prob = dealMapper.calculateProbability(d.getPipelineStage(), d.getStatus());
                    return value.multiply(BigDecimal.valueOf(prob)).divide(BigDecimal.valueOf(100), 2,
                            RoundingMode.HALF_UP);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // ── Task KPIs ─────────────────────────────────────────────────────────
        List<TaskStatus> excludedTaskStatuses = List.of(TaskStatus.COMPLETED, TaskStatus.CANCELLED);
        long pendingTasks = taskRepository.countByStatusNotIn(excludedTaskStatuses);

        OffsetDateTime now = OffsetDateTime.now();
        long overdueTasks = taskRepository.countByStatusNotInAndEndAtBefore(excludedTaskStatuses, now);

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

        return DashboardSummaryResponse.builder()
                .activeLeadsCount(activeLeads)
                .totalLeadsCount(totalLeads)
                .activeDealsCount(activeDeals.size())
                .activeDealsValue(activeDealsValue)
                .weightedPipelineValue(weightedPipelineValue)
                .totalDealsValue(totalDealsValue)
                .pendingTasksCount(pendingTasks)
                .overdueTasksCount(overdueTasks)
                .funnelStages(funnelStages)
                .build();
    }
}
