package com.novax.leadora.application.usecase.deal;

import com.novax.leadora.api.dto.response.DealStatsResponse;
import com.novax.leadora.infrastructure.persistence.entity.DealEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.DealPipelineStage;
import com.novax.leadora.infrastructure.persistence.entity.enums.DealStatus;
import com.novax.leadora.infrastructure.persistence.repository.DealRepository;
import com.novax.leadora.infrastructure.persistence.specification.DealSpecification;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The counts and totals behind the tiles above the Deals list — mirrors {@code
 * GetLeadStatsUseCase}: counted in the database over the whole filtered set, over the same
 * filters and the same owner scope as {@link GetDealListUseCase}, so the tiles can never
 * describe a different set of deals than the table beneath them.
 *
 * <p>Counts go through {@code DealRepository.count(Specification)}; sums do not have a
 * {@code JpaSpecificationExecutor} equivalent, so they are built directly off the same
 * {@link DealSpecification#filter} predicate via the entity manager — reusing the exact
 * predicate rather than re-deriving an equivalent one in JPQL is what keeps this in sync with
 * the list if the filtering logic ever changes.
 */
@Service
@RequiredArgsConstructor
public class GetDealStatsUseCase {

    private final DealRepository dealRepository;
    private final DealAccessPolicy dealAccessPolicy;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public DealStatsResponse execute(String search, UUID ownerId, DealPipelineStage stage) {
        UserEntity currentUser = dealAccessPolicy.currentUser();
        UUID scopedUserId = dealAccessPolicy.listScopeOwnerId(currentUser);
        boolean unscoped = (scopedUserId == null);

        Specification<DealEntity> base =
                DealSpecification.filter(search, ownerId, stage, null, unscoped, scopedUserId);
        Specification<DealEntity> openSpec = withStatus(base, DealStatus.OPEN);
        Specification<DealEntity> wonSpec = withStatus(base, DealStatus.WON);
        Specification<DealEntity> lostSpec = withStatus(base, DealStatus.LOST);

        long activeCount = dealRepository.count(openSpec);
        long wonCount = dealRepository.count(wonSpec);
        long lostCount = dealRepository.count(lostSpec);

        BigDecimal activeValue = sumExpectedRevenue(openSpec);
        BigDecimal wonValue = sumExpectedRevenue(wonSpec);

        return DealStatsResponse.of(activeCount, activeValue, wonCount, wonValue, lostCount);
    }

    private static Specification<DealEntity> withStatus(Specification<DealEntity> base, DealStatus status) {
        return base.and((root, query, cb) -> cb.equal(root.get("status"), status));
    }

    private BigDecimal sumExpectedRevenue(Specification<DealEntity> spec) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<BigDecimal> query = cb.createQuery(BigDecimal.class);
        Root<DealEntity> root = query.from(DealEntity.class);
        query.select(cb.sum(root.get("expectedRevenue")));

        Predicate predicate = spec.toPredicate(root, query, cb);
        if (predicate != null) {
            query.where(predicate);
        }

        BigDecimal result = entityManager.createQuery(query).getSingleResult();
        return result != null ? result : BigDecimal.ZERO;
    }
}
