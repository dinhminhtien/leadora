package com.novax.leadora.application.usecase.reporting;

import com.novax.leadora.api.dto.request.SaveReportLogRequest;
import com.novax.leadora.api.dto.response.ReportLogResponse;
import com.novax.leadora.infrastructure.persistence.entity.ReportGenerationLogEntity;
import com.novax.leadora.infrastructure.persistence.repository.ReportGenerationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class SaveReportLogUseCase {

    private final ReportGenerationLogRepository reportLogRepository;

    @Transactional
    public ReportLogResponse execute(SaveReportLogRequest request) {
        ReportGenerationLogEntity log = ReportGenerationLogEntity.builder()
                .generatedByName(request.getGeneratedByName())
                .generatedByRole(request.getGeneratedByRole())
                .filterDateFrom(request.getFilterDateFrom())
                .filterDateTo(request.getFilterDateTo())
                .filterRoomType(request.getFilterRoomType())
                .filterDiscountThreshold(request.getFilterDiscountThreshold())
                .resultCount(request.getResultCount())
                .action(request.getAction() != null ? request.getAction() : "GENERATE_DISCOUNT_REPORT")
                .result(request.getResult() != null ? request.getResult() : (request.getResultCount() > 0 ? "SUCCESS" : "NO_DATA"))
                .reason(request.getReason())
                .build();

        return ReportLogResponse.from(reportLogRepository.save(log));
    }

    /**
     * POST-2 (UC-23.1 / UC-23.2) — audit a report VIEW action (who, when, which report, filters,
     * how many records). Runs in its own transaction and is best-effort: an audit-log failure must
     * never break the report the user asked for, so callers wrap this and swallow exceptions.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logReportView(String actorName, String actorRole, String action,
                              LocalDate from, LocalDate to, int resultCount) {
        ReportGenerationLogEntity log = ReportGenerationLogEntity.builder()
                .generatedByName(actorName != null ? actorName : "Unknown")
                .generatedByRole(actorRole)
                .filterDateFrom(from)
                .filterDateTo(to)
                .resultCount(resultCount)
                .action(action)
                .result(resultCount > 0 ? "SUCCESS" : "NO_DATA")
                .build();
        reportLogRepository.save(log);
    }
}
