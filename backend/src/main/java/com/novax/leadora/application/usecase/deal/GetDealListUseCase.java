package com.novax.leadora.application.usecase.deal;

import com.novax.leadora.api.dto.response.DealResponse;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.repository.DealRepository;
import com.novax.leadora.infrastructure.persistence.specification.DealSpecification;
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
    private final DealAccessPolicy dealAccessPolicy;

    @Transactional(readOnly = true)
    public List<DealResponse> execute() {
        return execute(null, null);
    }

    @Transactional(readOnly = true)
    public List<DealResponse> execute(String search, UUID ownerId) {
        UserEntity currentUser = dealAccessPolicy.currentUser();
        UUID scopedUserId = dealAccessPolicy.listScopeOwnerId(currentUser);
        boolean unscoped = (scopedUserId == null);

        var spec = DealSpecification.filter(
                search,
                ownerId,
                unscoped,
                scopedUserId
        );

        return dealRepository.findAll(spec).stream()
                .map(dealMapper::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DealResponse> execute(UUID customerId) {
        UserEntity currentUser = dealAccessPolicy.currentUser();
        UUID scopedUserId = dealAccessPolicy.listScopeOwnerId(currentUser);
        boolean unscoped = (scopedUserId == null);

        var spec = DealSpecification.filter(
                null,
                null,
                unscoped,
                scopedUserId
        );

        org.springframework.data.jpa.domain.Specification<com.novax.leadora.infrastructure.persistence.entity.DealEntity> customerSpec = 
            (root, query, cb) -> cb.equal(root.get("customer").get("customerId"), customerId);

        return dealRepository.findAll(spec.and(customerSpec)).stream()
                .map(dealMapper::mapToResponse)
                .collect(Collectors.toList());
    }
}
