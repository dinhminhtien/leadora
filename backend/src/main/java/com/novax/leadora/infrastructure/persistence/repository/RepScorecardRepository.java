package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.infrastructure.persistence.entity.DealEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * The per-rep scorecard aggregates that UC-23.1's own statement does not already produce.
 *
 * <p>Outcome and efficiency deliberately do <b>not</b> live here: they come from
 * {@link DealRepository#salesPerformanceAggregates}, the same statement the Sales Performance report
 * reads. Copying those six populations into a second query would have created a second definition
 * of "deals won", and the moment the two drift a manager is looking at one number on the report and
 * a different one on the scorecard with no way to tell which is right.
 *
 * <p>Not a {@code JpaRepository}: nothing here loads or writes a {@code DealEntity}, and extending
 * the full interface would advertise a save/delete surface that has no business existing on a
 * reporting type. {@code DealEntity} is named only because Spring Data needs a domain type.
 *
 * <p>Ranges are half-open: {@code [start, end)} — see ReportRange. Every {@code :zone} parameter is
 * the business time zone, because "closed on the day it was forecast for" is a question about local
 * calendar days and comparing UTC instants answers a different one.
 */
public interface RepScorecardRepository extends Repository<DealEntity, UUID> {

    /**
     * How long things took, as {@code [metric, ownerId, ownerName, samples, avgValue]}.
     *
     * <p>First response is measured as the time to move a lead off NEW, taken from activity_log.
     * That is the same event UC-17.2 uses to auto-resolve the LEAD_RESPONSE SLA, so the scorecard
     * and the SLA report are answering the same question. The obvious alternative — the first row
     * in interact_timelines — was rejected because that table is written by hand: a rep who does
     * not log calls would score as one who does not make them.
     */
    @Query(value = """
            WITH voided_ref AS (
                SELECT ref_activity_id AS activity_id
                  FROM activity_log
                 WHERE record_operation = 'VOIDED' AND ref_activity_id IS NOT NULL
            ),
            lead_first_response AS (
                SELECT a.entity_id AS lead_id, min(a.created_at) AS at
                  FROM activity_log a
                 WHERE a.activity_type = 'LEAD_STATUS_UPDATED'
                   AND a.entity_type = 'LEAD'
                   AND a.record_operation <> 'VOIDED'
                   AND NOT EXISTS (SELECT 1 FROM voided_ref v WHERE v.activity_id = a.id)
                 GROUP BY a.entity_id
            )
            SELECT 'FIRST_RESPONSE_HOURS' AS metric, u.user_id AS owner_id, u.full_name AS owner_name,
                   count(*) AS samples,
                   avg(EXTRACT(EPOCH FROM (fr.at - l.created_at)) / 3600.0) AS value
              FROM leads l
                   JOIN lead_first_response fr ON fr.lead_id = l.lead_id
                   LEFT JOIN users u ON u.user_id = l.assigned_user_id
             WHERE l.created_at >= :start AND l.created_at < :end
               AND fr.at >= l.created_at
             GROUP BY u.user_id, u.full_name
            UNION ALL
            SELECT 'LEAD_TO_CONVERSION_DAYS', u.user_id, u.full_name, count(*),
                   avg(EXTRACT(EPOCH FROM (l.converted_at - l.created_at)) / 86400.0)
              FROM leads l LEFT JOIN users u ON u.user_id = l.assigned_user_id
             WHERE l.converted_at >= :start AND l.converted_at < :end
               AND l.converted_at >= l.created_at
             GROUP BY u.user_id, u.full_name
            UNION ALL
            SELECT 'QUOTATION_TURNAROUND_HOURS', u.user_id, u.full_name, count(*),
                   avg(EXTRACT(EPOCH FROM (q.sent_at - q.created_at)) / 3600.0)
              FROM quotations q LEFT JOIN users u ON u.user_id = q.created_by
             WHERE q.created_at >= :start AND q.created_at < :end
               AND q.sent_at IS NOT NULL AND q.sent_at >= q.created_at
             GROUP BY u.user_id, u.full_name
            UNION ALL
            SELECT 'DEAL_CYCLE_DAYS', u.user_id, u.full_name, count(*),
                   avg(EXTRACT(EPOCH FROM (d.closed_at - d.created_at)) / 86400.0)
              FROM deals d LEFT JOIN users u ON u.user_id = d.assigned_user_id
             WHERE d.closed_at >= :start AND d.closed_at < :end
               AND d.status = 'WON' AND d.closed_at >= d.created_at
             GROUP BY u.user_id, u.full_name
            """, nativeQuery = true)
    List<Object[]> velocityByOwner(
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end);

    /**
     * Process discipline, as {@code [metric, ownerId, ownerName, count, value]}.
     *
     * <p>Counts and their denominators are emitted as separate rows rather than pre-divided, so the
     * caller can show "3 of 4" next to the percentage. A rate with no denominator on screen is how
     * a rep with two tasks ends up outranking one with forty.
     *
     * <p>{@code TASKS_OVERDUE} follows BR-17: overdue is derived from the clock, never stored.
     */
    @Query(value = """
            SELECT 'TASKS_TOTAL' AS metric, u.user_id AS owner_id, u.full_name AS owner_name,
                   count(*) AS cnt, 0::numeric AS value
              FROM tasks t LEFT JOIN users u ON u.user_id = t.assigned_user_id
             WHERE t.end_at >= :start AND t.end_at < :end
             GROUP BY u.user_id, u.full_name
            UNION ALL
            SELECT 'TASKS_COMPLETED', u.user_id, u.full_name, count(*), 0::numeric
              FROM tasks t LEFT JOIN users u ON u.user_id = t.assigned_user_id
             WHERE t.end_at >= :start AND t.end_at < :end AND t.status = 'COMPLETED'
             GROUP BY u.user_id, u.full_name
            UNION ALL
            SELECT 'TASKS_OVERDUE', u.user_id, u.full_name, count(*), 0::numeric
              FROM tasks t LEFT JOIN users u ON u.user_id = t.assigned_user_id
             WHERE t.end_at >= :start AND t.end_at < :end
               AND t.end_at < now() AND t.status NOT IN ('COMPLETED', 'CANCELLED')
             GROUP BY u.user_id, u.full_name
            UNION ALL
            SELECT 'DISCOUNT_PCT', u.user_id, u.full_name, count(*), COALESCE(avg(q.discount_percent), 0)
              FROM quotations q LEFT JOIN users u ON u.user_id = q.created_by
             WHERE q.created_at >= :start AND q.created_at < :end AND q.status <> 'SUPERSEDED'
             GROUP BY u.user_id, u.full_name
            UNION ALL
            SELECT 'QUOTATION_ROOTS', u.user_id, u.full_name, count(*), 0::numeric
              FROM quotations q LEFT JOIN users u ON u.user_id = q.created_by
             WHERE q.created_at >= :start AND q.created_at < :end AND q.parent_quotation_id IS NULL
             GROUP BY u.user_id, u.full_name
            UNION ALL
            SELECT 'QUOTATION_REVISIONS', u.user_id, u.full_name, count(*), 0::numeric
              FROM quotations q LEFT JOIN users u ON u.user_id = q.created_by
             WHERE q.created_at >= :start AND q.created_at < :end AND q.parent_quotation_id IS NOT NULL
             GROUP BY u.user_id, u.full_name
            UNION ALL
            SELECT 'COLLECTION_TOTAL', u.user_id, u.full_name, count(*), 0::numeric
              FROM payments p
                   LEFT JOIN bookings b ON b.booking_id = p.booking_id
                   LEFT JOIN users u ON u.user_id = b.assigned_user_id
             WHERE p.status = 'PAID' AND p.paid_at >= :start AND p.paid_at < :end
               AND p.due_date IS NOT NULL
             GROUP BY u.user_id, u.full_name
            UNION ALL
            SELECT 'COLLECTION_ONTIME', u.user_id, u.full_name, count(*), 0::numeric
              FROM payments p
                   LEFT JOIN bookings b ON b.booking_id = p.booking_id
                   LEFT JOIN users u ON u.user_id = b.assigned_user_id
             WHERE p.status = 'PAID' AND p.paid_at >= :start AND p.paid_at < :end
               AND p.due_date IS NOT NULL
               AND ((p.paid_at AT TIME ZONE CAST(:zone AS text))::date) <= p.due_date
             GROUP BY u.user_id, u.full_name
            UNION ALL
            SELECT 'FORECAST_TOTAL', u.user_id, u.full_name, count(*), 0::numeric
              FROM deals d LEFT JOIN users u ON u.user_id = d.assigned_user_id
             WHERE d.closed_at >= :start AND d.closed_at < :end
               AND d.expected_close_date IS NOT NULL
             GROUP BY u.user_id, u.full_name
            UNION ALL
            SELECT 'FORECAST_HIT', u.user_id, u.full_name, count(*), 0::numeric
              FROM deals d LEFT JOIN users u ON u.user_id = d.assigned_user_id
             WHERE d.closed_at >= :start AND d.closed_at < :end
               AND d.expected_close_date IS NOT NULL
               AND abs(((d.closed_at AT TIME ZONE CAST(:zone AS text))::date) - d.expected_close_date)
                   <= CAST(:forecastToleranceDays AS integer)
             GROUP BY u.user_id, u.full_name
            UNION ALL
            SELECT 'ACTIVE_DAYS', a.actor_user_id, u.full_name,
                   count(DISTINCT ((a.created_at AT TIME ZONE CAST(:zone AS text))::date)), 0::numeric
              FROM activity_log a LEFT JOIN users u ON u.user_id = a.actor_user_id
             WHERE a.created_at >= :start AND a.created_at < :end
               AND a.actor_user_id IS NOT NULL
             GROUP BY a.actor_user_id, u.full_name
            """, nativeQuery = true)
    List<Object[]> disciplineByOwner(
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end,
            @Param("zone") String zone,
            @Param("forecastToleranceDays") int forecastToleranceDays);

    /**
     * What the customer thought, as {@code [metric, ownerId, ownerName, label, count, value]}.
     *
     * <p>{@code label} carries the lost reason for LOST_REASON rows and is null for the rating
     * rows. Lost reasons ride along in the same statement because they are qualitative context for
     * the same person over the same period — a separate round trip to fetch a handful of strings
     * would be pure latency.
     *
     * <p>Feedback is attributed through {@code sales_feedbacks.sales_staff_id}, which the customer
     * never sees or chooses: it is whoever owned the booking, so this measures the rep's handling
     * and not the customer's memory of a name.
     */
    @Query(value = """
            SELECT 'CSAT' AS metric, u.user_id AS owner_id, u.full_name AS owner_name,
                   CAST(NULL AS text) AS label, count(f.rating) AS cnt, COALESCE(avg(f.rating), 0) AS value
              FROM sales_feedbacks f LEFT JOIN users u ON u.user_id = f.sales_staff_id
             WHERE f.submitted_at >= :start AND f.submitted_at < :end AND f.rating IS NOT NULL
             GROUP BY u.user_id, u.full_name
            UNION ALL
            SELECT 'CSAT_ATTITUDE', u.user_id, u.full_name, CAST(NULL AS text),
                   count(f.rating_attitude), COALESCE(avg(f.rating_attitude), 0)
              FROM sales_feedbacks f LEFT JOIN users u ON u.user_id = f.sales_staff_id
             WHERE f.submitted_at >= :start AND f.submitted_at < :end AND f.rating_attitude IS NOT NULL
             GROUP BY u.user_id, u.full_name
            UNION ALL
            SELECT 'CSAT_SPEED', u.user_id, u.full_name, CAST(NULL AS text),
                   count(f.rating_speed), COALESCE(avg(f.rating_speed), 0)
              FROM sales_feedbacks f LEFT JOIN users u ON u.user_id = f.sales_staff_id
             WHERE f.submitted_at >= :start AND f.submitted_at < :end AND f.rating_speed IS NOT NULL
             GROUP BY u.user_id, u.full_name
            UNION ALL
            SELECT 'CSAT_ACCURACY', u.user_id, u.full_name, CAST(NULL AS text),
                   count(f.rating_accuracy), COALESCE(avg(f.rating_accuracy), 0)
              FROM sales_feedbacks f LEFT JOIN users u ON u.user_id = f.sales_staff_id
             WHERE f.submitted_at >= :start AND f.submitted_at < :end AND f.rating_accuracy IS NOT NULL
             GROUP BY u.user_id, u.full_name
            UNION ALL
            SELECT 'LOST_REASON', u.user_id, u.full_name, r.lost_reason, count(*), 0::numeric
              FROM quotation_customer_responses r
                   JOIN quotations q ON q.quotation_id = r.quotation_id
                   LEFT JOIN users u ON u.user_id = q.created_by
             WHERE r.created_at >= :start AND r.created_at < :end
               AND r.lost_reason IS NOT NULL AND r.lost_reason <> ''
             GROUP BY u.user_id, u.full_name, r.lost_reason
            """, nativeQuery = true)
    List<Object[]> qualityByOwner(
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end);

    /**
     * One row per tracked SLA, as {@code [ownerId, ownerName, status, warningAt, deadlineAt,
     * resolvedAt]}, so the caller can classify them with the same code UC-23.3 uses.
     *
     * <p>Returning rows instead of counts is a deliberate exception to the aggregate-in-SQL rule
     * everywhere else in this file. Classifying in SQL would mean writing the "was this on time"
     * rules a second time, in a second language, and the whole point of
     * {@code SlaOutcomeClassifier} is that those rules exist once. At a few hundred tracked SLAs
     * per period the extra rows cost nothing; if that stops being true, aggregate in SQL and move
     * the classifier's tests onto the query.
     *
     * <p>{@code sla_tracking} points at its subject through a polymorphic {@code (entity_type,
     * entity_id)} pair with no foreign key, so the owner has to be recovered by trying each table
     * in turn. A denormalised {@code owner_user_id} on the table would make this a lookup instead.
     */
    @Query(value = """
            SELECT o.owner_id, u.full_name AS owner_name,
                   o.status, o.warning_at, o.deadline_at, o.resolved_at
              FROM (
                    SELECT COALESCE(l.assigned_user_id, t.assigned_user_id,
                                    q.created_by, b.assigned_user_id) AS owner_id,
                           s.status, s.warning_at, s.deadline_at, s.resolved_at
                      FROM sla_tracking s
                           LEFT JOIN leads l ON s.entity_type = 'LEAD' AND l.lead_id = s.entity_id
                           LEFT JOIN tasks t ON s.entity_type = 'TASK' AND t.task_id = s.entity_id
                           LEFT JOIN quotations q ON s.entity_type = 'QUOTATION' AND q.quotation_id = s.entity_id
                           LEFT JOIN bookings b ON s.entity_type = 'BOOKING' AND b.booking_id = s.entity_id
                     WHERE s.started_at >= :start AND s.started_at < :end
                   ) o
                   LEFT JOIN users u ON u.user_id = o.owner_id
            """, nativeQuery = true)
    List<Object[]> slaRowsByOwner(
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end);
}
