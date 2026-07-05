package com.novax.leadora.application.usecase.reporting;

import com.novax.leadora.api.dto.response.PipelineProgressionReportResponse;
import com.novax.leadora.api.dto.response.PipelineProgressionReportResponse.StageRow;
import com.novax.leadora.infrastructure.persistence.entity.DealEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.DealPipelineStage;
import com.novax.leadora.infrastructure.persistence.repository.DealRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.cache.annotation.Cacheable;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** UC-23.4 — View Sales Pipeline Progression Report. */
@Service
@RequiredArgsConstructor
public class GetPipelineProgressionReportUseCase {

    /** Stages that are still "in-flight" (a bottleneck candidate); the other two are terminal. */
    private static final Set<DealPipelineStage> OPEN_STAGES = EnumSet.of(
            DealPipelineStage.PROSPECTING, DealPipelineStage.QUALIFICATION,
            DealPipelineStage.PROPOSAL, DealPipelineStage.NEGOTIATION);

    private final DealRepository dealRepository;

    @Cacheable(value = "pipeline-progression-report", key = "#from + '_' + #to", unless = "#result == null")
    @Transactional(readOnly = true)
    public PipelineProgressionReportResponse execute(LocalDate from, LocalDate to) {
        OffsetDateTime now = OffsetDateTime.now();

        OffsetDateTime start = from != null ? from.atStartOfDay().atOffset(java.time.ZoneOffset.UTC)
                : OffsetDateTime.of(1970, 1, 1, 0, 0, 0, 0, java.time.ZoneOffset.UTC);
        OffsetDateTime end = to != null ? to.atTime(java.time.LocalTime.MAX).atOffset(java.time.ZoneOffset.UTC)
                : OffsetDateTime.of(2100, 12, 31, 23, 59, 59, 999999999, java.time.ZoneOffset.UTC);

        List<DealEntity> deals = dealRepository.findByCreatedAtRange(start, end);

        long totalDeals = deals.size();
        long closedWon = countStage(deals, DealPipelineStage.CLOSED_WON);
        long closedLost = countStage(deals, DealPipelineStage.CLOSED_LOST);
        long openDeals = totalDeals - closedWon - closedLost;

        BigDecimal pipelineValue = deals.stream()
                .filter(d -> d.getPipelineStage() != null && OPEN_STAGES.contains(d.getPipelineStage()))
                .map(d -> d.getExpectedRevenue() != null ? d.getExpectedRevenue() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, (a, b) -> a.add(b));

        List<StageRow> stages = new ArrayList<>();
        String bottleneck = null;
        double worstAge = -1;
        for (DealPipelineStage stage : DealPipelineStage.values()) {
            List<DealEntity> inStage = deals.stream()
                    .filter(d -> d.getPipelineStage() == stage).toList();
            BigDecimal value = inStage.stream()
                    .map(d -> d.getExpectedRevenue() != null ? d.getExpectedRevenue() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, (a, b) -> a.add(b));
            double avgAge = inStage.stream()
                    .mapToLong(d -> ageDays(d.getCreatedAt(), now))
                    .average().orElse(0);
            boolean closed = !OPEN_STAGES.contains(stage);

            stages.add(StageRow.builder()
                    .stage(stage.name())
                    .label(label(stage))
                    .count(inStage.size())
                    .value(value)
                    .avgAgeDays(Math.round(avgAge * 10.0) / 10.0)
                    .closed(closed)
                    .build());

            // Bottleneck = the open stage where deals have aged the longest (and something is there).
            if (!closed && !inStage.isEmpty() && avgAge > worstAge) {
                worstAge = avgAge;
                bottleneck = label(stage);
            }
        }

        return PipelineProgressionReportResponse.builder()
                .dateFrom(from)
                .dateTo(to)
                .totalDeals(totalDeals)
                .openDeals(openDeals)
                .closedWon(closedWon)
                .closedLost(closedLost)
                .winRate(rate(closedWon, closedWon + closedLost))
                .pipelineValue(pipelineValue)
                .bottleneckStage(bottleneck)
                .stages(stages)
                .build();
    }

    private long countStage(List<DealEntity> deals, DealPipelineStage stage) {
        return deals.stream().filter(d -> d.getPipelineStage() == stage).count();
    }

    private long ageDays(OffsetDateTime createdAt, OffsetDateTime now) {
        if (createdAt == null) return 0;
        return Math.max(0, Duration.between(createdAt, now).toDays());
    }

    private String label(DealPipelineStage stage) {
        return switch (stage) {
            case PROSPECTING -> "Prospecting";
            case QUALIFICATION -> "Qualification";
            case PROPOSAL -> "Proposal";
            case NEGOTIATION -> "Negotiation";
            case CLOSED_WON -> "Closed won";
            case CLOSED_LOST -> "Closed lost";
        };
    }


    private double rate(long part, long whole) {
        if (whole <= 0) return 0;
        return Math.round((part * 10000.0 / whole)) / 100.0;
    }
}
