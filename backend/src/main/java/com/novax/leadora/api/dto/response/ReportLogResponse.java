package com.novax.leadora.api.dto.response;

import com.novax.leadora.infrastructure.persistence.entity.ReportGenerationLogEntity;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class ReportLogResponse {

    private UUID logId;
    private String generatedByName;
    private String generatedByRole;
    private LocalDate filterDateFrom;
    private LocalDate filterDateTo;
    private String filterRoomType;
    private BigDecimal filterDiscountThreshold;
    private Integer resultCount;
    private String action;
    private String result;
    private String reason;
    private OffsetDateTime generatedAt;

    public static ReportLogResponse from(ReportGenerationLogEntity entity) {
        return ReportLogResponse.builder()
                .logId(entity.getLogId())
                .generatedByName(entity.getGeneratedByName())
                .generatedByRole(entity.getGeneratedByRole())
                .filterDateFrom(entity.getFilterDateFrom())
                .filterDateTo(entity.getFilterDateTo())
                .filterRoomType(entity.getFilterRoomType())
                .filterDiscountThreshold(entity.getFilterDiscountThreshold())
                .resultCount(entity.getResultCount())
                .action(entity.getAction())
                .result(entity.getResult())
                .reason(entity.getReason())
                .generatedAt(entity.getCreatedAt())
                .build();
    }
}
