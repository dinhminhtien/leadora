package com.novax.leadora.application.usecase.deal;

import com.novax.leadora.api.dto.response.DealResponse;
import com.novax.leadora.infrastructure.persistence.repository.DealRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GetDealListUseCase {

    private final DealRepository dealRepository;
    private final DealMapper dealMapper;

    @Transactional(readOnly = true)
    public List<DealResponse> execute() {
        return dealRepository.findAll().stream()
                .map(dealMapper::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DealResponse> execute(UUID customerId) {
        return dealRepository.findByCustomer_CustomerId(customerId).stream()
                .map(dealMapper::mapToResponse)
                .collect(Collectors.toList());
    }
}
