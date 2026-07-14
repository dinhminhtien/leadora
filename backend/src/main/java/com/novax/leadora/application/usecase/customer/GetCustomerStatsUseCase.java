package com.novax.leadora.application.usecase.customer;

import com.novax.leadora.infrastructure.persistence.entity.enums.CustomerStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.CustomerType;
import com.novax.leadora.infrastructure.persistence.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetCustomerStatsUseCase {

    private final CustomerRepository customerRepository;

    public record CustomerStats(long total, long active, long inactive, long individual, long corporate) {}

    @Transactional(readOnly = true)
    public CustomerStats execute() {
        long total      = customerRepository.count();
        long active     = customerRepository.countByStatus(CustomerStatus.ACTIVE);
        long inactive   = customerRepository.countByStatus(CustomerStatus.INACTIVE);
        long individual = customerRepository.countByCustomerType(CustomerType.INDIVIDUAL);
        long corporate  = customerRepository.countByCustomerType(CustomerType.CORPORATE);
        return new CustomerStats(total, active, inactive, individual, corporate);
    }
}
