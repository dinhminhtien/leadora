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

    // ── UC-23.1: the whole report in one round trip ───────────────────────────
    //
    // Both databases this application talks to are remote (Supabase Postgres, Upstash Redis), so
    // every statement pays an internet round trip. Split across ten small aggregates the report was
    // ten round trips deep: at these table sizes — tens to a couple of hundred rows — the query
    // work is free and the latency is entirely in the trips.
    //
    // The headline KPIs and the per-rep table used to be two statements over the same populations,
    // which meant the two could disagree while both looked plausible. Carrying the owner as an
    // extra grouping column collapses them into one: the per-rep rows ARE the headline rows before
    // they are summed, so "the table adds up to the tiles" is now structural rather than a property
    // somebody has to keep remembering. It also costs one round trip instead of two.
    //
    // Native rather than JPQL because the branches select different entities into one result shape,
    // which HQL set operations handle awkwardly, and because plain SQL could be run against the
    // real database to confirm the numbers before it was wired in.
    //
    // Three time axes on purpose. Acquisition ("how many deals did we open") belongs to created_at;
    // outcome ("how many did we win") belongs to closed_at; cash belongs to paid_at. Measuring
    // outcomes on created_at drops a deal opened in May and won in July, and dilutes the rate with
    // deals still in flight.
    //
    // Event dates that have no column of their own — when a lead was qualified, when a booking was
    // confirmed — are reconstructed from activity_log rather than inferred from the row's CURRENT
    // status. Counting "leads created in the period whose status is QUALIFIED right now" made the
    // number fall when a rep did well, because qualifying then converting a lead moved it out of
    // the bucket; the same mistake UC-23.3 had to unwind for SLA breaches.

    /**
     * Every UC-23.1 aggregate, headline and per-rep alike, as
     * {@code [kind, bucket, ownerId, ownerName, count, amount]}.
     *
     * <p>{@code kind} names the population and the time axis it is measured on; {@code bucket} is
     * that population's status. Sum across owners for a headline KPI; read a single kind across
     * owners for the per-rep table. A null {@code ownerId} is the unassigned group, deliberately
     * kept — dropping it is what stopped the per-rep table adding up.
     *
     * <p>Owner attribution differs per population, and the difference is meaningful: leads, deals
     * and bookings go to their assignee, quotations to {@code created_by} (whoever did the work of
     * drafting it), and revenue to the assignee of the booking the payment sits on.
     *
     * <p>Filters are all optional. {@code ownerId} narrows to one rep; {@code source} /
     * {@code service} / {@code corporate} narrow to a lead segment, which for the non-lead
     * populations means "whose customer came from a lead matching this segment". {@code segmentOff}
     * is computed caller-side and short-circuits the segment lookup entirely when no segment filter
     * was supplied, so the common case pays nothing for the feature.
     *
     * <p>{@code source} matches exactly because leads pick it from a fixed list; {@code service}
     * matches as a case-insensitive substring because {@code interested_service} is free text a rep
     * typed, and an exact match on free text finds nothing often enough to look like a broken filter.
     *
     * <p>A confirmation date is taken from three sources, best evidence first: the confirmation
     * event, then the deposit that caused it, then the row's own creation date. That last fallback
     * is not cosmetic — without it, a booking confirmed before this audit trail existed and paid for
     * outside the system carries no date at all and drops out of <em>every</em> period, quietly
     * lowering the historical "bookings confirmed" figure instead of dating one record approximately.
     *
     * <p>Prose lives here and never inside the statement: Spring Data parses the query text before
     * Postgres ever sees it, and an apostrophe in a {@code --} comment reads to that parser as the
     * start of a string literal, which fails repository creation at startup.
     *
     * <p>Living on DealRepository is a compromise: the statement spans seven tables and belongs to
     * no single one.
     */
    @Query(value = """
            WITH voided_ref AS (
                SELECT ref_activity_id AS activity_id
                  FROM activity_log
                 WHERE record_operation = 'VOIDED' AND ref_activity_id IS NOT NULL
            ),
            segment_leads AS (
                SELECT lk.lead_id, lk.customer_id
                  FROM leads lk
                 WHERE (CAST(:source AS text) IS NULL OR lk.source = CAST(:source AS text))
                   AND (CAST(:service AS text) IS NULL OR lk.interested_service ILIKE '%' || CAST(:service AS text) || '%')
                   AND (CAST(:corporate AS boolean) IS NULL OR lk.is_corporate = CAST(:corporate AS boolean))
            ),
            segment_customers AS (
                SELECT DISTINCT customer_id FROM segment_leads WHERE customer_id IS NOT NULL
            ),
            lead_qualified AS (
                SELECT a.entity_id AS lead_id, min(a.created_at) AS at
                  FROM activity_log a
                 WHERE a.activity_type = 'LEAD_STATUS_UPDATED'
                   AND a.entity_type = 'LEAD'
                   AND a.payload->>'newStatus' = 'QUALIFIED'
                   AND a.record_operation <> 'VOIDED'
                   AND NOT EXISTS (SELECT 1 FROM voided_ref v WHERE v.activity_id = a.id)
                 GROUP BY a.entity_id
            ),
            booking_confirmed AS (
                SELECT b.booking_id, b.assigned_user_id, b.customer_id,
                       COALESCE(al.at, pay.at, b.created_at) AS at
                  FROM bookings b
                  LEFT JOIN (SELECT a.entity_id, min(a.created_at) AS at
                               FROM activity_log a
                              WHERE a.activity_type = 'BOOKING_CONFIRMED'
                                AND a.entity_type = 'BOOKING'
                                AND a.record_operation <> 'VOIDED'
                                AND NOT EXISTS (SELECT 1 FROM voided_ref v WHERE v.activity_id = a.id)
                              GROUP BY a.entity_id) al ON al.entity_id = b.booking_id
                  LEFT JOIN (SELECT p.booking_id, min(p.paid_at) AS at
                               FROM payments p
                              WHERE p.status = 'PAID' AND p.paid_at IS NOT NULL
                              GROUP BY p.booking_id) pay ON pay.booking_id = b.booking_id
                 WHERE b.status IN ('CONFIRMED', 'CHECKED_IN', 'CHECKED_OUT')
            )
            SELECT 'LEAD' AS kind, l.status::text AS bucket,
                   u.user_id AS owner_id, u.full_name AS owner_name,
                   count(*) AS cnt, 0::numeric AS amount
              FROM leads l LEFT JOIN users u ON u.user_id = l.assigned_user_id
             WHERE l.created_at >= :start AND l.created_at < :end
               AND (CAST(:ownerId AS uuid) IS NULL OR l.assigned_user_id = CAST(:ownerId AS uuid))
               AND (CAST(:segmentOff AS boolean) OR l.lead_id IN (SELECT lead_id FROM segment_leads))
             GROUP BY l.status, u.user_id, u.full_name
            UNION ALL
            SELECT 'LEAD_QUALIFIED', 'QUALIFIED', u.user_id, u.full_name, count(*), 0::numeric
              FROM lead_qualified lq
                   JOIN leads l ON l.lead_id = lq.lead_id
                   LEFT JOIN users u ON u.user_id = l.assigned_user_id
             WHERE lq.at >= :start AND lq.at < :end
               AND (CAST(:ownerId AS uuid) IS NULL OR l.assigned_user_id = CAST(:ownerId AS uuid))
               AND (CAST(:segmentOff AS boolean) OR l.lead_id IN (SELECT lead_id FROM segment_leads))
             GROUP BY u.user_id, u.full_name
            UNION ALL
            SELECT 'LEAD_CONVERTED', 'CONVERTED', u.user_id, u.full_name, count(*), 0::numeric
              FROM leads l LEFT JOIN users u ON u.user_id = l.assigned_user_id
             WHERE l.converted_at >= :start AND l.converted_at < :end
               AND (CAST(:ownerId AS uuid) IS NULL OR l.assigned_user_id = CAST(:ownerId AS uuid))
               AND (CAST(:segmentOff AS boolean) OR l.lead_id IN (SELECT lead_id FROM segment_leads))
             GROUP BY u.user_id, u.full_name
            UNION ALL
            SELECT 'LEAD_COHORT_CONVERTED', 'CONVERTED', u.user_id, u.full_name, count(*), 0::numeric
              FROM leads l LEFT JOIN users u ON u.user_id = l.assigned_user_id
             WHERE l.created_at >= :start AND l.created_at < :end
               AND l.converted_at IS NOT NULL
               AND (CAST(:ownerId AS uuid) IS NULL OR l.assigned_user_id = CAST(:ownerId AS uuid))
               AND (CAST(:segmentOff AS boolean) OR l.lead_id IN (SELECT lead_id FROM segment_leads))
             GROUP BY u.user_id, u.full_name
            UNION ALL
            SELECT 'DEAL_OPENED', d.status::text, u.user_id, u.full_name,
                   count(*), COALESCE(sum(d.expected_revenue), 0)
              FROM deals d LEFT JOIN users u ON u.user_id = d.assigned_user_id
             WHERE d.created_at >= :start AND d.created_at < :end
               AND (CAST(:ownerId AS uuid) IS NULL OR d.assigned_user_id = CAST(:ownerId AS uuid))
               AND (CAST(:segmentOff AS boolean) OR d.customer_id IN (SELECT customer_id FROM segment_customers))
             GROUP BY d.status, u.user_id, u.full_name
            UNION ALL
            SELECT 'DEAL_CLOSED', d.status::text, u.user_id, u.full_name,
                   count(*), COALESCE(sum(d.expected_revenue), 0)
              FROM deals d LEFT JOIN users u ON u.user_id = d.assigned_user_id
             WHERE d.closed_at >= :start AND d.closed_at < :end
               AND (CAST(:ownerId AS uuid) IS NULL OR d.assigned_user_id = CAST(:ownerId AS uuid))
               AND (CAST(:segmentOff AS boolean) OR d.customer_id IN (SELECT customer_id FROM segment_customers))
             GROUP BY d.status, u.user_id, u.full_name
            UNION ALL
            SELECT 'QUOTATION', q.status::text, u.user_id, u.full_name, count(*), 0::numeric
              FROM quotations q LEFT JOIN users u ON u.user_id = q.created_by
             WHERE q.created_at >= :start AND q.created_at < :end
               AND (CAST(:ownerId AS uuid) IS NULL OR q.created_by = CAST(:ownerId AS uuid))
               AND (CAST(:segmentOff AS boolean) OR q.customer_id IN (SELECT customer_id FROM segment_customers))
             GROUP BY q.status, u.user_id, u.full_name
            UNION ALL
            SELECT 'BOOKING_CONFIRMED', 'CONFIRMED', u.user_id, u.full_name, count(*), 0::numeric
              FROM booking_confirmed bc LEFT JOIN users u ON u.user_id = bc.assigned_user_id
             WHERE bc.at >= :start AND bc.at < :end
               AND (CAST(:ownerId AS uuid) IS NULL OR bc.assigned_user_id = CAST(:ownerId AS uuid))
               AND (CAST(:segmentOff AS boolean) OR bc.customer_id IN (SELECT customer_id FROM segment_customers))
             GROUP BY u.user_id, u.full_name
            UNION ALL
            SELECT 'REVENUE', 'PAID', u.user_id, u.full_name, count(*), COALESCE(sum(p.amount), 0)
              FROM payments p
                   LEFT JOIN bookings b ON b.booking_id = p.booking_id
                   LEFT JOIN users u ON u.user_id = b.assigned_user_id
             WHERE p.status = 'PAID'
               AND ((p.paid_at IS NOT NULL AND p.paid_at >= :start AND p.paid_at < :end) OR (p.paid_at IS NULL AND p.created_at >= :start AND p.created_at < :end))
               AND (CAST(:ownerId AS uuid) IS NULL OR b.assigned_user_id = CAST(:ownerId AS uuid))
               AND (CAST(:segmentOff AS boolean) OR b.customer_id IN (SELECT customer_id FROM segment_customers))
             GROUP BY u.user_id, u.full_name
            """, nativeQuery = true)
    List<Object[]> salesPerformanceAggregates(
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end,
            @Param("ownerId") String ownerId,
            @Param("source") String source,
            @Param("service") String service,
            @Param("corporate") String corporate,
            @Param("segmentOff") boolean segmentOff);

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
