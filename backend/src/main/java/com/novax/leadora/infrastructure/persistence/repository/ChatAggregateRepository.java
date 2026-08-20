package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.application.usecase.chat.dto.ChatCounts;
import com.novax.leadora.application.usecase.chat.dto.SlaRow;
import com.novax.leadora.application.usecase.chat.dto.StatusBucket;
import com.novax.leadora.application.usecase.chat.intent.CrmArea;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every area's status breakdown for the chat snapshot, in one round trip.
 *
 * <p><b>Why one query instead of seven typed ones.</b> Measured against the running application,
 * gathering context took 716ms of a 1.6-second time-to-first-token, and almost none of it was
 * work: it was eight sequential round trips to a database in another country, each costing more
 * in latency than in execution. Batching them collapses that to a single trip. The model's own
 * first token, by comparison, took 469ms — the database was the bottleneck, not the LLM.
 *
 * <p>This is deliberately native SQL. The equivalent in JPQL would be seven separate statements,
 * which is exactly the problem; a UNION across seven unrelated entities is not something the
 * criteria API expresses well, and each branch needs its own scoping join anyway. The cost of that
 * choice is that {@code ChatQueryCompilationTest} cannot validate this the way it validates
 * declared JPQL — see {@code ChatAggregateRepositoryTest} for the guard that partly makes up for
 * it, and verify against a real database when changing the statement.
 *
 * <p>Scoping (BR-36) is a single {@code :scope} parameter, null meaning "every record". Quotations,
 * payments and SLA records carry no assignee of their own and are reached through the deal, booking
 * or deal they belong to, and contracts through their deal; every one of those parent columns is
 * NOT NULL, so the joins cannot drop rows.
 */
@Repository
@RequiredArgsConstructor
public class ChatAggregateRepository {

    /** Marks the derived overdue-task row, which is a filter rather than a stored status. */
    public static final String OVERDUE_MARKER = "__OVERDUE__";

    /**
     * Marks the derived low-rating feedback row: a customer score of 2 or less.
     *
     * <p>Derived like the overdue one, and for the same reason — nothing stores "this went badly".
     * It is worth a branch of its own because "are there unhappy customers?" is the question
     * actually asked, and answering it from a review-status breakdown would mean reading a
     * workflow state (has somebody looked at it) as if it were a sentiment.
     */
    public static final String LOW_RATING_MARKER = "__LOW_RATING__";

    /**
     * The period predicate, applied to every branch on its own {@code created_at}.
     *
     * <p>Both bounds are always supplied — {@code ChatDateRange} substitutes far-past and
     * far-future sentinels for an open-ended side — so there is no {@code IS NULL} guard to write.
     * That is not only shorter: a guarded bound cannot use the index, because the planner has to
     * evaluate the {@code OR} per row. Two plain comparisons stay sargable.
     *
     * <p><b>One column per area, and for eight of them it is {@code created_at}.</b> None is
     * filtered on an area-specific date ({@code paid_at}, {@code check_in_date}, {@code end_at}):
     * those each answer a different question — "paid this month" is not "created this month" — and
     * mixing them inside one snapshot would produce a set of numbers that cannot be added up or
     * compared. The reference block states the rule so the assistant can say which it is.
     *
     * <p><b>Feedback is the one exception, and it is not an inconsistency.</b> A feedback row is
     * created when the survey link is issued, which is an act of ours, and carries no answer at
     * all until the customer sends one — {@code submitted_at} is when the thing being counted
     * happened. Counting on {@code created_at} would report links we sent, presented as opinions
     * we received, and would move a customer's reply into the month we happened to ask. The
     * section labels its own column so the two windows are never read as the same window.
     */
    private static String dated(String column) {
        return "   AND " + column + " >= :from\n"
                + "   AND " + column + " <= :to\n";
    }

