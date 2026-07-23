package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.application.usecase.chat.dto.ChatCounts;
import com.novax.leadora.application.usecase.chat.dto.StatusBucket;
import com.novax.leadora.application.usecase.chat.intent.CrmArea;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

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
 * <p>Scoping (BR-36) is a single {@code :scope} parameter, null meaning "every record". Quotations
 * and payments carry no assignee of their own and are reached through the deal and booking they
 * belong to; both parent columns are NOT NULL, so those joins cannot drop rows.
 */
@Repository
@RequiredArgsConstructor
public class ChatAggregateRepository {

    /** Marks the derived overdue-task row, which is a filter rather than a stored status. */
    static final String OVERDUE_MARKER = "__OVERDUE__";

    private static final String COUNT_ALL = """
            SELECT 'LEADS' AS area, l.status AS status, COUNT(*) AS cnt, NULL::numeric AS amount
              FROM leads l
             WHERE (CAST(:scope AS uuid) IS NULL OR l.assigned_user_id = CAST(:scope AS uuid))
             GROUP BY l.status
            UNION ALL
            SELECT 'DEALS', d.status, COUNT(*), COALESCE(SUM(d.expected_revenue), 0)
              FROM deals d
             WHERE (CAST(:scope AS uuid) IS NULL OR d.assigned_user_id = CAST(:scope AS uuid))
             GROUP BY d.status
            UNION ALL
            SELECT 'TASKS', t.status, COUNT(*), NULL
              FROM tasks t
             WHERE (CAST(:scope AS uuid) IS NULL OR t.assigned_user_id = CAST(:scope AS uuid))
             GROUP BY t.status
            UNION ALL
            SELECT 'TASKS', '__OVERDUE__', COUNT(*), NULL
              FROM tasks t
             WHERE (CAST(:scope AS uuid) IS NULL OR t.assigned_user_id = CAST(:scope AS uuid))
               AND t.status NOT IN ('COMPLETED', 'CANCELLED')
               AND t.end_at IS NOT NULL
               AND t.end_at < now()
            UNION ALL
            SELECT 'QUOTATIONS', q.status, COUNT(*), COALESCE(SUM(q.total_amount), 0)
              FROM quotations q
              JOIN deals qd ON qd.deal_id = q.deal_id
             WHERE (CAST(:scope AS uuid) IS NULL OR qd.assigned_user_id = CAST(:scope AS uuid))
             GROUP BY q.status
            UNION ALL
            SELECT 'BOOKINGS', b.status, COUNT(*), COALESCE(SUM(b.total_amount), 0)
              FROM bookings b
             WHERE (CAST(:scope AS uuid) IS NULL OR b.assigned_user_id = CAST(:scope AS uuid))
             GROUP BY b.status
            UNION ALL
            SELECT 'PAYMENTS', p.status, COUNT(*), COALESCE(SUM(p.amount), 0)
              FROM payments p
              JOIN bookings pb ON pb.booking_id = p.booking_id
             WHERE (CAST(:scope AS uuid) IS NULL OR pb.assigned_user_id = CAST(:scope AS uuid))
             GROUP BY p.status
            UNION ALL
            SELECT 'CUSTOMERS', c.status, COUNT(*), NULL
              FROM customers c
             WHERE (CAST(:scope AS uuid) IS NULL OR c.assigned_user_id = CAST(:scope AS uuid))
             GROUP BY c.status
            """;

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * @param scopeUserId records assigned to this user, or null for every record
     */
    public ChatCounts countAll(UUID scopeUserId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("scope", scopeUserId != null ? scopeUserId.toString() : null);

        Map<CrmArea, List<StatusBucket>> byArea = new EnumMap<>(CrmArea.class);
        long[] overdue = {0};

        jdbc.query(COUNT_ALL, params, rs -> {
            CrmArea area = CrmArea.valueOf(rs.getString("area"));
            String status = rs.getString("status");
            long count = rs.getLong("cnt");
            if (OVERDUE_MARKER.equals(status)) {
                overdue[0] = count;
                return;
            }
            byArea.computeIfAbsent(area, a -> new ArrayList<>())
                    .add(new StatusBucket(status, count, rs.getBigDecimal("amount")));
        });

        return new ChatCounts(byArea, overdue[0]);
    }

    /** The statement, for the test that checks every area is represented in it. */
    static String countAllSql() {
        return COUNT_ALL;
    }
}
