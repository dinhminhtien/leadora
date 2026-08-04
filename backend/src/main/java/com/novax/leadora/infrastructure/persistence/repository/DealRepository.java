package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.application.usecase.chat.dto.RepDealStat;
import com.novax.leadora.infrastructure.persistence.entity.DealEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.DealStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface DealRepository extends JpaRepository<DealEntity, UUID>, JpaSpecificationExecutor<DealEntity> {
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM DealEntity d WHERE d.dealId = :id")
    java.util.Optional<DealEntity> findByIdForUpdate(@Param("id") UUID id);
    List<DealEntity> findByAssignedUser_UserId(UUID assignedUserId);
    List<DealEntity> findByCustomer_CustomerId(UUID customerId);
    List<DealEntity> findByStatus(DealStatus status);

    // ── UC-23.1 / UC-23.4 report aggregates ───────────────────────────────────
    // Ranges are half-open: [start, end) — see ReportRange.

    // ── UC-23.1: the whole report in two round trips ──────────────────────────
    //
    // Both databases this application talks to are remote (Supabase Postgres, Upstash Redis), so
    // every statement pays an internet round trip. Split across ten small aggregates the report was
    // ten round trips deep: at these table sizes — tens to a couple of hundred rows — the query
    // work is free and the latency is entirely in the trips. These two native UNION ALL statements
    // return the same numbers in two.
    //
    // Native rather than JPQL because the branches select different entities into one result shape,
    // which HQL set operations handle awkwardly, and because plain SQL could be run against the
    // real database to confirm the numbers before it was wired in.
    //
    // Two time axes on purpose. Acquisition ("how many deals did we open") belongs to created_at;
    // outcome ("how many did we win") belongs to closed_at. Measuring outcomes on created_at drops
    // a deal opened in May and won in July, and dilutes the rate with deals still in flight.

    /**
     * Every headline aggregate UC-23.1 needs, as {@code [kind, bucket, count, amount]}.
     *
     * <p>{@code kind} names the population — LEAD, DEAL_OPENED, DEAL_CLOSED, QUOTATION, BOOKING,
     * REVENUE — and {@code bucket} is that population's status. Living on DealRepository is a
     * compromise: the statement spans five tables and belongs to no single one.
     */
    @Query(value = """
            SELECT 'LEAD' AS kind, l.status::text AS bucket, count(*) AS cnt, 0::numeric AS amount
              FROM leads l
             WHERE l.created_at >= :start AND l.created_at < :end
             GROUP BY l.status
            UNION ALL
            SELECT 'DEAL_OPENED', d.status::text, count(*), COALESCE(sum(d.expected_revenue), 0)
              FROM deals d
             WHERE d.created_at >= :start AND d.created_at < :end
             GROUP BY d.status
            UNION ALL
            SELECT 'DEAL_CLOSED', d.status::text, count(*), COALESCE(sum(d.expected_revenue), 0)
              FROM deals d
             WHERE d.closed_at >= :start AND d.closed_at < :end
             GROUP BY d.status
            UNION ALL
            SELECT 'QUOTATION', q.status::text, count(*), 0
              FROM quotations q
             WHERE q.created_at >= :start AND q.created_at < :end
             GROUP BY q.status
            UNION ALL
            SELECT 'BOOKING', b.status::text, count(*), 0
              FROM bookings b
             WHERE b.created_at >= :start AND b.created_at < :end
             GROUP BY b.status
            UNION ALL
            SELECT 'REVENUE', 'PAID', count(*), COALESCE(sum(p.amount), 0)
              FROM payments p
             WHERE p.status = 'PAID'
               AND ((p.paid_at IS NOT NULL AND p.paid_at >= :start AND p.paid_at < :end) OR (p.paid_at IS NULL AND p.created_at >= :start AND p.created_at < :end))
            """, nativeQuery = true)
    List<Object[]> salesPerformanceAggregates(
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end);

    /**
     * The per-rep breakdown for UC-23.1, as {@code [kind, ownerId, ownerName, count, amount]}.
     *
     * <p>LEFT JOINs throughout so records with no assignee survive as a null-owner group; dropping
     * them is what stopped the per-rep table adding up to the headline totals.
     */
    @Query(value = """
            SELECT 'LEADS' AS kind, u.user_id AS owner_id, u.full_name AS owner_name,
                   count(*) AS cnt, 0::numeric AS amount
              FROM leads l LEFT JOIN users u ON u.user_id = l.assigned_user_id
             WHERE l.created_at >= :start AND l.created_at < :end
             GROUP BY u.user_id, u.full_name
            UNION ALL
            SELECT 'DEALS_WON', u.user_id, u.full_name,
                   count(*), COALESCE(sum(d.expected_revenue), 0)
              FROM deals d LEFT JOIN users u ON u.user_id = d.assigned_user_id
             WHERE d.closed_at >= :start AND d.closed_at < :end AND d.status = 'WON'
             GROUP BY u.user_id, u.full_name
            UNION ALL
            SELECT 'BOOKINGS', u.user_id, u.full_name, count(*), 0
              FROM bookings b LEFT JOIN users u ON u.user_id = b.assigned_user_id
             WHERE b.created_at >= :start AND b.created_at < :end
               AND b.status IN ('CONFIRMED', 'CHECKED_IN', 'CHECKED_OUT')
             GROUP BY u.user_id, u.full_name
            UNION ALL
            SELECT 'REVENUE', u.user_id, u.full_name, count(*), COALESCE(sum(p.amount), 0)
              FROM payments p
                   LEFT JOIN bookings b ON b.booking_id = p.booking_id
                   LEFT JOIN users u ON u.user_id = b.assigned_user_id
             WHERE p.status = 'PAID'
               AND ((p.paid_at IS NOT NULL AND p.paid_at >= :start AND p.paid_at < :end) OR (p.paid_at IS NULL AND p.created_at >= :start AND p.created_at < :end))
             GROUP BY u.user_id, u.full_name
            """, nativeQuery = true)
    List<Object[]> salesPerformanceByOwner(
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end);

    /** {@code [status, count, sumExpectedRevenue]} over deals *opened* in the range. */
    @Query("""
            SELECT d.status, count(d), sum(d.expectedRevenue) FROM DealEntity d
            WHERE d.createdAt >= :start
              AND d.createdAt < :end
            GROUP BY d.status
            """)
    List<Object[]> aggregateByStatus(
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end);

    /** {@code [status, count, sumExpectedRevenue]} over deals *closed* in the range. */
    @Query("""
            SELECT d.status, count(d), sum(d.expectedRevenue) FROM DealEntity d
            WHERE d.closedAt >= :start
              AND d.closedAt < :end
            GROUP BY d.status
            """)
    List<Object[]> aggregateByStatusClosedInRange(
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end);

    /** {@code [ownerId, ownerName, count, sumExpectedRevenue]} for deals closed in the range. */
    @Query("""
            SELECT u.userId, u.fullName, count(d), sum(d.expectedRevenue)
            FROM DealEntity d LEFT JOIN d.assignedUser u
            WHERE d.closedAt >= :start
              AND d.closedAt < :end
              AND d.status = :status
            GROUP BY u.userId, u.fullName
            """)
    List<Object[]> aggregateByOwnerForStatusClosedInRange(
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end,
            @Param("status") DealStatus status);

    /**
     * {@code [dealId, pipelineStage, expectedRevenue, createdAt, closedAt]} — one lightweight tuple
     * per deal for UC-23.4, over the cohort of deals opened in the range.
     *
     * <p>The deal ids come back so the report can fetch each deal's stage history in one follow-up
     * query. Aging cannot be averaged in portable JPQL (there is no date-difference aggregate), so
     * the rows are paired up in Java — but this still selects five scalars rather than hydrating a
     * managed entity plus its {@code assignedUser} join, which is where the cost was.
     */
    @Query("""
            SELECT d.dealId, d.pipelineStage, d.expectedRevenue, d.createdAt, d.closedAt
            FROM DealEntity d
            WHERE d.createdAt >= :start
              AND d.createdAt < :end
            """)
    List<Object[]> findStageAgingRows(
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end);

    // ── Chat-assistant snapshot ───────────────────────────────────────────────
    // A null :userId means "every deal" (Manager/Admin scope); a non-null value restricts to that
    // user's records — the BR-36 filter lives in SQL for both scopes.

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
