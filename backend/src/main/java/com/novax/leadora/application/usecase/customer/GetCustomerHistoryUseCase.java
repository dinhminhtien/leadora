package com.novax.leadora.application.usecase.customer;

import com.novax.leadora.api.dto.response.CustomerHistoryItem;
import com.novax.leadora.api.dto.response.CustomerHistoryProjection;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetCustomerHistoryUseCase {

    private final CustomerRepository customerRepository;

    @Transactional(readOnly = true)
    public List<CustomerHistoryItem> execute(UUID customerId) {
        if (!customerRepository.existsById(customerId)) {
            throw new ResourceNotFoundException("Customer", customerId);
        }

        List<CustomerHistoryProjection> projections = customerRepository.findCustomerHistory(customerId);

        return projections.stream()
                .map(p -> new CustomerHistoryItem(
                        p.getType(),
                        p.getId(),
                        p.getTitle(),
                        p.getStatus(),
                        p.getStage(),
                        p.getAmount(),
                        p.getCheckIn(),
                        p.getCheckOut(),
                        p.getExpectedClose(),
                        p.getCreatedAt(),
                        p.getNotes()
                ))
                .toList();
    }
}
