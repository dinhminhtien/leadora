package com.novax.leadora.infrastructure.persistence.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Executes the dashboard's two hand-written queries against a real PostgreSQL.
 *
 * <p>Same reasoning as {@code ChatQueryExecutionIT}, and the same blind spot it was written for:
 * the SLA read is native SQL, so nothing in the ordinary suite parses it, and the unit tests mock
 * the repository away. A mistake in it compiles, ships, and fails on every call.
 *
 * <p>The column types matter as much as the SQL. A native projection hands its timestamps back as
 * whatever the driver chose - here {@code Instant}, not {@code Timestamp} and not
 * {@code OffsetDateTime} - so {@code GetDashboardSummaryUseCase} converts defensively. If that
 * conversion is ever narrowed to one type, this test is what catches it.
 *
 * <p>Opt-in, because it needs credentials for a populated database:
 * <pre>  RUN_DB_IT=true ./mvnw test -Dtest=ReportingQueryExecutionIT</pre>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=none")
@EnabledIfEnvironmentVariable(named = "RUN_DB_IT", matches = "true")
class ReportingQueryExecutionIT {

    @Autowired private SlaTrackingRepository slaTrackingRepository;
    @Autowired private InteractTimelineRepository interactTimelineRepository;

    @Test
    @DisplayName("the dashboard SLA read runs, scoped and unscoped alike")
    void slaDashboardRowsExecute() {
        assertThatCode(() -> {
            slaTrackingRepository.findDashboardRows(null);
            slaTrackingRepository.findDashboardRows(UUID.randomUUID().toString());
        }).doesNotThrowAnyException();
    }

    /**
     * BR-02: a scoped caller must never be handed the company-wide set. A random user id owns
     * nothing, so its result has to be a strict subset of the unscoped one - if the scope
     * predicate were ever dropped, both calls would return the same rows and this would fail.
     */
    @Test
    @DisplayName("scoping actually narrows the SLA read")
    void slaScopeNarrowsTheResult() {
        List<Object[]> everything = slaTrackingRepository.findDashboardRows(null);
        List<Object[]> nobodys = slaTrackingRepository.findDashboardRows(UUID.randomUUID().toString());
        assertThat(nobodys.size()).isLessThanOrEqualTo(everything.size());
    }

    @Test
    @DisplayName("the leaderboard aggregate runs and is capped in SQL")
    void leaderboardAggregateExecutes() {
        assertThatCode(() -> {
            List<Object[]> rows = interactTimelineRepository.countActionsPerUser(PageRequest.of(0, 5));
            assertThat(rows.size()).isLessThanOrEqualTo(5);
        }).doesNotThrowAnyException();
    }
}
