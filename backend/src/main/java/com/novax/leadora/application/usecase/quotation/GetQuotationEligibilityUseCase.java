package com.novax.leadora.application.usecase.quotation;

import com.novax.leadora.api.dto.response.QuotationEligibilityResponse;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import com.novax.leadora.infrastructure.persistence.repository.QuotationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Answers "which actions may I offer on this quotation, and what do I say about the rest".
 *
 * <p>Read-only and scoped exactly like viewing the quotation, so it discloses nothing a caller
 * could not already see. It changes no state — the same policy object decides here and on the
 * write paths, so asking in advance can never differ from finding out by trying.
 */
@Service
@RequiredArgsConstructor
public class GetQuotationEligibilityUseCase {

    private final QuotationRepository quotationRepository;
    private final QuotationAccessPolicy quotationAccessPolicy;
    private final QuotationActionPolicy quotationActionPolicy;

    @Transactional(readOnly = true)
    public QuotationEligibilityResponse execute(UUID quotationId) {
        QuotationEntity quotation = quotationRepository.findById(quotationId)
                .orElseThrow(() -> new ResourceNotFoundException("Quotation", quotationId));

        quotationAccessPolicy.assertCanView(quotationAccessPolicy.currentUser(), quotation);

        return QuotationEligibilityResponse.of(
                quotation.getQuotationId(),
                quotation.getStatus() != null ? quotation.getStatus().name() : null,
                quotationActionPolicy.canSend(quotation),
                quotationActionPolicy.canSendByEmail(quotation),
                quotationActionPolicy.canSendByPhone(quotation),
                quotationActionPolicy.canConvert(quotation));
    }
}
