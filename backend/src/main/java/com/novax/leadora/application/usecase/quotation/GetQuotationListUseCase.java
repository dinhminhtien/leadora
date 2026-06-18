package com.novax.leadora.application.usecase.quotation;

import com.novax.leadora.api.dto.response.QuotationResponse;
import com.novax.leadora.infrastructure.persistence.repository.QuotationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GetQuotationListUseCase {

    private final QuotationRepository quotationRepository;

    @Transactional(readOnly = true)
    public List<QuotationResponse> execute() {
        return quotationRepository.findAll()
                .stream()
                .map(QuotationResponse::from)
                .collect(Collectors.toList());
    }
}