package com.novax.leadora.application.usecase.customer;

import com.novax.leadora.infrastructure.persistence.entity.CustomerEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.CustomerStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.CustomerType;
import com.novax.leadora.infrastructure.persistence.repository.CustomerRepository;
import com.novax.leadora.infrastructure.persistence.specification.CustomerSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetCustomerStatsUseCase {

    private final CustomerRepository customerRepository;
    private final CustomerAccessPolicy customerAccessPolicy;

    public record CustomerStats(long total, long active, long inactive, long individual, long corporate) {}

    @Transactional(readOnly = true)
    public CustomerStats execute() {
        UserEntity currentUser = customerAccessPolicy.currentUser();
        UUID scopedOwnerId = customerAccessPolicy.listScopeOwnerId(currentUser);
        Specification<CustomerEntity> base = CustomerSpecification.isScopedToUser(scopedOwnerId);

        long total      = customerRepository.count(base);
        long active     = customerRepository.count(Specification.allOf(base, CustomerSpecification.hasStatus(CustomerStatus.ACTIVE)));
        long inactive   = customerRepository.count(Specification.allOf(base, CustomerSpecification.hasStatus(CustomerStatus.INACTIVE)));
        long individual = customerRepository.count(Specification.allOf(base, CustomerSpecification.hasType(CustomerType.INDIVIDUAL)));
        long corporate  = customerRepository.count(Specification.allOf(base, CustomerSpecification.hasType(CustomerType.CORPORATE)));
        return new CustomerStats(total, active, inactive, individual, corporate);
    }
}
