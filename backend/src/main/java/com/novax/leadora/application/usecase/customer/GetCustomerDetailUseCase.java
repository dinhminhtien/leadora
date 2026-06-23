package com.novax.leadora.application.usecase.customer;

import com.novax.leadora.api.dto.response.CustomerResponse;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetCustomerDetailUseCase {

    private final CustomerRepository customerRepository;

    @Transactional(readOnly = true)
    public CustomerResponse execute(UUID customerId) {
        return customerRepository.findByIdWithUsers(customerId)
                .map(CustomerResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));
    }
}
