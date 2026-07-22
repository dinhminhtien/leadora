package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.application.usecase.chat.dto.DealStatusAggregate;
import com.novax.leadora.application.usecase.chat.dto.RepDealStat;
import com.novax.leadora.infrastructure.persistence.entity.DealEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.DealStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface DealRepository extends JpaRepository<DealEntity, UUID>, JpaSpecificationExecutor<DealEntity> {
    List<DealEntity> findByAssignedUser_UserId(UUID assignedUserId);
    List<DealEntity> findByCustomer_CustomerId(UUID customerId);
    List<DealEntity> findByStatus(DealStatus status);

    // ── Performance report query (eliminates N+1 and filters at DB level) ──
    @EntityGraph(attributePaths = {"assignedUser"})
    @Query("""
            SELECT d FROM DealEntity d
            WHERE d.createdAt >= :startDate
              AND d.createdAt <= :endDate
            """)
    List<DealEntity> findByCreatedAtRange(
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate);

    // ── Chat-assistant snapshot ───────────────────────────────────────────────
    // A null :userId means "every deal" (Manager/Admin scope); a non-null value restricts to that
    // user's records — the BR-36 filter lives in SQL for both scopes.

    @Query("""
            SELECT new com.novax.leadora.application.usecase.chat.dto.DealStatusAggregate(
                       d.status, COUNT(d), SUM(d.expectedRevenue))
            FROM DealEntity d
            WHERE (:userId IS NULL OR d.assignedUser.userId = :userId)
            GROUP BY d.status
            """)
    List<DealStatusAggregate> aggregateByStatusForChat(@Param("userId") UUID userId);

    @EntityGraph(attributePaths = {"assignedUser"})
    @Query("""
            SELECT d FROM DealEntity d
            WHERE (:userId IS NULL OR d.assignedUser.userId = :userId)
            ORDER BY d.createdAt DESC
            """)
    List<DealEntity> findRecentForChat(@Param("userId") UUID userId, Pageable pageable);

    /**
     * Per-rep deal totals, one row per (rep, status). Grouping this way avoids CASE expressions,
     * whose result type HQL infers inconsistently; the caller pivots the small result in Java.
     */
    @Query("""
            SELECT new com.novax.leadora.application.usecase.chat.dto.RepDealStat(
                       u.fullName, d.status, COUNT(d), SUM(d.expectedRevenue))
            FROM DealEntity d JOIN d.assignedUser u
            GROUP BY u.fullName, d.status
            """)
    List<RepDealStat> statsPerAssignee();
}
