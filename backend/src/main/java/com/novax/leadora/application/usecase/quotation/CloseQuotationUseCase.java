package com.novax.leadora.application.usecase.quotation;

import com.novax.leadora.api.dto.request.CloseQuotationRequest;
import com.novax.leadora.api.dto.response.QuotationResponse;
import com.novax.leadora.infrastructure.persistence.entity.QuotationClosureLogEntity;
import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus;
import com.novax.leadora.infrastructure.persistence.repository.QuotationClosureLogRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CloseQuotationUseCase {

    private static final Set<QuotationStatus> NON_CLOSEABLE = Set.of(
            QuotationStatus.CONVERTED, QuotationStatus.CLOSED, QuotationStatus.EXPIRED
    );

    private final QuotationRepository quotationRepository;
    private final QuotationClosureLogRepository closureLogRepository;

    @Transactional
    public QuotationResponse execute(UUID quotationId, CloseQuotationRequest request) {
        QuotationEntity quotation = quotationRepository.findById(quotationId)
                .orElseThrow(() -> new RuntimeException("Quotation not found: " + quotationId));

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

        // POST-2: Log closure for audit (BR-37)
        QuotationClosureLogEntity log = QuotationClosureLogEntity.builder()
                .quotation(saved)
                .action("CLOSED")
                .reason(request.getReason())
                .notes(request.getNotes())
                .closedByName(request.getClosedByName())
                .closedByRole(request.getClosedByRole())
                .previousStatus(previousStatus)
                .newStatus("CLOSED")
                .build();
        closureLogRepository.save(log);

        return QuotationResponse.from(saved);
    }
}
