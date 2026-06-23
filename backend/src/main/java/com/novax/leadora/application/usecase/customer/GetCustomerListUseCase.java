package com.novax.leadora.application.usecase.customer;

import com.novax.leadora.api.dto.response.CustomerResponse;
import com.novax.leadora.infrastructure.persistence.entity.CustomerEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.CustomerStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.CustomerType;
import com.novax.leadora.infrastructure.persistence.repository.CustomerRepository;
import com.novax.leadora.infrastructure.persistence.specification.CustomerSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class GetCustomerListUseCase {

    private static final Set<String> ALLOWED_SORT_FIELDS =
            Set.of("createdAt", "fullName", "status", "customerType");

    private final CustomerRepository customerRepository;

    @Transactional(readOnly = true)
    public Page<CustomerResponse> execute(
            String search, String customerType, String status,
            String sortBy, String sortDir, int page, int size) {

        Specification<CustomerEntity> spec = Specification.allOf(
                CustomerSpecification.search(StringUtils.hasText(search) ? search.trim() : null),
                CustomerSpecification.hasType(parseEnum(CustomerType.class, customerType)),
                CustomerSpecification.hasStatus(parseEnum(CustomerStatus.class, status))
        );

        String sortField = ALLOWED_SORT_FIELDS.contains(sortBy) ? sortBy : "createdAt";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;

        return customerRepository
                .findAll(spec, PageRequest.of(page, size, Sort.by(direction, sortField)))
                .map(CustomerResponse::from);
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        if (!StringUtils.hasText(value)) return null;
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
