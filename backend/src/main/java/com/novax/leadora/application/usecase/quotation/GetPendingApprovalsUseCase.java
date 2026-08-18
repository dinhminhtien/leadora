package com.novax.leadora.application.usecase.quotation;

import com.novax.leadora.api.dto.response.QuotationResponse;
import com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus;
import com.novax.leadora.infrastructure.persistence.repository.QuotationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GetPendingApprovalsUseCase {

    private final QuotationRepository quotationRepository;

    @Transactional(readOnly = true)
    public List<QuotationResponse> execute() {
        return quotationRepository.findByStatus(
                        QuotationStatus.PENDING_APPROVAL,
                        org.springframework.data.domain.PageRequest.of(0, 100, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"))
                )
                .stream()
                .map(QuotationResponse::from)
                .collect(Collectors.toList());
    }
}
