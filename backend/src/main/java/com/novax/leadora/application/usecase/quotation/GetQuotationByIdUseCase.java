package com.novax.leadora.application.usecase.quotation;

import com.novax.leadora.api.dto.response.QuotationResponse;
import com.novax.leadora.infrastructure.persistence.entity.QuotationDetailEntity;
import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import com.novax.leadora.infrastructure.persistence.repository.QuotationDetailRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetQuotationByIdUseCase {

    private final QuotationRepository quotationRepository;
    private final QuotationDetailRepository quotationDetailRepository;
    private final QuotationAccessPolicy quotationAccessPolicy;

    @Transactional(readOnly = true)
    public QuotationResponse execute(UUID quotationId) {
        QuotationEntity entity = quotationRepository.findById(quotationId)
                .orElseThrow(() -> new RuntimeException("Quotation not found: " + quotationId));

        // A Sales Staff may only open quotations they created; MANAGER/ADMIN per policy.
        quotationAccessPolicy.assertCanView(quotationAccessPolicy.currentUser(), entity);

        List<QuotationDetailEntity> details = quotationDetailRepository.findByQuotation_QuotationId(quotationId);
        return QuotationResponse.fromWithDetails(entity, details);
    }
}
