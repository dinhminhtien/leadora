package com.novax.leadora.application.usecase.quotation;

import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.application.event.QuotationAcceptedByCustomerEvent;
import com.novax.leadora.application.usecase.activitylog.ActivityLogPublisher;
import com.novax.leadora.application.usecase.audit.SystemAuditLogService;
import com.novax.leadora.application.usecase.contract.GenerateContractUseCase;
import com.novax.leadora.application.usecase.roomrequest.AutoRoomRequestService;
import com.novax.leadora.infrastructure.persistence.repository.ContractRepository;
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
 * UC-14.x — the customer accepts their quotation from the secure link.
 *
 * <p>Acceptance is a single click. It used to be gated behind a one-time code emailed to the
 * customer, who then had to find it and type it back; Report 1 requires no such step anywhere in
 * the quotation workflow, and it cost the sale a round trip through the customer's inbox at the
 * exact moment they had decided to say yes. The secure link itself is the credential — a 256-bit
 * token, single-use, expiring in 24 hours — and every acceptance is still recorded with its IP
 * address and user agent.
 *
 * <p>Transitions the quotation to {@code RESERVATION_PENDING}, marks the token used, records the
 * audit trail, puts the availability question to the Reservation team, and fires the event.
 *
 * <p>At this moment — the single authoritative business event where a Sales estimate becomes a
 * confirmed figure — {@code deal.expectedRevenue} is updated to match
 * {@code quotation.totalAmount} within the same atomic transaction. This is the ONLY place this
 * sync happens; {@code DealWorkflowSyncService} deliberately does NOT touch {@code deal_value} to
 * preserve its role as an independent forecast field during earlier pipeline stages.
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
        private final GetQuotationByTokenUseCase getQuotationByTokenUseCase;
        private final AutoRoomRequestService autoRoomRequestService;
        private final GenerateContractUseCase generateContractUseCase;
        private final ContractRepository contractRepository;

        @Transactional
        public QuotationEntity execute(UUID quotationId, String token, String ipAddress, String userAgent) {
                log.info("Processing customer acceptance for quotation: {}", quotationId);

                // The link is the credential: rejects an unknown, expired, already-used or
                // tampered token before anything else happens.
                getQuotationByTokenUseCase.validateToken(quotationId, token);

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

                // 7. Put the availability question to the Reservation team. This is the canonical
                // trigger: the customer has committed, so the sales side now needs a real answer
                // about rooms. Non-fatal — a problem raising it must not undo the acceptance.
                autoRoomRequestService.raiseOnCustomerAcceptance(savedQuotation,
                                savedQuotation.getCreatedBy());

                // 8. Draft the contract so it is ready for the rep to send once the Reservation
                // team confirms. Non-fatal for the same reason.
                try {
                        if (contractRepository.findByQuotation_QuotationId(quotationId).isEmpty()) {
                                generateContractUseCase.execute(savedQuotation, savedQuotation.getCreatedBy());
                        }
                } catch (Exception e) {
                        log.warn("Could not draft the contract for quotation {}: {}", quotationId, e.getMessage());
                }

                // 9. Publish Spring ApplicationEvent
                eventPublisher.publishEvent(new QuotationAcceptedByCustomerEvent(savedQuotation));

                return savedQuotation;
        }
}
