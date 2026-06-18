package com.novax.leadora.application.usecase.reporting;

import com.novax.leadora.api.dto.request.SaveReportLogRequest;
import com.novax.leadora.api.dto.response.ReportLogResponse;
import com.novax.leadora.infrastructure.persistence.entity.ReportGenerationLogEntity;
import com.novax.leadora.infrastructure.persistence.repository.ReportGenerationLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
