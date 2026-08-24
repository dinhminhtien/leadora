package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.infrastructure.persistence.entity.InteractTimelineEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InteractTimelineRepository
        extends JpaRepository<InteractTimelineEntity, UUID>, JpaSpecificationExecutor<InteractTimelineEntity> {
    List<InteractTimelineEntity> findByCustomer_CustomerIdOrderByOccurredAtDesc(UUID customerId);

    List<InteractTimelineEntity> findByLead_LeadIdOrderByOccurredAtDesc(UUID leadId);

    List<InteractTimelineEntity> findByDeal_DealIdOrderByOccurredAtDesc(UUID dealId);

    boolean existsByCustomer_CustomerIdAndUser_UserId(UUID customerId, UUID userId);

    /**
     * Logged actions per staff member, busiest first - the dashboard's activity leaderboard.
     *
     * <p>Counted in the database rather than by streaming {@code findAll()} into a map, which is
     * what the dashboard did: it pulled every interaction row the company had ever recorded into
     * the heap on each cache miss, to display five names.
     *
     * <p>Callers must still decide whether the leaderboard may be shown at all - it names
     * colleagues, so it is a Manager/Admin view. There is deliberately no scoped variant: a
     * leaderboard of one person is not a leaderboard.
     */
    @Query("""
            SELECT u.fullName, COUNT(i)
            FROM InteractTimelineEntity i JOIN i.user u
            WHERE u.fullName IS NOT NULL
            GROUP BY u.fullName
            ORDER BY COUNT(i) DESC
            """)
    List<Object[]> findTopUserActivity(Pageable pageable);
}
