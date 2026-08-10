package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.application.usecase.chat.dto.ChatCounts;
import com.novax.leadora.application.usecase.chat.intent.CrmArea;
import com.novax.leadora.application.usecase.chat.time.ChatDateRange;
import com.novax.leadora.infrastructure.persistence.entity.enums.TaskStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Executes every chat query against a real PostgreSQL, which is the only way this class of failure
 * shows up.
 *
 * <p>{@code ChatQueryCompilationTest} parses the declared JPQL but never runs it, and the unit
 * tests mock the repositories away, so a statement can pass the entire suite and still fail on
 * every call. That happened: the period filter was written as
 * {@code (:from IS NULL OR created_at >= :from)}, PostgreSQL could not infer a type for a parameter
 * whose only use is {@code ? IS NULL}, and all ten listings began failing with
 * <i>could not determine data type of parameter</i> — at prepare time, so regardless of the value
 * bound. Retrieval is best-effort and swallows failures, so nothing surfaced: the assistant simply
 * answered "no data" to everything.
 *
 * <p>Opt-in, because it needs credentials for a populated database. Run it with:
 * <pre>  RUN_DB_IT=true ./mvnw test -Dtest=ChatQueryExecutionIT</pre>
 *
 * <p>It asserts only that each statement <em>executes</em>. Row counts belong to whatever data the
 * database happens to hold, and asserting on those would make the test fail for the wrong reason.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=none")
@Import({ChatAggregateRepository.class, ChatQueryExecutionIT.JdbcCfg.class})
@EnabledIfEnvironmentVariable(named = "RUN_DB_IT", matches = "true")
class ChatQueryExecutionIT {

    /** {@code @DataJpaTest} gives a DataSource but not the named-parameter template. */
    static class JdbcCfg {
        @Bean
        NamedParameterJdbcTemplate namedParameterJdbcTemplate(DataSource dataSource) {
            return new NamedParameterJdbcTemplate(dataSource);
        }
    }

    private static final Pageable PAGE = PageRequest.of(0, 5);
    private static final List<TaskStatus> CLOSED =
            List.of(TaskStatus.COMPLETED, TaskStatus.CANCELLED);

    @Autowired private ChatAggregateRepository aggregates;
    @Autowired private LeadRepository leads;
    @Autowired private DealRepository deals;
    @Autowired private TaskRepository tasks;
    @Autowired private QuotationRepository quotations;
    @Autowired private BookingRepository bookings;
    @Autowired private PaymentRepository payments;
    @Autowired private CustomerRepository customers;

    /** The bounds production uses: an open-ended side resolves to a sentinel, never to null. */
    private final ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
    private final OffsetDateTime from = ChatDateRange.allTime().start(zone);
    private final OffsetDateTime to = ChatDateRange.allTime().end(zone);

    @Test
    @DisplayName("every chat query runs against a real database, unfiltered")
    void allChatQueriesExecute() {
        assertThatCode(() -> {
            ChatCounts counts = aggregates.countAll(null, from, to);
            counts.total(CrmArea.LEADS);
            aggregates.unresolvedSla(null, from, to, 5);
            leads.findRecentForChat(null, from, to, PAGE);
            deals.findRecentForChat(null, from, to, PAGE);
            tasks.findOpenForChat(null, CLOSED, from, to, PAGE);
            quotations.findRecentForChat(null, from, to, PAGE);
            bookings.findRecentForChat(null, from, to, PAGE);
            payments.findRecentForChat(null, from, to, PAGE);
            customers.findRecentForChat(null, from, to, PAGE);
            deals.statsPerAssignee(from, to);
            leads.countPerAssignee(from, to, PAGE);
        }).doesNotThrowAnyException();
    }

    /** A real, narrow window exercises the same statements with bound values rather than sentinels. */
    @Test
    @DisplayName("every chat query runs with a bounded period too")
    void allChatQueriesExecuteWithABoundedPeriod() {
        OffsetDateTime start = OffsetDateTime.now(zone).minusDays(30);
        OffsetDateTime end = OffsetDateTime.now(zone);
        assertThatCode(() -> {
            aggregates.countAll(null, start, end);
            aggregates.unresolvedSla(null, start, end, 5);
            leads.findRecentForChat(null, start, end, PAGE);
            deals.findRecentForChat(null, start, end, PAGE);
            tasks.findOpenForChat(null, CLOSED, start, end, PAGE);
            quotations.findRecentForChat(null, start, end, PAGE);
            bookings.findRecentForChat(null, start, end, PAGE);
            payments.findRecentForChat(null, start, end, PAGE);
            customers.findRecentForChat(null, start, end, PAGE);
            deals.statsPerAssignee(start, end);
            leads.countPerAssignee(start, end, PAGE);
        }).doesNotThrowAnyException();
    }
}
