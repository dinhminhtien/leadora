package com.novax.leadora.application.usecase.lead;

import com.novax.leadora.api.dto.response.LeadStatsResponse;
import com.novax.leadora.infrastructure.persistence.entity.LeadEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.LeadStatus;
import com.novax.leadora.infrastructure.persistence.repository.LeadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * UC-8.2 — the counts behind the summary tiles above the lead list.
 *
 * <p>Counted in the database over the whole filtered set. The tiles were previously derived in the
 * browser from the page it happened to be showing, which made "Qualified" mean "qualified among
 * these ten rows": the number changed when the user paged or re-sorted, while the data had not.
 *
 * <p>Two properties this deliberately shares with {@link GetLeadListUseCase}, because a mismatch in
 * either would be invisible and wrong:
 * <ul>
 *   <li><b>the same filters</b>, via {@link LeadFilterParams}, so the tiles describe exactly the
 *       rows shown beneath them;</li>
 *   <li><b>the same owner scope</b> (BR-02), via {@link LeadAccessPolicy#listScopeOwnerId}. Counting
 *       without it would leak team-wide totals to a sales rep who is not allowed to see the
 *       underlying records — an aggregate is still information.</li>
 * </ul>
 *
 * <p>Four {@code COUNT} queries rather than one {@code GROUP BY}: each is an index-friendly count
 * over the same predicate, they run inside one read-only transaction, and the shape stays readable.
 * Worth revisiting only if the lead table grows far beyond CRM scale.
 */
@Service
@RequiredArgsConstructor
public class GetLeadStatsUseCase {

    private final LeadRepository leadRepository;
    private final LeadAccessPolicy leadAccessPolicy;

    @Transactional(readOnly = true)
    public LeadStatsResponse execute(String search, String status, String source, Boolean isCorporate,
                                     String dateFrom, String dateTo, String scope,
                                     Boolean unassigned) {
        LeadFilterParams filters =
                LeadFilterParams.parse(search, status, source, isCorporate, dateFrom, dateTo, unassigned);

        UserEntity currentUser = leadAccessPolicy.currentUser();
        UUID ownerId = leadAccessPolicy.listScopeOwnerId(currentUser);
        boolean unscoped = (ownerId == null);
        boolean createdByMe = "created".equalsIgnoreCase(scope);

        Specification<LeadEntity> base = filters.toSpecification(unscoped, ownerId, createdByMe);

        return LeadStatsResponse.of(
                leadRepository.count(base),
                leadRepository.count(withStatus(base, LeadStatus.CONVERTED)),
                leadRepository.count(withStatus(base, LeadStatus.LOST)),
                leadRepository.count(withStatus(base, LeadStatus.QUALIFIED)));
    }

    /**
     * Narrows the filtered set to one status.
     *
     * <p>Note this is added on top of the caller's own status filter rather than replacing it. That
     * is intentional: if the user has filtered the list to {@code LOST}, the tiles should report on
     * that same view — total 12, lost 12, converted 0 — not quietly widen the query to describe
     * leads the table is not showing.
     */
    private static Specification<LeadEntity> withStatus(Specification<LeadEntity> base,
                                                        LeadStatus status) {
        return base.and((root, query, cb) -> cb.equal(root.get("status"), status));
    }
}