    private static final String COUNT_ALL = """
            SELECT 'LEADS' AS area, l.status AS status, COUNT(*) AS cnt, NULL::numeric AS amount
              FROM leads l
             WHERE (CAST(:scope AS uuid) IS NULL OR l.assigned_user_id = CAST(:scope AS uuid))
            """
            + dated("l.created_at")
            + """
             GROUP BY l.status
            UNION ALL
            SELECT 'DEALS', d.status, COUNT(*), COALESCE(SUM(d.expected_revenue), 0)
              FROM deals d
             WHERE (CAST(:scope AS uuid) IS NULL OR d.assigned_user_id = CAST(:scope AS uuid))
            """
            + dated("d.created_at")
            + """
             GROUP BY d.status
            UNION ALL
            SELECT 'TASKS', t.status, COUNT(*), NULL
              FROM tasks t
             WHERE (CAST(:scope AS uuid) IS NULL OR t.assigned_user_id = CAST(:scope AS uuid))
            """
            + dated("t.created_at")
            + """
             GROUP BY t.status
            UNION ALL
            SELECT 'TASKS', '__OVERDUE__', COUNT(*), NULL
              FROM tasks t
             WHERE (CAST(:scope AS uuid) IS NULL OR t.assigned_user_id = CAST(:scope AS uuid))
               AND t.status NOT IN ('COMPLETED', 'CANCELLED')
               AND t.end_at IS NOT NULL
               AND t.end_at < now()
            """
            + dated("t.created_at")
            + """
            UNION ALL
            SELECT 'QUOTATIONS', q.status, COUNT(*), COALESCE(SUM(q.total_amount), 0)
              FROM quotations q
              JOIN deals qd ON qd.deal_id = q.deal_id
             WHERE (CAST(:scope AS uuid) IS NULL OR qd.assigned_user_id = CAST(:scope AS uuid))
            """
            + dated("q.created_at")
            + """
             GROUP BY q.status
            UNION ALL
            SELECT 'CONTRACTS', ct.status, COUNT(*), COALESCE(SUM(ct.total_contract_value), 0)
              FROM contracts ct
              JOIN deals cd ON cd.deal_id = ct.deal_id
             WHERE (CAST(:scope AS uuid) IS NULL OR cd.assigned_user_id = CAST(:scope AS uuid))
            """
            + dated("ct.created_at")
            + """
             GROUP BY ct.status
            UNION ALL
            SELECT 'BOOKINGS', b.status, COUNT(*), COALESCE(SUM(b.total_amount), 0)
              FROM bookings b
             WHERE (CAST(:scope AS uuid) IS NULL OR b.assigned_user_id = CAST(:scope AS uuid))
            """
            + dated("b.created_at")
            + """
             GROUP BY b.status
            UNION ALL
            SELECT 'PAYMENTS', p.status, COUNT(*), COALESCE(SUM(p.amount), 0)
              FROM payments p
              JOIN bookings pb ON pb.booking_id = p.booking_id
             WHERE (CAST(:scope AS uuid) IS NULL OR pb.assigned_user_id = CAST(:scope AS uuid))
            """
            + dated("p.created_at")
            + """
             GROUP BY p.status
            UNION ALL
            SELECT 'CUSTOMERS', c.status, COUNT(*), NULL
              FROM customers c
             WHERE (CAST(:scope AS uuid) IS NULL OR c.assigned_user_id = CAST(:scope AS uuid))
            """
            + dated("c.created_at")
            + """
             GROUP BY c.status
            UNION ALL
            SELECT 'SLA', st.status, COUNT(*), NULL
              FROM sla_tracking st
              LEFT JOIN leads      sl  ON st.entity_type = 'LEAD'      AND sl.lead_id      = st.entity_id
              LEFT JOIN tasks      stk ON st.entity_type = 'TASK'      AND stk.task_id     = st.entity_id
              LEFT JOIN quotations sq  ON st.entity_type = 'QUOTATION' AND sq.quotation_id = st.entity_id
              LEFT JOIN bookings   sb  ON st.entity_type = 'BOOKING'   AND sb.booking_id   = st.entity_id
             WHERE (CAST(:scope AS uuid) IS NULL
                    OR (st.entity_type = 'LEAD' AND sl.assigned_user_id = CAST(:scope AS uuid))
                    OR (st.entity_type = 'TASK' AND stk.assigned_user_id = CAST(:scope AS uuid))
                    OR (st.entity_type = 'QUOTATION' AND sq.created_by = CAST(:scope AS uuid))
                    OR (st.entity_type = 'BOOKING' AND sb.assigned_user_id = CAST(:scope AS uuid)))
            """
            + dated("st.created_at")
            + """
             GROUP BY st.status
            UNION ALL
            SELECT 'FEEDBACK', f.review_status, COUNT(*), AVG(f.rating)
              FROM sales_feedbacks f
             WHERE (CAST(:scope AS uuid) IS NULL OR f.sales_staff_id = CAST(:scope AS uuid))
               AND f.submitted_at IS NOT NULL
            """
            + dated("f.submitted_at")
            + """
             GROUP BY f.review_status
            UNION ALL
            SELECT 'FEEDBACK', '__LOW_RATING__', COUNT(*), NULL
              FROM sales_feedbacks f
             WHERE (CAST(:scope AS uuid) IS NULL OR f.sales_staff_id = CAST(:scope AS uuid))
               AND f.submitted_at IS NOT NULL
               AND f.rating IS NOT NULL
               AND f.rating <= 2
            """
            + dated("f.submitted_at")
            + """
            """;

    /**
     * The 4-way polymorphic join that resolves who owns an SLA tracking row. Shared by the count
     * branch above and the listing below so the two can never disagree about ownership.
     */
    private static final String SLA_SCOPE_JOIN = """
              FROM sla_tracking st
              LEFT JOIN leads      sl  ON st.entity_type = 'LEAD'      AND sl.lead_id      = st.entity_id
              LEFT JOIN tasks      stk ON st.entity_type = 'TASK'      AND stk.task_id     = st.entity_id
              LEFT JOIN quotations sq  ON st.entity_type = 'QUOTATION' AND sq.quotation_id = st.entity_id
              LEFT JOIN bookings   sb  ON st.entity_type = 'BOOKING'   AND sb.booking_id   = st.entity_id
            """;

