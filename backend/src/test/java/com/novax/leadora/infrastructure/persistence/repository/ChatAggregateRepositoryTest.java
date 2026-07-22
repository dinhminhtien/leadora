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

        assertThat(branches).isEqualTo(8); // seven areas plus the derived overdue-task row
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
}
