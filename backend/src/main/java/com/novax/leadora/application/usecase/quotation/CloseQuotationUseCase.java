package com.novax.leadora.application.usecase.quotation;

import com.novax.leadora.api.dto.request.CloseQuotationRequest;
import com.novax.leadora.api.dto.response.QuotationResponse;
import com.novax.leadora.application.usecase.audit.SystemAuditLogService;
import com.novax.leadora.application.usecase.sla.ResolveSlaBreachUseCase;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.QuotationClosureLogEntity;
import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus;
import com.novax.leadora.infrastructure.persistence.repository.QuotationClosureLogRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CloseQuotationUseCase {

    private static final Set<QuotationStatus> NON_CLOSEABLE = Set.of(
            QuotationStatus.CONVERTED, QuotationStatus.CLOSED, QuotationStatus.EXPIRED
    );

    private final QuotationRepository quotationRepository;
    private final QuotationClosureLogRepository closureLogRepository;
    private final CurrentUserProvider currentUserProvider;
    private final QuotationAccessPolicy quotationAccessPolicy;
    private final ResolveSlaBreachUseCase resolveSlaBreachUseCase;
    private final SystemAuditLogService systemAuditLogService;

    @Transactional
    public QuotationResponse execute(UUID quotationId, CloseQuotationRequest request) {
        QuotationEntity quotation = quotationRepository.findById(quotationId)
                .orElseThrow(() -> new ResourceNotFoundException("Quotation", quotationId));

        quotationAccessPolicy.assertCanView(quotationAccessPolicy.currentUser(), quotation);

        // E3: Already converted — cannot close
        if (quotation.getStatus() == QuotationStatus.CONVERTED) {
            throw new IllegalStateException(
                    "Quotation has already been converted to a booking and cannot be closed (E3)");
        }

        // E3: Already terminal
        if (NON_CLOSEABLE.contains(quotation.getStatus())) {
            throw new IllegalStateException(
                    "Quotation is already " + quotation.getStatus().name().toLowerCase()
                            + " and cannot be closed again (E3)");
        }

        String previousStatus = quotation.getStatus().name();

        // POST-1: Update status to CLOSED
        quotation.setStatus(QuotationStatus.CLOSED);
        QuotationEntity saved = quotationRepository.save(quotation);

        UserEntity actor = currentUserProvider.resolve(null);
        String actorRole = actor.getRole() != null ? actor.getRole().getRoleName() : null;

        // POST-2: Log closure for audit (BR-37)
        QuotationClosureLogEntity closureLog = QuotationClosureLogEntity.builder()
                .quotation(saved)
                .action("CLOSED")
                .reason(request.getReason())
                .notes(request.getNotes())
                .closedByName(actor.getFullName())
                .closedByRole(actorRole)
                .previousStatus(previousStatus)
                .newStatus("CLOSED")
                .build();
        closureLogRepository.save(closureLog);

        systemAuditLogService.log("QUOTATION", "QUOTATION", quotationId, "CLOSED", actor,
                previousStatus, "CLOSED", request.getReason());

        // POST-3: resolve any pending SLA tracking tied to this quotation — it is no
        // longer an open opportunity to chase (UC-14.8)
        try {
            resolveSlaBreachUseCase.executeByEntity("QUOTATION", quotationId);
        } catch (Exception e) {
            log.warn("SLA auto-resolve failed for closed quotation {}: {}", quotationId, e.getMessage());
        }

        return QuotationResponse.from(saved);
    }
}
