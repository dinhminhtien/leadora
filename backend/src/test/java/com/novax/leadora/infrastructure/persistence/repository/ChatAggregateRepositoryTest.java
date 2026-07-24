package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.application.usecase.chat.intent.CrmArea;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

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
        assertThat(ChatAggregateRepository.countAllSql())
                .as("adding an area means adding a UNION branch, or it always reports zero")
                .contains("'" + area.name() + "'");
    }

    @Test
    @DisplayName("every branch is scoped, so no branch can leak another user's records")
    void everyBranchIsScoped() {
        String sql = ChatAggregateRepository.countAllSql();
        long branches = sql.lines().filter(l -> l.stripLeading().startsWith("SELECT")).count();
        long scopeChecks = sql.lines().filter(l -> l.contains("CAST(:scope AS uuid) IS NULL")).count();

        assertThat(branches).isEqualTo(9); // eight areas plus the derived overdue-task row
        assertThat(scopeChecks)
                .as("BR-36: a branch without the scope predicate would return everyone's rows")
                .isEqualTo(branches);
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
                    .as("ownership must be the same COALESCE in both statements, or the count "
                            + "and the rows beneath it would disagree about scope")
                    .contains("COALESCE(sl.assigned_user_id, stk.assigned_user_id,");
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
                .contains("IS NULL OR COALESCE(")
                .doesNotContain("ORCOALESCE")
                .doesNotContain("NULLOR");
    }
}
