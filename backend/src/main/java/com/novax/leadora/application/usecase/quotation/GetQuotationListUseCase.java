package com.novax.leadora.application.usecase.quotation;

import com.novax.leadora.api.dto.response.QuotationResponse;
import com.novax.leadora.infrastructure.persistence.entity.QuotationDetailEntity;
import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.repository.QuotationDetailRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GetQuotationListUseCase {

    private final QuotationRepository quotationRepository;
    private final QuotationDetailRepository quotationDetailRepository;
    private final QuotationAccessPolicy quotationAccessPolicy;

    @Transactional(readOnly = true)
    public List<QuotationResponse> execute() {
        // Owner-scoping: SALES is restricted to quotations they created; MANAGER/ADMIN see all.
        UserEntity currentUser = quotationAccessPolicy.currentUser();
        UUID ownerId = quotationAccessPolicy.listScopeOwnerId(currentUser);

        List<QuotationEntity> quotations = quotationRepository.findAll();
        if (ownerId != null) {
            quotations = quotations.stream()
                    .filter(q -> q.getCreatedBy() != null && ownerId.equals(q.getCreatedBy().getUserId()))
                    .toList();
        }

        List<UUID> quotationIds = quotations.stream().map(q -> q.getQuotationId()).toList();
        Map<UUID, List<QuotationDetailEntity>> detailsByQuotation = quotationDetailRepository
                .findByQuotation_QuotationIdIn(quotationIds).stream()
                .collect(Collectors.groupingBy(d -> d.getQuotation().getQuotationId()));

        return quotations.stream()
                .map(q -> QuotationResponse.fromWithDetails(q,
                        detailsByQuotation.getOrDefault(q.getQuotationId(), List.of())))
                .collect(Collectors.toList());
    }
}
