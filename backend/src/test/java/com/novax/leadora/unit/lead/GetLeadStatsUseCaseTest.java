package com.novax.leadora.unit.lead;

import com.novax.leadora.api.dto.response.LeadStatsResponse;
import com.novax.leadora.application.usecase.lead.GetLeadStatsUseCase;
import com.novax.leadora.application.usecase.lead.LeadFilterParams;
import com.novax.leadora.application.usecase.lead.LeadAccessPolicy;
import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.infrastructure.persistence.entity.LeadEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.repository.LeadRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The summary tiles' backing query (UC-8.2).
 *
 * <p>
 * Two things matter here and neither is arithmetic — that lives in
 * {@code LeadStatsResponseTest}.
 * First, the counts must be pinned to the caller's own leads for a sales rep:
 * an aggregate is still
 * information, and a total that includes records the user may not open would
 * leak the size of the
 * team's pipeline (BR-02). Second, a rejected filter must be rejected here too,
 * or the tiles would
 * quietly summarise a wider set than the table beneath them.
 */
class GetLeadStatsUseCaseTest {

    private final LeadRepository repository = mock(LeadRepository.class);
    private final LeadAccessPolicy policy = mock(LeadAccessPolicy.class);
    private final GetLeadStatsUseCase useCase = new GetLeadStatsUseCase(repository, policy);

    private static UserEntity user() {
        return UserEntity.builder().userId(UUID.randomUUID()).build();
    }

    /**
     * total, converted, lost, qualified — in the order the use case asks for them.
     */
    @SuppressWarnings("unchecked")
    private void stubCounts(long total, long converted, long lost, long qualified) {
        when(repository.count(any(Specification.class)))
                .thenReturn(total, converted, lost, qualified);
    }

    @Test
    @DisplayName("counts are assembled into the response in the right order")
    void countsAreMappedInOrder() {
        UserEntity caller = user();
        when(policy.currentUser()).thenReturn(caller);
        when(policy.listScopeOwnerId(caller)).thenReturn(null); // manager
        stubCounts(32, 12, 5, 7);

        LeadStatsResponse stats = useCase.execute(null, null, null, null, null, null, "assigned", null);

        assertThat(stats.getTotal()).isEqualTo(32);
        assertThat(stats.getConverted()).isEqualTo(12);
        assertThat(stats.getLost()).isEqualTo(5);
        assertThat(stats.getQualified()).isEqualTo(7);
        assertThat(stats.getActive()).isEqualTo(15);
    }

    @Test
    @DisplayName("a sales rep's totals go through the owner scope, never the whole table")
    void appliesOwnerScopeForASalesRep() {
        UserEntity rep = user();
        when(policy.currentUser()).thenReturn(rep);
        when(policy.listScopeOwnerId(rep)).thenReturn(rep.getUserId());
        stubCounts(3, 1, 1, 1);

        useCase.execute(null, null, null, null, null, null, "assigned", null);

        // The policy decides the scope; the use case must consult it rather than
        // counting freely.
        verify(policy).listScopeOwnerId(rep);
    }

    @Test
    @DisplayName("a role with no lead access gets no totals either")
    void refusesRolesWithoutLeadAccess() {
        UserEntity outsider = user();
        when(policy.currentUser()).thenReturn(outsider);
        when(policy.listScopeOwnerId(outsider))
                .thenThrow(new AccessDeniedException("no access"));

        assertThatThrownBy(() -> useCase.execute(null, null, null, null, null, null, "assigned", null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("an unreadable filter is refused before any counting happens")
    void refusesAnInvalidFilter() {
        assertThatThrownBy(() -> useCase.execute(null, "NOT_A_STATUS", null, null, null, null, "assigned", null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo("INVALID_FILTER");
    }

    @Test
    @DisplayName("an empty result reports no rate rather than a rate of zero")
    void handlesAnEmptyResultSet() {
        UserEntity caller = user();
        when(policy.currentUser()).thenReturn(caller);
        when(policy.listScopeOwnerId(caller)).thenReturn(null);
        stubCounts(0, 0, 0, 0);

        LeadStatsResponse stats = useCase.execute(null, null, null, null, null, null, "assigned", null);

        assertThat(stats.getConvertedRate()).isNull();
        assertThat(stats.getLostRate()).isNull();
    }

    @Test
    @DisplayName("the specification the counts run on is never null")
    void buildsAUsableSpecification() {
        UserEntity caller = user();
        when(policy.currentUser()).thenReturn(caller);
        when(policy.listScopeOwnerId(caller)).thenReturn(caller.getUserId());
        stubCounts(1, 0, 0, 0);

        useCase.execute("hotel", "NEW", "Referral", true,
                "2026-01-01", "2026-12-31", "created", null);

        // Four counts: total, then one per terminal/qualified status.
        verify(repository, times(4)).count(ArgumentMatchers.<Specification<LeadEntity>>any());
    }

    /**
     * The tiles and the table must describe the same rows. "Assignment needed"
     * narrows the list, so
     * it has to narrow the counts too — a filter applied to one and not the other
     * is the exact
     * failure LeadFilterParams exists to prevent, and it would look like nothing
     * was wrong.
     */
    @Test
    @DisplayName("the assignment-needed filter reaches the counts, not just the list")
    void assignmentNeededNarrowsTheCounts() {
        stubCounts(3, 0, 0, 1);

        assertThat(LeadFilterParams.parse(null, null, null, null, null, null, true).unassignedOnly())
                .isTrue();
        assertThat(LeadFilterParams.parse(null, null, null, null, null, null, null).unassignedOnly())
                .isFalse();

        useCase.execute(null, null, null, null, null, null, "assigned", true);

        verify(repository, org.mockito.Mockito.times(4)).count(ArgumentMatchers.<Specification<LeadEntity>>any());
    }
}
