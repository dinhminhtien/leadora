package com.novax.leadora.application.usecase.customer;

import com.novax.leadora.infrastructure.persistence.entity.CustomerEntity;
import com.novax.leadora.infrastructure.persistence.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SearchCustomersUseCase {

    private final CustomerRepository customerRepository;

    @Transactional(readOnly = true)
    public List<CustomerSearchResult> execute(String query, int limit) {
        String search = StringUtils.hasText(query) ? query.trim() : "";
        int clampedLimit = Math.min(Math.max(limit, 1), 50);

        return customerRepository.searchCustomers(search, PageRequest.of(0, clampedLimit))
                .getContent()
                .stream()
                .map(CustomerSearchResult::from)
                .toList();
    }

    public record CustomerSearchResult(
            String id,
            String name,
            String email,
            String phone,
            String company
    ) {
        public static CustomerSearchResult from(CustomerEntity entity) {
            return new CustomerSearchResult(
                    entity.getCustomerId().toString(),
                    entity.getFullName(),
                    entity.getEmail(),
                    entity.getPhone(),
                    entity.getCompanyName()
            );
        }
    }
}
