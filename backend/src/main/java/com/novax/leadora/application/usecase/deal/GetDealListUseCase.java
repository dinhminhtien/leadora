package com.novax.leadora.application.usecase.deal;

import com.novax.leadora.api.dto.response.DealResponse;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.DealPipelineStage;
import com.novax.leadora.infrastructure.persistence.entity.enums.DealStatus;
import com.novax.leadora.infrastructure.persistence.repository.DealRepository;
import com.novax.leadora.infrastructure.persistence.specification.DealSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.novax.leadora.infrastructure.persistence.entity.DealEntity;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GetDealListUseCase {

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private final DealRepository dealRepository;
    private final DealMapper dealMapper;
    private final DealAccessPolicy dealAccessPolicy;

    @Transactional(readOnly = true)
    public List<DealResponse> execute() {
        return executeAll(null, null, null, null);
    }

    /**
     * The Deals table, one page at a time (mirrors {@code GetLeadListUseCase}).
     *
     * <p>Newest first, same as the unpaged {@link #executeAll} always was — the screen has no
     * column-sort feature yet, so there is nothing to expose a {@code sortBy} param for.
     */
    @Transactional(readOnly = true)
    public Page<DealResponse> execute(String search, UUID ownerId, DealPipelineStage stage,
                                      DealStatus status, int page, int size) {
        Specification<DealEntity> spec = buildSpec(search, ownerId, stage, status);
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1), DEFAULT_SORT);
        return dealRepository.findAll(spec, pageable).map(dealMapper::mapToResponse);
    }

    /**
     * Every deal matching the filters, unpaged — backs the CSV export, which has to describe the
     * whole filtered set rather than whatever page happens to be on screen (mirrors why
     * {@code GetLeadStatsUseCase} exists as its own unpaged query).
     */
    @Transactional(readOnly = true)
    public List<DealResponse> executeAll(String search, UUID ownerId, DealPipelineStage stage,
                                         DealStatus status) {
        Specification<DealEntity> spec = buildSpec(search, ownerId, stage, status);
        return dealRepository.findAll(spec, DEFAULT_SORT).stream()
                .map(dealMapper::mapToResponse)
                .collect(Collectors.toList());
    }

    private Specification<DealEntity> buildSpec(String search, UUID ownerId, DealPipelineStage stage,
                                                DealStatus status) {
        UserEntity currentUser = dealAccessPolicy.currentUser();
        UUID scopedUserId = dealAccessPolicy.listScopeOwnerId(currentUser);
        boolean unscoped = (scopedUserId == null);

        return DealSpecification.filter(search, ownerId, stage, status, unscoped, scopedUserId);
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
 
        return dealRepository.findAll(spec.and(customerSpec), Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(dealMapper::mapToResponse)
                .collect(Collectors.toList());
    }
}
