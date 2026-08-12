package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * UC-23.5 aggregates, as one statement.
 *
 * <p>Ranges are half-open: {@code [start, end)}. The report answers two different questions and they
 * need two different populations, so every row carries which one it belongs to:
 *
 * <ul>
 *   <li><b>COHORT</b> — quotations <em>created</em> in the period. Follows a batch of work to see
 *       what became of it, and is the only population a win rate can be computed against.
 *   <li><b>ACTIVITY</b> — approvals, customer replies, dispatches, closures and conversions that
 *       <em>happened</em> in the period, whenever the quotation was written. This is what the team
 *       did this month, and on a single created_at axis it was partly invisible: a quotation raised
 *       in June and approved in July showed up in neither month's approval figure.
 * </ul>
 *
 * <h2>Which rows are live</h2>
 *
 * <p>BR-22 turns an edit into a new version row, and the replaced row is supposed to be marked
 * SUPERSEDED. In practice the status column does not hold that promise: a replaced quotation is
 * frequently left at EXPIRED or CLOSED by whatever touched it next, so filtering on the status
 * misses most of them and the denominator counts one negotiation several times. The durable fact is
 * structural — a revision exists that points at this row — so that is what the cohort filters on.
 *
 * <p>The status is still honoured on top of it. The two signals fail in opposite directions: a
 * status left behind by a later edit misses replaced rows, while a SUPERSEDED row whose child was
 * deleted would otherwise be counted as a quotation still awaiting an answer. Excluding on either
 * signal is the only combination with no way to land in the cohort by mistake.
 *
 * <p>The ACTIVITY branches deliberately do <em>not</em> apply that filter. An approval decision made
 * on a quotation that was later revised was still a decision somebody made in this period; dropping
 * it would understate the approver's workload.
 *
 * <h2>Losing and never starting are different failures</h2>
 *
 * <p>REJECTED, EXPIRED and CLOSED do not all mean a customer declined. {@code
 * ProcessQuotationApprovalUseCase} writes REJECTED when the <em>approver</em> turns a quotation
 * down, and {@code ExpireOverdueQuotationsUseCase} expires rows still sitting at DRAFT or
 * PENDING_APPROVAL — neither ever reached a customer. Folding them into losses would put a rep who
 * drafted twenty quotations and submitted none at the bottom of the win rate for work no customer
 * ever saw, and inflate the lost value by the whole draft book.
 *
 * <p>So the split is made on {@code sent_at}: a terminal quotation that was dispatched is a LOST
 * negotiation, one that never was is ABANDONED, and only the first belongs in a win rate. The same
 * distinction separates an expiry that a follow-up let lapse from one that was never sent at all.
 *
 * <p>The owner is the <b>preparer</b> ({@code quotations.created_by}). Quotations have no assignee
 * of their own, and attributing them through the deal would credit whoever owns the customer rather
 * than whoever wrote the document the report is about.
 *
 * <p>Prose stays in Javadoc and never inside the statement: Spring Data parses the query text before
 * Postgres sees it, and an apostrophe in a {@code --} comment reads to that parser as the start of a
 * string literal, which fails repository creation at startup.
 */
public interface QuotationOutcomeRepository extends Repository<QuotationEntity, UUID> {

