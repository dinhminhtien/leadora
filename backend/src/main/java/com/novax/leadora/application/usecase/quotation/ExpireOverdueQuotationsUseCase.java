package com.novax.leadora.application.usecase.quotation;

import com.novax.leadora.api.dto.request.ExpireOverdueRequest;
import com.novax.leadora.application.usecase.sla.ResolveSlaBreachUseCase;
import com.novax.leadora.infrastructure.persistence.entity.QuotationClosureLogEntity;
import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus;
import com.novax.leadora.infrastructure.persistence.repository.QuotationClosureLogRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationRepository;
import com.novax.leadora.application.usecase.activitylog.ActivityLogPublisher;
import com.novax.leadora.infrastructure.persistence.entity.enums.ActivityLogType;
import com.novax.leadora.infrastructure.persistence.entity.enums.EntityType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExpireOverdueQuotationsUseCase {

    private static final List<QuotationStatus> ELIGIBLE = List.of(
            QuotationStatus.DRAFT,
            QuotationStatus.SENT,
            QuotationStatus.PENDING_APPROVAL,
            QuotationStatus.INTERESTED,
            QuotationStatus.PENDING_REVISION,
            QuotationStatus.PENDING_CUSTOMER_RESPONSE
    );

    private final QuotationRepository quotationRepository;
    private final QuotationClosureLogRepository closureLogRepository;
    private final ResolveSlaBreachUseCase resolveSlaBreachUseCase;
    private final ActivityLogPublisher activityLogPublisher;
    private final ObjectMapper objectMapper;

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

            try {
                resolveSlaBreachUseCase.executeByEntity("QUOTATION", q.getQuotationId());
            } catch (Exception e) {
                log.warn("SLA auto-resolve failed for expired quotation {}: {}", q.getQuotationId(), e.getMessage());
            }

            try {
                ObjectNode payload = objectMapper.createObjectNode()
                        .put("reason", "Validity period exceeded — auto-expired by system")
                        .put("previousStatus", previousStatus)
                        .put("newStatus", "EXPIRED");
                activityLogPublisher.publish(
                        ActivityLogType.QUOTATION_UPDATED,
                        EntityType.QUOTATION,
                        q.getQuotationId(),
                        "Quotation expired",
                        payload
                );
            } catch (Exception e) {
                log.warn("Failed to publish quotation expiration activity: {}", e.getMessage());
            }

            return q.getQuotationId().toString();
        }).collect(Collectors.toList());

        return Map.of(
                "expiredCount", expiredIds.size(),
                "expiredIds", expiredIds
        );
    }
}
