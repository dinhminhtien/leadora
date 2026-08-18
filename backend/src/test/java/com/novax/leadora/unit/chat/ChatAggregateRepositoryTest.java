package com.novax.leadora.unit.chat;
import com.novax.leadora.infrastructure.persistence.repository.*;

import com.novax.leadora.application.usecase.chat.intent.CrmArea;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Structural guard for the one statement that is native SQL rather than JPQL.
 *
 * <p>{@code ChatQueryCompilationTest} validates every declared JPQL query against the entity
 * model without a database; native SQL gets no such check, and a mistake in it surfaces only when
 * somebody asks the assistant a question. These assertions cannot prove the SQL is correct — only
 * a real database can, and it was verified against one — but they do catch the mistake most likely
 * to be made later: adding a {@link CrmArea} and forgetting that this statement has to grow a
 * branch for it, which would silently report the new area as permanently empty.
 */
class ChatAggregateRepositoryTest {

    @ParameterizedTest(name = "{0} has a branch in the batched count")
    @EnumSource(CrmArea.class)
    void everyAreaIsCounted(CrmArea area) {
        // Areas that declare themselves not counted per user are exempt by design: they hold no
        // per-assignee rows, so a scoped branch would have nothing to count. The exemption lives
        // on the enum rather than in a name list here, so a new area cannot slip past this check
        // by accident — it has to say which kind it is.
        assumeTrue(area.countedPerUser(), area + " is not a per-user record area");

        assertThat(ChatAggregateRepository.countAllSql())
                .as("adding an area means adding a UNION branch, or it always reports zero")
                .contains("'" + area.name() + "'");
    }

    /**
     * The period predicate is assembled by concatenating text blocks — exactly how the "ORCOALESCE"
     * fusion this file already guards against was introduced. The statement is printed on failure
     * because a malformed date filter satisfies a naive contains() check and surfaces only as a
     * wrong answer against a real database.
     */
    @Test
    @DisplayName("every branch is filtered on its own date column, on both bounds")
    void everyBranchIsDated() {
        String sql = ChatAggregateRepository.countAllSql();
        // Feedback is the one branch not dated on created_at: a row exists from the moment the
        // survey link is issued and holds an answer only once the customer sends one, so
        // submitted_at is when the thing being counted happened.
        for (String column : new String[]{"l.created_at", "d.created_at", "t.created_at",
                "q.created_at", "b.created_at", "p.created_at", "c.created_at", "st.created_at",
                "f.submitted_at"}) {
            assertThat(sql).as("lower bound on %s in:%n%s", column, sql)
                    .contains("AND " + column + " >= :from");
            assertThat(sql).as("upper bound on %s in:%n%s", column, sql)
                    .contains("AND " + column + " <= :to");
        }
        // A predicate glued onto the neighbouring token still satisfies the checks above while
        // being invalid SQL.
        assertThat(sql).doesNotContain(":fromAND").doesNotContain(":toGROUP")
                .doesNotContain(":toUNION").doesNotContain("now()AND");
    }

    /**
     * No {@code IS NULL} guard on a date bound, anywhere.
     *
     * <p>It reads as harmless defensive SQL and is not: PostgreSQL types a parameter when it
     * prepares the statement, and one that only ever appears in {@code ? IS NULL} gives it nothing
     * to infer from. Every such query failed with <i>could not determine data type of parameter</i>
     * on every call. The bounds are always supplied now — {@code ChatDateRange} substitutes
     * sentinels — so the guard is both unnecessary and dangerous to reintroduce.
     */
    @Test
    @DisplayName("no date bound is guarded with IS NULL")
    void noUntypeableNullGuards() {
        for (String sql : new String[]{ChatAggregateRepository.countAllSql(),
                                       ChatAggregateRepository.slaListingSql()}) {
            assertThat(sql).doesNotContain(":from IS NULL").doesNotContain(":to IS NULL");
        }
    }

    /** The listing must be filtered exactly like its count branch, or the header misreports it. */
    @Test
    @DisplayName("the SLA listing carries the same date filter as its count branch")
    void slaListingIsDated() {
        assertThat(ChatAggregateRepository.slaListingSql())
                .contains("AND st.created_at >= :from")
                .contains("AND st.created_at <= :to")
                .doesNotContain("'RESOLVED'AND")
                .doesNotContain(":toORDER");
    }

    @Test
    @DisplayName("every branch is scoped, so no branch can leak another user's records")
    void everyBranchIsScoped() {
        String sql = ChatAggregateRepository.countAllSql();
        long branches = sql.lines().filter(l -> l.stripLeading().startsWith("SELECT")).count();
        long scopeChecks = sql.lines().filter(l -> l.contains("CAST(:scope AS uuid) IS NULL")).count();

        // Eight record areas, plus the two derived rows (overdue tasks, low-rated feedback), plus
        // the feedback branch itself.
        assertThat(branches).isEqualTo(11);
        assertThat(scopeChecks)
                .as("BR-36: a branch without the scope predicate would return everyone's rows")
                .isEqualTo(branches);
    }

