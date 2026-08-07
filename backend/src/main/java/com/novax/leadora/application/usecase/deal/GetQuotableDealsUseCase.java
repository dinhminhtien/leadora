package com.novax.leadora.application.usecase.deal;

import com.novax.leadora.api.dto.response.DealResponse;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.repository.DealRepository;
import com.novax.leadora.infrastructure.persistence.specification.DealSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Search-first lookup of the deals a quotation can be raised against (UC-14.1).
 *
 * <p>Exists so the quotation form's deal picker no longer has to download every deal the
 * user can see and sift it in the client. Eligibility is decided by
 * {@link DealSpecification#quotable} — the deal must still be active — which also means web
 * and mobile can no longer disagree about which deals are quotable.
 */
@Service
@RequiredArgsConstructor
public class GetQuotableDealsUseCase {

    /** Upper bound on {@code size}, so one request cannot ask for the whole table. */
    private static final int MAX_PAGE_SIZE = 50;

    /**
     * Soonest expected close first, so the deals under time pressure surface at the top of
     * an unsearched picker. {@code createdAt} breaks ties, keeping the order stable across
     * pages — without a tiebreaker two deals sharing a close date can swap between page
     * requests and appear twice, or not at all.
     *
     * <p><b>Do not add {@code .nullsLast()} here.</b> Spring Data rejects
     * {@code Sort.NullHandling} on the Criteria path that {@code Specification} queries use
     * — it throws rather than degrading, so every request to this endpoint came back 500.
     * It is also unnecessary: PostgreSQL already orders {@code NULLS LAST} for {@code ASC},
     * so undated deals — which carry no urgency signal — sink to the bottom regardless.
     */
    private static final Sort SORT = Sort.by(
            Sort.Order.asc("expectedCloseDate"),
            Sort.Order.desc("createdAt"));

    private final DealRepository dealRepository;
    private final DealMapper dealMapper;
    private final DealAccessPolicy dealAccessPolicy;

    @Transactional(readOnly = true)
    public Page<DealResponse> execute(String search, int page, int size) {
        UserEntity currentUser = dealAccessPolicy.currentUser();
        UUID scopedUserId = dealAccessPolicy.listScopeOwnerId(currentUser);
        boolean unscoped = (scopedUserId == null);

        var spec = DealSpecification.quotable(search, unscoped, scopedUserId);

        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                SORT);

        return dealRepository.findAll(spec, pageable).map(dealMapper::mapToResponse);
    }
}
