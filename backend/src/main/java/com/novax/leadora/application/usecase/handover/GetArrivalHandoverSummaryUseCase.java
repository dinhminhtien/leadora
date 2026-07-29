package com.novax.leadora.application.usecase.handover;

import com.novax.leadora.api.dto.response.ArrivalHandoverSummaryResponse;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.OpHandoverEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ReadinessStatus;
import com.novax.leadora.infrastructure.persistence.specification.OpHandoverSpecification;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/** UC-22.1 — Front Office desk summary: arrival-handover counts by readiness. */
@Service
@RequiredArgsConstructor
public class GetArrivalHandoverSummaryUseCase {

    private final EntityManager entityManager;
    private final CurrentUserProvider currentUserProvider;
    private final ArrivalDeskScope arrivalDeskScope;

    /**
     * Counts the arrivals the caller can see, bucketed by readiness.
     *
     * <p>One {@code GROUP BY} rather than the previous {@code findAll(spec)} into a list that was
     * then streamed four times: that loaded every submitted handover into memory on every render of
     * the desk, so it got slower and heavier for as long as the table kept growing.
     *
     * <p>The filters mirror the list's — <b>except readiness</b>, deliberately. The KPI cards are
     * how the user *applies* a readiness filter, so counting them under that same filter would zero
     * three of the four cards the moment one was clicked and leave no way back.
     */
    @Transactional(readOnly = true)
    public ArrivalHandoverSummaryResponse execute(String search, String arrivalDate,
                                                  String assignedFoUserId, boolean deskWide) {
        LocalDate arrivalFilter = HandoverListQuery.dateFilter(arrivalDate, "arrivalDate");
        UUID requestedAssignee = HandoverListQuery.uuidFilter(assignedFoUserId, "assignedFoUserId");

        UserEntity caller = currentUserProvider.resolve(null);
        UUID scopedTo = arrivalDeskScope.scopeFor(caller, deskWide);
        UUID assigneeFilter = arrivalDeskScope.canFilterByAssignee(caller) ? requestedAssignee : null;

        Specification<OpHandoverEntity> spec = OpHandoverSpecification.forFrontOffice(
                search, null, arrivalFilter, assigneeFilter, scopedTo);

        Map<ReadinessStatus, Long> counts = countByReadiness(spec);

        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        return ArrivalHandoverSummaryResponse.builder()
                .total((int) total)
                .pendingReview(counts.getOrDefault(ReadinessStatus.PENDING_REVIEW, 0L))
                .reviewed(counts.getOrDefault(ReadinessStatus.REVIEWED, 0L))
                .readyForArrival(counts.getOrDefault(ReadinessStatus.READY_FOR_ARRIVAL, 0L))
                .needClarification(counts.getOrDefault(ReadinessStatus.NEED_CLARIFICATION, 0L))
                .build();
    }

    /**
     * {@code SELECT readiness_status, count(*) ... GROUP BY readiness_status}, built from the same
     * {@link Specification} the list uses so the two can never disagree about what is visible.
     */
    private Map<ReadinessStatus, Long> countByReadiness(Specification<OpHandoverEntity> spec) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<OpHandoverEntity> root = query.from(OpHandoverEntity.class);

        Predicate predicate = spec.toPredicate(root, query, cb);
        if (predicate != null) {
            query.where(predicate);
        }

        Path<ReadinessStatus> readiness = root.get("readinessStatus");
        query.multiselect(readiness, cb.count(root)).groupBy(readiness);

        Map<ReadinessStatus, Long> counts = new EnumMap<>(ReadinessStatus.class);
        for (Tuple row : entityManager.createQuery(query).getResultList()) {
            ReadinessStatus status = row.get(0, ReadinessStatus.class);
            if (status != null) {
                counts.put(status, row.get(1, Long.class));
            }
        }
        return counts;
    }
}
