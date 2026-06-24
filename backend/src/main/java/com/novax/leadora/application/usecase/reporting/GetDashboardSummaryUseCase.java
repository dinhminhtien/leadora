package com.novax.leadora.application.usecase.reporting;

import com.novax.leadora.api.dto.response.DashboardSummaryResponse;
import com.novax.leadora.api.dto.response.DashboardSummaryResponse.StageSummary;
import com.novax.leadora.infrastructure.persistence.entity.DealEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.*;
import com.novax.leadora.infrastructure.persistence.repository.DealRepository;
import com.novax.leadora.infrastructure.persistence.repository.LeadRepository;
import com.novax.leadora.infrastructure.persistence.repository.TaskRepository;
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

    /**
     * Stage display names in pipeline order.
     * Must match the mapping used in {@link com.novax.leadora.application.usecase.deal.DealMapper}.
     */
    private static final List<String> PIPELINE_STAGES = List.of(
            "Inquiry", "Site Visit", "Proposal", "Negotiation", "Contract", "Confirmed"
    );

    @Transactional(readOnly = true)
    public DashboardSummaryResponse execute() {

        // ── Lead KPIs ─────────────────────────────────────────────────────────
        long totalLeads = leadRepository.count();
        long activeLeads = totalLeads
                - leadRepository.findByStatus(LeadStatus.LOST).size()
                - leadRepository.findByStatus(LeadStatus.CONVERTED).size();

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
                    int prob = calculateProbability(d.getPipelineStage(), d.getStatus());
                    return value.multiply(BigDecimal.valueOf(prob)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // ── Task KPIs ─────────────────────────────────────────────────────────
        // Pending = not COMPLETED and not CANCELLED
        long pendingTasks = taskRepository.findAll().stream()
                .filter(t -> t.getStatus() != TaskStatus.COMPLETED && t.getStatus() != TaskStatus.CANCELLED)
                .count();

        // Overdue = status OPEN && endAt < now
        OffsetDateTime now = OffsetDateTime.now();
        long overdueTasks = taskRepository.findAll().stream()
                .filter(t -> t.getStatus() != TaskStatus.COMPLETED && t.getStatus() != TaskStatus.CANCELLED)
                .filter(t -> t.getEndAt() != null && t.getEndAt().isBefore(now))
                .count();

        // ── Sales Funnel ──────────────────────────────────────────────────────
        List<StageSummary> funnelStages = new ArrayList<>();
        for (String stageName : PIPELINE_STAGES) {
            long count = 0;
            BigDecimal value = BigDecimal.ZERO;

            for (DealEntity deal : allDeals) {
                String dealStageName = mapStageToString(deal.getPipelineStage(), deal.getStatus());
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

    // ── Private helpers (mirrored from DealMapper) ─────────────────────

    private String mapStageToString(DealPipelineStage stage, DealStatus status) {
        if (stage == null) return "Inquiry";
        switch (stage) {
            case PROSPECTING:  return "Inquiry";
            case QUALIFICATION: return "Site Visit";
            case PROPOSAL:     return "Proposal";
            case NEGOTIATION:  return "Negotiation";
            case CLOSED_WON:
                return (status == DealStatus.WON) ? "Confirmed" : "Contract";
            case CLOSED_LOST:  return "Confirmed";
            default:           return "Inquiry";
        }
    }

    private int calculateProbability(DealPipelineStage stage, DealStatus status) {
        if (status == DealStatus.WON) return 100;
        if (status == DealStatus.LOST) return 0;
        if (stage == null) return 50;
        switch (stage) {
            case PROSPECTING:  return 10;
            case QUALIFICATION: return 30;
            case PROPOSAL:     return 50;
            case NEGOTIATION:  return 70;
            case CLOSED_WON:   return 100;
            case CLOSED_LOST:  return 0;
            default:           return 50;
        }
    }
}
