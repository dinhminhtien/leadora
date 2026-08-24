package com.novax.leadora.application.usecase.customer;

import com.novax.leadora.infrastructure.persistence.entity.CustomerEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.repository.CustomerRepository;
import com.novax.leadora.infrastructure.persistence.specification.CustomerSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SearchCustomersUseCase {

    private final CustomerRepository customerRepository;
    private final CustomerAccessPolicy customerAccessPolicy;

    @Transactional(readOnly = true)
    public List<CustomerSearchResult> execute(String query, int limit) {
        String search = StringUtils.hasText(query) ? query.trim() : "";
        int clampedLimit = Math.min(Math.max(limit, 1), 50);

        UserEntity currentUser = customerAccessPolicy.currentUser();
        UUID scopedOwnerId = customerAccessPolicy.listScopeOwnerId(currentUser);

        Specification<CustomerEntity> spec = Specification.allOf(
                CustomerSpecification.search(search),
                CustomerSpecification.isScopedToUser(scopedOwnerId)
        );

        return customerRepository.findAll(spec, PageRequest.of(0, clampedLimit, Sort.by(Sort.Direction.DESC, "createdAt")))
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