    /**
     * Feedback is scoped through the rep it is about, not through an {@code assigned_user_id}.
     *
     * <p>Asserted by name because it is the one area whose owner column is different, and a
     * copy-paste of a neighbouring branch would compile, run, and quietly show every rep their
     * colleagues' ratings.
     */
    @Test
    @DisplayName("feedback is scoped by the rep it is about")
    void feedbackIsScopedByItsSubject() {
        assertThat(ChatAggregateRepository.countAllSql())
                .contains("f.sales_staff_id = CAST(:scope AS uuid)")
                .contains("f.submitted_at IS NOT NULL");
    }

    /** Derived like overdue tasks: nothing stores "the customer was unhappy". */
    @Test
    @DisplayName("low customer ratings are derived, not read from a stored status")
    void lowRatingIsDerived() {
        assertThat(ChatAggregateRepository.countAllSql())
                .contains(ChatAggregateRepository.LOW_RATING_MARKER)
                .contains("f.rating <= 2");
    }

    @Test
    @DisplayName("overdue tasks are derived, not read from a stored status")
    void overdueIsDerived() {
        assertThat(ChatAggregateRepository.countAllSql())
                .contains(ChatAggregateRepository.OVERDUE_MARKER)
                .contains("t.status NOT IN ('COMPLETED', 'CANCELLED')")
                .contains("t.end_at < now()");
    }

    @Test
    @DisplayName("quotations and payments are scoped through their parent record")
    void inheritedScopingIsPresent() {
        String sql = ChatAggregateRepository.countAllSql();
        // Neither table has an assignee column; both parents are NOT NULL so the join drops nothing.
        assertThat(sql).contains("JOIN deals qd ON qd.deal_id = q.deal_id");
        assertThat(sql).contains("JOIN bookings pb ON pb.booking_id = p.booking_id");
        assertThat(sql).contains("qd.assigned_user_id");
        assertThat(sql).contains("pb.assigned_user_id");
    }

    /**
     * SLA tracking is the one area whose subject is polymorphic: a row points at a lead, task,
     * quotation or booking through {@code entity_type}/{@code entity_id} and has no assignee of
     * its own. Dropping one of those joins would not fail — it would silently stop attributing
     * that kind of SLA to anybody, so those rows would vanish for every non-manager.
     */
    @Test
    @DisplayName("SLA ownership resolves through all four subject types, in count and listing alike")
    void slaPolymorphicScoping() {
        for (String sql : new String[]{ChatAggregateRepository.countAllSql(),
                                       ChatAggregateRepository.slaListingSql()}) {
            assertThat(sql).contains("sl.lead_id      = st.entity_id");
            assertThat(sql).contains("stk.task_id     = st.entity_id");
            assertThat(sql).contains("sq.quotation_id = st.entity_id");
            assertThat(sql).contains("sb.booking_id   = st.entity_id");
            assertThat(sql)
                    .as("ownership must be resolved per entity type consistently in both statements")
                    .contains("st.entity_type = 'LEAD' AND sl.assigned_user_id")
                    .contains("st.entity_type = 'TASK' AND stk.assigned_user_id")
                    .contains("st.entity_type = 'QUOTATION' AND sq.created_by")
                    .contains("st.entity_type = 'BOOKING' AND sb.assigned_user_id");
        }
    }

    @Test
    @DisplayName("the SLA area reads sla_tracking, not the unmaintained sla_records table")
    void slaReadsTheMaintainedTable() {
        // sla_records has no scheduler keeping it current; the SLA Control screen and the breach
        // scheduler both work on sla_tracking. Reading the other one makes the assistant contradict
        // the very screen it links to.
        assertThat(ChatAggregateRepository.countAllSql())
                .contains("FROM sla_tracking st")
                .doesNotContain("sla_records");
    }

    @Test
    @DisplayName("the SLA listing excludes resolved rows and caps in SQL")
    void slaListingIsBoundedAndActionable() {
        assertThat(ChatAggregateRepository.slaListingSql())
                .contains("st.status <> 'RESOLVED'")
                .contains("ORDER BY st.deadline_at ASC")
                .contains("LIMIT :limit");
    }

    /**
     * Regression: the listing is assembled by concatenating a text block, which strips trailing
     * whitespace off each line. An earlier form fused "OR" onto the ownership expression
     * ("ORCOALESCE"), valid Java that only failed as SQL at runtime. Keep the tokens separated.
     */
    @Test
    @DisplayName("every SQL keyword in the SLA listing keeps its separating space")
    void slaListingHasNoFusedKeywords() {
        assertThat(ChatAggregateRepository.slaListingSql())
                .contains("IS NULL OR")
                .doesNotContain("NULLOR");
    }
}
