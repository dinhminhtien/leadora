package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.infrastructure.persistence.entity.DealStageHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface DealStageHistoryRepository extends JpaRepository<DealStageHistoryEntity, UUID> {

    /** A single deal's journey, oldest first. */
    List<DealStageHistoryEntity> findByDealIdOrderByChangedAtAsc(UUID dealId);

    /**
     * Every transition belonging to a set of deals, ordered so consecutive rows of the same deal sit
     * together in chronological order.
     *
     * <p>Returns {@code [dealId, fromStage, toStage, changedAt]}. Time-in-stage is the gap between
     * one row and the next for the same deal, which cannot be expressed as a portable JPQL
     * aggregate, so the pairing happens in Java over these four scalars.
     *
     * <p>Scoped by deal id rather than by {@code changed_at}: a deal created in the reporting period
     * may have moved stage after it, and a transition inside the period may belong to a deal from
     * long before. Selecting on the deal keeps each journey whole.
     */
    @Query("""
            SELECT h.dealId, h.fromStage, h.toStage, h.changedAt
            FROM DealStageHistoryEntity h
            WHERE h.dealId IN :dealIds
            ORDER BY h.dealId ASC, h.changedAt ASC
            """)
    List<Object[]> findTransitionsForDeals(@Param("dealIds") List<UUID> dealIds);

    /** Guard for the backfill script and for tests: is there any history at all yet? */
    boolean existsByDealId(UUID dealId);

    /** Transitions in a window, for trend queries that do not need whole journeys. */
    @Query("""
            SELECT h.toStage, count(h)
            FROM DealStageHistoryEntity h
            WHERE h.changedAt >= :start AND h.changedAt < :end
            GROUP BY h.toStage
            """)
    List<Object[]> countTransitionsInto(
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end);
}
