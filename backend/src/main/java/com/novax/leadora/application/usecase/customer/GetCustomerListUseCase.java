package com.novax.leadora.application.usecase.customer;

import com.novax.leadora.api.dto.response.CustomerResponse;
import com.novax.leadora.infrastructure.persistence.entity.enums.CustomerStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.CustomerType;
import com.novax.leadora.infrastructure.persistence.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class GetCustomerListUseCase {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("createdAt", "fullName", "status", "customerType");

    private final CustomerRepository customerRepository;

    @Transactional(readOnly = true)
    public Page<CustomerResponse> execute(
            String search, String customerType, String status,
            String sortBy, String sortDir, int page, int size) {

        String searchParam = StringUtils.hasText(search) ? search.trim() : "";

        CustomerType customerTypeParam = null;
        if (StringUtils.hasText(customerType)) {
            try {
                customerTypeParam = CustomerType.valueOf(customerType.toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }

        CustomerStatus statusParam = null;
        if (StringUtils.hasText(status)) {
            try {
                statusParam = CustomerStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }

        String sortField = ALLOWED_SORT_FIELDS.contains(sortBy) ? sortBy : "createdAt";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortField));

        return customerRepository.searchCustomersFiltered(searchParam, customerTypeParam, statusParam, pageable)
                .map(CustomerResponse::from);
    }
}