    /**
     * The unresolved SLA rows themselves, most urgent first.
     *
     * <p>Native for the same reason as the batched count: the subject is referenced polymorphically,
     * which JPQL cannot join on at all. A RESOLVED row is history — it still counts, but listing it
     * would push the rows that need attention off the end of the cap.
     */
    private static final String SLA_LISTING =
            "SELECT st.activity_type, st.entity_type, st.status, st.deadline_at,\n"
            + "       COALESCE(ul.full_name, ut.full_name, uq.full_name, ub.full_name) AS assignee\n"
            + SLA_SCOPE_JOIN
            + "  LEFT JOIN users ul ON ul.user_id = sl.assigned_user_id\n"
            + "  LEFT JOIN users ut ON ut.user_id = stk.assigned_user_id\n"
            + "  LEFT JOIN users uq ON uq.user_id = sq.created_by\n"
            + "  LEFT JOIN users ub ON ub.user_id = sb.assigned_user_id\n"
            + " WHERE (CAST(:scope AS uuid) IS NULL OR \n"
            + "        (st.entity_type = 'LEAD' AND sl.assigned_user_id = CAST(:scope AS uuid)) OR\n"
            + "        (st.entity_type = 'TASK' AND stk.assigned_user_id = CAST(:scope AS uuid)) OR\n"
            + "        (st.entity_type = 'QUOTATION' AND sq.created_by = CAST(:scope AS uuid)) OR\n"
            + "        (st.entity_type = 'BOOKING' AND sb.assigned_user_id = CAST(:scope AS uuid)))\n"
            + "   AND st.status <> 'RESOLVED'\n"
            + dated("st.created_at")
            + " ORDER BY st.deadline_at ASC\n"
            + " LIMIT :limit\n";

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * @param scopeUserId records assigned to this user, or null for every record
     * @param from        earliest {@code created_at} to count — <b>required</b>
     * @param to          latest {@code created_at} to count — <b>required</b>
     *
     * <p><b>The bounds must not be null.</b> The statement compares them directly, with no
     * {@code IS NULL} guard, because a parameter PostgreSQL only sees in {@code ? IS NULL} cannot
     * be typed at prepare time — see {@link #dated}. A null therefore makes every comparison
     * {@code NULL}, and all eight branches return nothing at all: an empty snapshot rather than an
     * unbounded one, with no error to notice. Pass {@code ChatDateRange.start}/{@code end}, which
     * substitute far-past and far-future sentinels for an open-ended side.
     */
    public ChatCounts countAll(UUID scopeUserId, OffsetDateTime from, OffsetDateTime to) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("scope", scopeUserId != null ? scopeUserId.toString() : null)
                .addValue("from", from)
                .addValue("to", to);

        Map<CrmArea, List<StatusBucket>> byArea = new EnumMap<>(CrmArea.class);
        long[] overdue = {0};
        long[] lowRated = {0};

        jdbc.query(COUNT_ALL, params, rs -> {
            CrmArea area = CrmArea.valueOf(rs.getString("area"));
            String status = rs.getString("status");
            long count = rs.getLong("cnt");
            if (OVERDUE_MARKER.equals(status)) {
                overdue[0] = count;
                return;
            }
            if (LOW_RATING_MARKER.equals(status)) {
                lowRated[0] = count;
                return;
            }
            byArea.computeIfAbsent(area, a -> new ArrayList<>())
                    .add(new StatusBucket(status, count, rs.getBigDecimal("amount")));
        });

        return new ChatCounts(byArea, overdue[0], lowRated[0]);
    }

    /**
     * Unresolved SLA tracking rows within the caller's scope, earliest deadline first.
     *
     * @param scopeUserId records whose subject is assigned to this user, or null for every record
     * @param from        earliest {@code created_at} — <b>required</b>, see {@link #countAll}
     * @param to          latest {@code created_at} — <b>required</b>, see {@link #countAll}
     * @param limit       hard cap, applied in SQL
     */
    public List<SlaRow> unresolvedSla(UUID scopeUserId, OffsetDateTime from, OffsetDateTime to,
                                      int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("scope", scopeUserId != null ? scopeUserId.toString() : null)
                .addValue("from", from)
                .addValue("to", to)
                .addValue("limit", limit);

        return jdbc.query(SLA_LISTING, params, (rs, i) -> new SlaRow(
                rs.getString("activity_type"),
                rs.getString("entity_type"),
                rs.getString("status"),
                rs.getObject("deadline_at", OffsetDateTime.class),
                rs.getString("assignee")));
    }

    /** The statement, for the test that checks every area is represented in it. */
    public static String countAllSql() {
        return COUNT_ALL;
    }

    /** The listing statement, for the test that checks its scoping mirrors the count branch. */
    public static String slaListingSql() {
        return SLA_LISTING;
    }
}