    /**
     * Every UC-23.5 aggregate, as {@code [metric, bucket, ownerId, ownerName, count, value]}.
     *
     * <p>{@code value} carries whatever the metric needs a number for — a money total for the
     * outcome and discount branches, an average latency in hours for the timing ones, and zero
     * where the count is the whole answer. A null {@code ownerId} is the unattributed group, kept
     * so the preparer table reconciles with the headline.
     *
     * <p>{@code COHORT_FACT} does not classify. It emits {@code STATUS|SENT|REPLACED} over every
     * quotation written in the period, including the replaced ones, and
     * {@code QuotationOutcomeClassifier} decides what each is — the same code UC-23.1 and the rep
     * scorecard fold their quotations through. Rows the cohort drops are therefore still present
     * and countable rather than filtered away: a total that silently shrinks is indistinguishable
     * from a slow month.
     */
    @Query(value = """
            WITH cohort AS (
                SELECT q.quotation_id, q.created_by, q.created_at, q.sent_at, q.approved_at,
                       q.status, q.total_amount, q.discount_percent
                  FROM quotations q
                 WHERE q.created_at >= :start AND q.created_at < :end
                   AND q.status <> 'SUPERSEDED'
                   AND NOT EXISTS (SELECT 1 FROM quotations r
                                    WHERE r.parent_quotation_id = q.quotation_id)
            )
            SELECT 'COHORT_FACT' AS metric,
                   q.status::text
                     || '|' || CASE WHEN q.sent_at IS NULL THEN 'UNSENT' ELSE 'SENT' END
                     || '|' || CASE WHEN EXISTS (SELECT 1 FROM quotations r
                                                  WHERE r.parent_quotation_id = q.quotation_id)
                                    THEN 'REPLACED' ELSE 'CURRENT' END AS bucket,
                   u.user_id AS owner_id, u.full_name AS owner_name,
                   count(*) AS cnt, COALESCE(sum(q.total_amount), 0) AS value
              FROM quotations q LEFT JOIN users u ON u.user_id = q.created_by
             WHERE q.created_at >= :start AND q.created_at < :end
             GROUP BY 2, u.user_id, u.full_name
            UNION ALL
            SELECT 'COHORT_APPROVAL',
                   CASE WHEN c.approved_at IS NOT NULL THEN 'APPROVED' ELSE 'NEVER_APPROVED' END,
                   u.user_id, u.full_name, count(*), 0::numeric
              FROM cohort c LEFT JOIN users u ON u.user_id = c.created_by
             GROUP BY 2, u.user_id, u.full_name
            UNION ALL
            SELECT 'COHORT_DISCOUNT',
                   CASE WHEN COALESCE(c.discount_percent, 0) <= 0 THEN 'D0'
                        WHEN c.discount_percent <= 10 THEN 'D1_10'
                        WHEN c.discount_percent <= 20 THEN 'D11_20'
                        ELSE 'D21_PLUS' END,
                   u.user_id, u.full_name, count(*), COALESCE(sum(c.total_amount), 0)
              FROM cohort c LEFT JOIN users u ON u.user_id = c.created_by
             GROUP BY 2, u.user_id, u.full_name
            UNION ALL
            SELECT 'COHORT_SEND',
                   CASE WHEN c.sent_at IS NULL THEN 'NEVER_SENT' ELSE 'SENT' END,
                   u.user_id, u.full_name, count(*),
                   COALESCE(avg(EXTRACT(EPOCH FROM (c.sent_at - c.created_at)) / 3600.0), 0)
              FROM cohort c LEFT JOIN users u ON u.user_id = c.created_by
             GROUP BY 2, u.user_id, u.full_name
            UNION ALL
            SELECT 'COHORT_EXPIRY',
                   CASE WHEN c.sent_at IS NULL THEN 'EXPIRED_NEVER_SENT'
                        WHEN EXISTS (SELECT 1 FROM quotation_customer_responses r
                                      WHERE r.quotation_id = c.quotation_id)
                        THEN 'EXPIRED_AFTER_REPLY' ELSE 'EXPIRED_NO_REPLY' END,
                   u.user_id, u.full_name, count(*), 0::numeric
              FROM cohort c LEFT JOIN users u ON u.user_id = c.created_by
             WHERE c.status = 'EXPIRED'
             GROUP BY 2, u.user_id, u.full_name
            UNION ALL
            SELECT 'ACT_DECISION', h.action, u.user_id, u.full_name, count(*), 0::numeric
              FROM quotation_approval_history h
                   JOIN quotations q ON q.quotation_id = h.quotation_id
                   LEFT JOIN users u ON u.user_id = q.created_by
             WHERE h.created_at >= :start AND h.created_at < :end
             GROUP BY h.action, u.user_id, u.full_name
            UNION ALL
            SELECT 'ACT_APPROVED_STAMP', 'APPROVED', u.user_id, u.full_name, count(*),
                   COALESCE(avg(EXTRACT(EPOCH FROM (q.approved_at - q.created_at)) / 3600.0), 0)
              FROM quotations q LEFT JOIN users u ON u.user_id = q.created_by
             WHERE q.approved_at >= :start AND q.approved_at < :end
             GROUP BY u.user_id, u.full_name
            UNION ALL
            SELECT 'ACT_REPLY', r.customer_response, u.user_id, u.full_name, count(*), 0::numeric
              FROM quotation_customer_responses r
                   JOIN quotations q ON q.quotation_id = r.quotation_id
                   LEFT JOIN users u ON u.user_id = q.created_by
             WHERE r.created_at >= :start AND r.created_at < :end
             GROUP BY r.customer_response, u.user_id, u.full_name
            UNION ALL
            SELECT 'ACT_REPLY_TIMED', 'TIMED', u.user_id, u.full_name, count(*),
                   COALESCE(avg(EXTRACT(EPOCH FROM (r.created_at - q.sent_at)) / 3600.0), 0)
              FROM quotation_customer_responses r
                   JOIN quotations q ON q.quotation_id = r.quotation_id
                   LEFT JOIN users u ON u.user_id = q.created_by
             WHERE r.created_at >= :start AND r.created_at < :end
               AND q.sent_at IS NOT NULL
             GROUP BY u.user_id, u.full_name
            UNION ALL
            SELECT 'ACT_LOST_REASON', r.lost_reason, u.user_id, u.full_name, count(*), 0::numeric
              FROM quotation_customer_responses r
                   JOIN quotations q ON q.quotation_id = r.quotation_id
                   LEFT JOIN users u ON u.user_id = q.created_by
             WHERE r.created_at >= :start AND r.created_at < :end
               AND r.lost_reason IS NOT NULL
             GROUP BY r.lost_reason, u.user_id, u.full_name
            UNION ALL
            SELECT 'ACT_CLOSURE', l.action, u.user_id, u.full_name, count(*), 0::numeric
              FROM quotation_closure_logs l
                   JOIN quotations q ON q.quotation_id = l.quotation_id
                   LEFT JOIN users u ON u.user_id = q.created_by
             WHERE l.created_at >= :start AND l.created_at < :end
             GROUP BY l.action, u.user_id, u.full_name
            UNION ALL
            SELECT 'ACT_SENT', 'SENT', u.user_id, u.full_name, count(*), 0::numeric
              FROM quotations q LEFT JOIN users u ON u.user_id = q.created_by
             WHERE q.sent_at >= :start AND q.sent_at < :end
             GROUP BY u.user_id, u.full_name
            UNION ALL
            SELECT 'ACT_CONVERTED', 'CONVERTED', u.user_id, u.full_name,
                   count(*), COALESCE(sum(cq.total_amount), 0)
              FROM (SELECT DISTINCT q.quotation_id, q.created_by, q.total_amount
                      FROM bookings b JOIN quotations q ON q.quotation_id = b.quotation_id
                     WHERE b.created_at >= :start AND b.created_at < :end) cq
                   LEFT JOIN users u ON u.user_id = cq.created_by
             GROUP BY u.user_id, u.full_name
            """, nativeQuery = true)
    List<Object[]> quotationOutcomeAggregates(
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end);
}
