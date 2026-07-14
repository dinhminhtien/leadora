package com.novax.leadora.application.usecase.reporting;

import com.novax.leadora.api.dto.response.DashboardSummaryResponse;
import com.novax.leadora.api.dto.response.DashboardSummaryResponse.StageSummary;
import com.novax.leadora.infrastructure.persistence.entity.DealEntity;
import com.novax.leadora.infrastructure.persistence.entity.LeadEntity;
import com.novax.leadora.infrastructure.persistence.entity.TaskEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.*;
import com.novax.leadora.infrastructure.persistence.repository.DealRepository;
import com.novax.leadora.infrastructure.persistence.repository.LeadRepository;
import com.novax.leadora.infrastructure.persistence.repository.TaskRepository;
import com.novax.leadora.application.usecase.deal.DealMapper;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.specification.DealSpecification;
import com.novax.leadora.infrastructure.persistence.specification.LeadSpecification;
import com.novax.leadora.infrastructure.persistence.specification.TaskSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    @Cacheable(
        value = "dashboard-summary",
        key = "(#actor.role != null && #actor.role.roleName != null && (#actor.role.roleName.trim().toUpperCase() == 'MANAGER' || #actor.role.roleName.trim().toUpperCase() == 'ADMIN') ? 'all' : #actor.userId)",
        unless = "#result == null"
    )
    @Transactional(readOnly = true)
    public DashboardSummaryResponse execute(UserEntity actor) {
        boolean unscoped = canSeeAll(actor);
        UUID userId = actor.getUserId();

        // ── Lead KPIs ─────────────────────────────────────────────────────────
        Specification<LeadEntity> totalLeadsSpec = LeadSpecification.filter(null, null, null, null, null, null, unscoped, userId, false);
        long totalLeads = leadRepository.count(totalLeadsSpec);

        Specification<LeadEntity> lostLeadsSpec = LeadSpecification.filter(null, LeadStatus.LOST, null, null, null, null, unscoped, userId, false);
        long lostLeads = leadRepository.count(lostLeadsSpec);

        Specification<LeadEntity> convertedLeadsSpec = LeadSpecification.filter(null, LeadStatus.CONVERTED, null, null, null, null, unscoped, userId, false);
        long convertedLeads = leadRepository.count(convertedLeadsSpec);

        long activeLeads = totalLeads - lostLeads - convertedLeads;

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

        // ── Task KPIs ─────────────────────────────────────────────────────────
        Specification<TaskEntity> pendingTasksSpec = (root, query, cb) -> cb.and(
                cb.notEqual(root.get("status"), TaskStatus.COMPLETED),
                cb.notEqual(root.get("status"), TaskStatus.CANCELLED)
        );
        if (!unscoped) {
            pendingTasksSpec = pendingTasksSpec.and(TaskSpecification.assignedTo(userId));
        }
        long pendingTasks = taskRepository.count(pendingTasksSpec);

        Specification<TaskEntity> overdueTasksSpec = TaskSpecification.isOverdue();
        if (!unscoped) {
            overdueTasksSpec = overdueTasksSpec.and(TaskSpecification.assignedTo(userId));
        }
        long overdueTasks = taskRepository.count(overdueTasksSpec);

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

    private boolean canSeeAll(UserEntity actor) {
        if (actor.getRole() == null || actor.getRole().getRoleName() == null) {
            return false;
        }
        String roleName = actor.getRole().getRoleName().trim().toUpperCase();
        return "MANAGER".equals(roleName) || "ADMIN".equals(roleName) || "OWNER".equals(roleName);
    }
}
