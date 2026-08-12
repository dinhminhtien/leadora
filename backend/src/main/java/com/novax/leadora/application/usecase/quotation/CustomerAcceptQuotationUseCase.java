package com.novax.leadora.application.usecase.quotation;

import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.application.event.QuotationAcceptedByCustomerEvent;
import com.novax.leadora.application.usecase.activitylog.ActivityLogPublisher;
import com.novax.leadora.application.usecase.audit.SystemAuditLogService;
import com.novax.leadora.infrastructure.persistence.entity.QuotationAcceptanceLogEntity;
import com.novax.leadora.infrastructure.persistence.entity.QuotationConfirmationTokenEntity;
import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ActivityLogType;
import com.novax.leadora.infrastructure.persistence.entity.enums.EntityType;
import com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus;
import com.novax.leadora.infrastructure.persistence.repository.DealRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationAcceptanceLogRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationConfirmationTokenRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * UC-14.x — Internal use case called when customer completes OTP verification.
 * Transitions quotation status to {@code RESERVATION_PENDING}, marks the token
 * as used, records the audit trail, and fires the corresponding event.
 *
 * <p>
 * At this moment — the single authoritative business event where a Sales
 * estimate becomes a confirmed figure — {@code deal.expectedRevenue} is updated
 * to match {@code quotation.totalAmount} within the same atomic transaction.
 * This is the ONLY place this sync happens; {@code DealWorkflowSyncService}
 * deliberately does NOT touch {@code deal_value} to preserve its role as an
 * independent forecast field during earlier pipeline stages.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerAcceptQuotationUseCase {

        private final QuotationRepository quotationRepository;
        private final DealRepository dealRepository;
        private final QuotationConfirmationTokenRepository tokenRepository;
        private final QuotationAcceptanceLogRepository acceptanceLogRepository;
        private final ActivityLogPublisher activityLogPublisher;
        private final SystemAuditLogService systemAuditLogService;
        private final ApplicationEventPublisher eventPublisher;

        @Transactional
        public QuotationEntity execute(UUID quotationId, String ipAddress, String userAgent) {
                log.info("Processing customer acceptance for quotation: {}", quotationId);

                QuotationEntity quotation = quotationRepository.findById(quotationId)
                                .orElseThrow(() -> new BusinessException("QUOTATION_NOT_FOUND", "Quotation not found",
                                                HttpStatus.NOT_FOUND));

                String previousStatus = quotation.getStatus().name();

                // Guard: must be in PENDING_CUSTOMER_RESPONSE status
                if (quotation.getStatus() != QuotationStatus.PENDING_CUSTOMER_RESPONSE) {
                        throw new BusinessException("INVALID_QUOTATION_STATUS",
                                        "Quotation cannot be accepted in its current status: " + quotation.getStatus(),
                                        HttpStatus.BAD_REQUEST);
                }

                // 1. Transition status to RESERVATION_PENDING
                quotation.setStatus(QuotationStatus.RESERVATION_PENDING);
                // Use a new final variable so downstream lambdas can capture it legally.
                // Re-assigning `quotation` would make it non-effectively-final.
                final QuotationEntity savedQuotation = quotationRepository.save(quotation);

                // 2. Mark secure token as used
                QuotationConfirmationTokenEntity tokenEntity = tokenRepository.findByQuotationId(quotationId)
                                .orElse(null);
                if (tokenEntity != null && tokenEntity.getUsedAt() == null) {
                        tokenEntity.setUsedAt(OffsetDateTime.now());
                        tokenRepository.save(tokenEntity);
                }

                // 3. Write acceptance log
                QuotationAcceptanceLogEntity logEntity = QuotationAcceptanceLogEntity.builder()
                                .quotationId(quotationId)
                                .action("ACCEPTED")
                                .ipAddress(ipAddress)
                                .userAgent(userAgent)
                                .build();
                acceptanceLogRepository.save(logEntity);

                // 4. Sync deal.expectedRevenue → quotation.totalAmount.
                // This is the single authoritative sync point: estimate → actuals.
                // We load DealEntity directly via dealRepository to avoid navigating
                // the lazy proxy (getDeal()) which can trigger LazyInitializationException
                // when the EntityManager session has closed (e.g. in WorkflowSyncEntityListener).
                if (savedQuotation.getTotalAmount() != null) {
                        dealRepository.findByQuotationId(quotationId).ifPresent(deal -> {
                                if (deal.getExpectedRevenue() == null
                                                || deal.getExpectedRevenue()
                                                                .compareTo(savedQuotation.getTotalAmount()) != 0) {
                                        log.info("Updating Deal {} expectedRevenue from {} to {} on customer acceptance",
                                                        deal.getDealId(), deal.getExpectedRevenue(),
                                                        savedQuotation.getTotalAmount());
                                        deal.setExpectedRevenue(savedQuotation.getTotalAmount());
                                        dealRepository.save(deal);
                                }
                        });
                }

                // 5. System audit log
                systemAuditLogService.log("QUOTATION", "QUOTATION", quotationId, "CUSTOMER_ACCEPTED_PORTAL",
                                savedQuotation.getCreatedBy(), previousStatus, QuotationStatus.RESERVATION_PENDING.name(),
                                "Customer accepted quotation via portal. Pending reservation confirmation. IP: "
                                                + ipAddress);

                // 6. Activity log
                activityLogPublisher.publish(
                                ActivityLogType.QUOTATION_UPDATED,
                                EntityType.QUOTATION,
                                savedQuotation.getQuotationId(),
                                "Quotation accepted by customer via secure link. Awaiting reservation availability check. IP: "
                                                + ipAddress,
                                null);

                // 7. Publish Spring ApplicationEvent
                eventPublisher.publishEvent(new QuotationAcceptedByCustomerEvent(savedQuotation));

                return savedQuotation;
        }
}
