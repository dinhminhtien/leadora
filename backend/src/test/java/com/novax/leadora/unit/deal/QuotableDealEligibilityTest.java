package com.novax.leadora.unit.deal;

import com.novax.leadora.application.usecase.deal.DealMapper;
import com.novax.leadora.infrastructure.persistence.entity.DealEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.DealStatus;
import com.novax.leadora.infrastructure.persistence.specification.DealSpecification;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The eligibility rule behind the Create Quotation deal picker: <b>a deal can be quoted if
 * and only if it is still active.</b>
 *
 * <p>Two failures this pins down, both of which are silent:
 * <ul>
 *   <li>comparing against the display string {@code "active"} instead of the persisted enum
 *       {@link DealStatus#OPEN}. {@code DealMapper} renders OPEN as "active" on the wire, so
 *       the two are easy to confuse — and a predicate built from the wrong one matches no
 *       rows at all, emptying the picker rather than throwing;</li>
 *   <li>an extra condition creeping back in and removing an active deal the rep needs. The
 *       spec must touch {@code status} and nothing else.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QuotableDealEligibilityTest {

    @Mock
    private Root<DealEntity> root;

    @Mock
    private CriteriaQuery<?> query;

    @Mock
    private CriteriaBuilder cb;

    /**
     * Runs the specification against a mock Criteria API and hands back the status path it
     * read, so the caller can assert what was compared to it.
     *
     * <p>The result type is reported as {@code Long} — the shape of the count query Spring
     * issues for pagination — because that branch skips the eager {@code fetch} joins and
     * leaves only the predicates under test.
     */
    @SuppressWarnings("unchecked")
    private Path<Object> applySpecification() {
        Path<Object> statusPath = mock(Path.class);
        Predicate predicate = mock(Predicate.class);

        when(query.getResultType()).thenAnswer(invocation -> Long.class);
        when(root.get("status")).thenReturn(statusPath);
        when(cb.and(any(Predicate[].class))).thenReturn(predicate);
        when(cb.and(any(Predicate.class), any(Predicate.class))).thenReturn(predicate);
        when(cb.equal(any(), any())).thenReturn(predicate);

        // Unscoped (MANAGER/ADMIN) with no search: the only predicate left is eligibility.
        DealSpecification.quotable(null, true, null).toPredicate(root, query, cb);

        return statusPath;
    }

    @Test
    @DisplayName("Eligibility compares status to the persisted enum OPEN, never the wire value")
    void comparesAgainstTheOpenEnumConstant() {
        Path<Object> statusPath = applySpecification();

        verify(cb).equal(statusPath, DealStatus.OPEN);
    }

    @Test
    @DisplayName("ACTIVE on the wire is OPEN in the enum — WON and LOST are the closed ones")
    void wireValueForOpenIsActive() {
        DealMapper mapper = new DealMapper();

        assertThat(mapper.mapStatusToString(DealStatus.OPEN)).isEqualTo("active");
        assertThat(mapper.mapStatusToString(DealStatus.WON)).isEqualTo("won");
        assertThat(mapper.mapStatusToString(DealStatus.LOST)).isEqualTo("lost");

        // The picker's status filter round-trips: what the client calls "active" is what
        // the specification matches on.
        assertThat(mapper.mapStatusToEnum("active")).isEqualTo(DealStatus.OPEN);
    }

    @Test
    @DisplayName("Nothing but status narrows the result — no condition may drop an active deal")
    void statusIsTheOnlyEligibilityCondition() {
        applySpecification();

        // A linked-customer check used to live here. `DealEntity.customer` is mapped
        // non-nullable, so it could never exclude anything legitimate — only an active deal
        // the rep was entitled to quote.
        verify(root, never()).get("customer");
        verify(root, never()).get("expectedCloseDate");
        verify(root, never()).get("pipelineStage");
    }
}
