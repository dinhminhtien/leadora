package com.novax.leadora.application.usecase.quotation;

import com.novax.leadora.api.dto.request.ExpireOverdueRequest;
import com.novax.leadora.infrastructure.persistence.entity.QuotationClosureLogEntity;
import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus;
import com.novax.leadora.infrastructure.persistence.repository.QuotationClosureLogRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExpireOverdueQuotationsUseCase {

    private static final List<QuotationStatus> ELIGIBLE = List.of(
            QuotationStatus.DRAFT,
            QuotationStatus.SENT,
            QuotationStatus.PENDING_APPROVAL,
            QuotationStatus.INTERESTED,
            QuotationStatus.PENDING_REVISION
    );

    private final QuotationRepository quotationRepository;
    private final QuotationClosureLogRepository closureLogRepository;

    @Transactional
    public Map<String, Object> execute(ExpireOverdueRequest request) {
        LocalDate today = LocalDate.now();

        List<QuotationEntity> overdue = quotationRepository
                .findByStatusInAndValidUntilBefore(ELIGIBLE, today);

        List<String> expiredIds = overdue.stream().map(q -> {
            String previousStatus = q.getStatus().name();
            q.setStatus(QuotationStatus.EXPIRED);
            quotationRepository.save(q);

            // POST-2 + BR-37: log each expiration
            closureLogRepository.save(QuotationClosureLogEntity.builder()
                    .quotation(q)
                    .action("EXPIRED")
                    .reason("Validity period exceeded — auto-expired by system")
                    .closedByName(request.getExpiredByName() != null
                            ? request.getExpiredByName() : "System (Auto)")
                    .closedByRole(request.getExpiredByRole())
                    .previousStatus(previousStatus)
                    .newStatus("EXPIRED")
                    .build());

            return q.getQuotationId().toString();
        }).collect(Collectors.toList());

        return Map.of(
                "expiredCount", expiredIds.size(),
                "expiredIds", expiredIds
        );
    }
}
