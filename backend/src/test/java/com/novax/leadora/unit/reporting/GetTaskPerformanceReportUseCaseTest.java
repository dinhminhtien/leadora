package com.novax.leadora.unit.reporting;
import com.novax.leadora.application.usecase.reporting.*;

import com.novax.leadora.api.dto.response.TaskPerformanceReportResponse;
import com.novax.leadora.api.dto.response.TaskPerformanceReportResponse.CountRow;
import com.novax.leadora.api.dto.response.TaskPerformanceReportResponse.StaffRow;
import com.novax.leadora.common.util.ReportRangeFactory;
import com.novax.leadora.infrastructure.persistence.entity.RoleEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.repository.TaskPerformanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** UC-23.2 — role scoping, the three time axes, and the punctuality figure the old report lacked. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GetTaskPerformanceReportUseCaseTest {

    @Mock
    private TaskPerformanceRepository taskPerformanceRepository;

    private GetTaskPerformanceReportUseCase useCase;

    /** [metric, bucket, ownerId, ownerName, count, value] */
    private final List<Object[]> rows = new ArrayList<>();
    /** [ownerId, ownerName, status, warningAt, deadlineAt, resolvedAt] */
    private final List<Object[]> slaRows = new ArrayList<>();

    private static final UUID ANNA = UUID.randomUUID();
    private static final UUID BINH = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new GetTaskPerformanceReportUseCase(
                taskPerformanceRepository, new ReportRangeFactory("Asia/Ho_Chi_Minh"));
        when(taskPerformanceRepository.taskPerformanceAggregates(any(), any(), any(), any()))
                .thenReturn(rows);
        when(taskPerformanceRepository.taskSlaRows(any(), any(), any())).thenReturn(slaRows);
    }

    private void agg(String metric, String bucket, long count) {
        rows.add(new Object[] { metric, bucket, null, null, count, 0.0 });
    }

    private void agg(String metric, String bucket, long count, double value) {
        rows.add(new Object[] { metric, bucket, null, null, count, value });
    }

    private void agg(String metric, String bucket, UUID id, String name, long count) {
        rows.add(new Object[] { metric, bucket, id, name, count, 0.0 });
    }

    private void agg(String metric, String bucket, UUID id, String name, long count, double value) {
        rows.add(new Object[] { metric, bucket, id, name, count, value });
    }

    private void sla(String status, OffsetDateTime deadline, OffsetDateTime resolved) {
        slaRows.add(new Object[] { ANNA, "Anna", status, deadline.minusHours(1), deadline, resolved });
    }

    private static UserEntity user(String roleName) {
        RoleEntity role = new RoleEntity();
        role.setRoleName(roleName);
        UserEntity user = new UserEntity();
        user.setUserId(UUID.randomUUID());
        user.setRole(role);
        return user;
    }

    private TaskPerformanceReportResponse run() {
        return useCase.execute(user("MANAGER"), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
    }

    private static long countOf(List<CountRow> list, String key) {
        return list.stream().filter(r -> key.equals(r.getKey())).mapToLong(r -> r.getCount()).findFirst().orElse(0L);
    }

    // ── The defect this rewrite exists for ───────────────────────────────────

    @Test
    @DisplayName("a task finished late is counted as late, not erased by having been finished")
    void finishingLateIsStillLate() {
        // Ten tasks raised, all completed, every single one after its deadline.
        agg("RAISED", "COMPLETED", 10);
        agg("RESOLVED", "LATE", 10, 50.0);

        TaskPerformanceReportResponse report = run();

        assertThat(report.getResolvedLate())
                .as("the old report showed 0 overdue here — completing a task removed it from the count")
                .isEqualTo(10);
        assertThat(report.getPunctualityRate()).isZero();
        assertThat(report.getResolvedLate()).isEqualTo(10);
        assertThat(report.getCompletionRate())
                .as("everything was completed, which is true and is a different question")
                .isEqualTo(100.0);
        assertThat(report.getOpenOverdue())
                .as("nothing is still running late, which is also true")
                .isZero();
    }

    @Test
    @DisplayName("punctuality and the open queue are separate figures with separate meanings")
    void queueAndPunctualityDoNotShareAFigure() {
        agg("RAISED", "OPEN", 8);
        agg("RAISED", "COMPLETED", 12);
        agg("OPEN_OVERDUE", "D1_3", 3, 2.0);
        agg("RESOLVED", "ON_TIME", 9, 10.0);
        agg("RESOLVED", "LATE", 3, 40.0);

        TaskPerformanceReportResponse report = run();

        assertThat(report.getOpenOverdue()).isEqualTo(3);
        assertThat(report.getOverdueRate()).as("3 of the 8 still open").isEqualTo(37.5);
        assertThat(report.getPunctualityRate()).as("9 of the 12 judged").isEqualTo(75.0);
    }

    @Test
    @DisplayName("a task finished with no deadline is held out of punctuality, not counted as on time")
    void tasksWithoutADeadlineAreNotCreditedAsPunctual() {
        agg("RESOLVED", "ON_TIME", 1);
        agg("RESOLVED", "LATE", 1);
        agg("RESOLVED", "UNDATED", 98);

        TaskPerformanceReportResponse report = run();

        assertThat(report.getResolvedTotal()).isEqualTo(100);
        assertThat(report.getPunctualityRate())
                .as("counting the 98 as on time would have printed 99%")
                .isEqualTo(50.0);
        assertThat(report.getResolvedNoDeadline()).isEqualTo(98);
    }

    @Test
    @DisplayName("completed tasks with no completion time are reported, not guessed at")
    void undatedCompletionsAreDisclosed() {
        agg("RAISED", "COMPLETED", 64);
        agg("RAISED_COMPLETED_UNDATED", "UNDATED", 33);

        TaskPerformanceReportResponse report = run();

        assertThat(report.getCompletedUndated()).isEqualTo(33);
        assertThat(report.getPunctualityCoverage())
                .as("31 of 64 — the figure has to travel with how much of the history it covers")
                .isEqualTo(48.44);
    }

    // ── The new dimensions ───────────────────────────────────────────────────

    @Test
    @DisplayName("overdue is broken down by priority, which is where it matters")
    void overdueIsCrossedWithPriority() {
        agg("OPEN_OVERDUE_PRIORITY", "HIGH", 3);
        agg("OPEN_OVERDUE_PRIORITY", "LOW", 12);

        List<CountRow> byPriority = run().getOverdueByPriority();

        assertThat(byPriority).extracting(r -> r.getKey())
                .as("HIGH first — the order must not change between periods")
                .containsExactly("HIGH", "LOW");
        assertThat(countOf(byPriority, "HIGH")).isEqualTo(3);
    }

    @Test
    @DisplayName("aging bands keep their order and carry the average with them")
    void overdueAgingIsOrdered() {
        agg("OPEN_OVERDUE", "D8_PLUS", 2, 30.0);
        agg("OPEN_OVERDUE", "D1_3", 6, 2.0);

        TaskPerformanceReportResponse report = run();

        assertThat(report.getOverdueAging()).extracting(r -> r.getKey())
                .containsExactly("D1_3", "D8_PLUS");
        assertThat(report.getOpenOverdue()).isEqualTo(8);
        assertThat(report.getAvgDaysOverdue())
                .as("weighted by group size, not the mean of two group averages")
                .isEqualTo(9.0);
    }

    @Test
    @DisplayName("the activity mix shows what kind of follow-up work is being done")
    void activityMixIsReported() {
        agg("RAISED_ACTIVITY", "CALL", 20);
        agg("RAISED_ACTIVITY", "SITE_VISIT", 2);

        List<CountRow> mix = run().getActivityMix();

        assertThat(mix).extracting(r -> r.getLabel()).contains("Call", "Site visit");
        assertThat(countOf(mix, "SITE_VISIT")).isEqualTo(2);
    }

    @Test
    @DisplayName("tasks attached to no lead, customer or deal are surfaced")
    void orphanTasksAreReported() {
        agg("RAISED", "OPEN", 10);
        agg("RAISED_LINKAGE", "LINKED", 6);
        agg("RAISED_LINKAGE", "ORPHAN", 4);

        TaskPerformanceReportResponse report = run();

        assertThat(report.getOrphanTasks()).isEqualTo(4);
        assertThat(report.getOrphanRate()).isEqualTo(40.0);
    }

    @Test
    @DisplayName("SLA is classified by the same rules as UC-23.3, so a resolved breach is still a breach")
    void slaUsesTheSharedClassifier() {
        OffsetDateTime deadline = OffsetDateTime.now().minusDays(2);
        sla("RESOLVED", deadline, deadline.minusHours(3));  // on time
        sla("RESOLVED", deadline, deadline.plusHours(5));   // resolved, late
        sla("IN_PROGRESS", deadline, null);                 // open, overdue
        sla("RESOLVED", deadline, null);                    // no timestamp: undetermined

        TaskPerformanceReportResponse report = run();

        assertThat(report.getSlaDecided())
                .as("the undetermined row is held out of the denominator")
                .isEqualTo(3);
        assertThat(report.getSlaOnTime()).isEqualTo(1);
        assertThat(report.getSlaComplianceRate()).isEqualTo(33.33);
    }

    // ── Per-staff breakdown ──────────────────────────────────────────────────

    @Test
    @DisplayName("one staff member's metrics merge into a single row")
    void metricsMergePerStaffMember() {
        agg("RAISED", "OPEN", ANNA, "Anna", 6);
        agg("RAISED", "COMPLETED", ANNA, "Anna", 4);
        agg("OPEN_OVERDUE", "D1_3", ANNA, "Anna", 2, 2.0);
        agg("RESOLVED", "ON_TIME", ANNA, "Anna", 3, 12.0);
        agg("RESOLVED", "LATE", ANNA, "Anna", 1, 60.0);

        assertThat(run().getStaff()).singleElement().satisfies(row -> {
            assertThat(row.getName()).isEqualTo("Anna");
            assertThat(row.getTotal()).isEqualTo(10);
            assertThat(row.getCompleted()).isEqualTo(4);
            assertThat(row.getOpenOverdue()).isEqualTo(2);
            assertThat(row.getResolvedLate()).isEqualTo(1);
            assertThat(row.getPunctualityRate()).isEqualTo(75.0);
            assertThat(row.getAvgCycleHours()).isEqualTo(24.0);
        });
    }

    @Test
    @DisplayName("staff are ranked by punctuality, not by how many tasks they raised")
    void staffAreRankedByPunctualityNotVolume() {
        agg("RAISED", "OPEN", ANNA, "Anna", 50);
        agg("RESOLVED", "LATE", ANNA, "Anna", 8);
        agg("RAISED", "OPEN", BINH, "Binh", 4);
        agg("RESOLVED", "ON_TIME", BINH, "Binh", 4);

        assertThat(run().getStaff()).extracting(r -> r.getName())
                .as("ranking on volume rewards whoever logged the most work, not who did it well")
                .containsExactly("Binh", "Anna");
    }

    @Test
    @DisplayName("a staff member with nothing datable sorts last rather than at either extreme")
    void unmeasurableStaffSortLast() {
        agg("RAISED", "OPEN", ANNA, "Anna", 5);
        agg("RAISED", "OPEN", BINH, "Binh", 5);
        agg("RESOLVED", "LATE", BINH, "Binh", 5);

        List<StaffRow> staff = run().getStaff();

        assertThat(staff).extracting(r -> r.getName()).containsExactly("Binh", "Anna");
        assertThat(staff.get(1).getPunctualityRate())
                .as("no judged outcome is null, never zero")
                .isNull();
    }

    @Test
    @DisplayName("unassigned tasks get their own row so the breakdown reconciles with the total")
    void unassignedTasksGetTheirOwnRow() {
        agg("RAISED", "OPEN", ANNA, "Anna", 7);
        agg("RAISED", "OPEN", 3);   // nobody assigned

        TaskPerformanceReportResponse report = run();

        assertThat(report.getTotalTasks()).isEqualTo(10);
        assertThat(report.getStaff()).hasSize(2);
        assertThat(report.getStaff().stream().mapToLong(r -> r.getTotal()).sum())
                .isEqualTo(report.getTotalTasks());
        assertThat(report.getStaff())
                .filteredOn(r -> r.isUnassigned())
                .singleElement()
                .satisfies(row -> assertThat(row.getName()).isEqualTo("(Unassigned)"));
    }

    // ── Scoping ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("a manager sees the whole team — no owner filter reaches the query")
    void managerScopeIsTeamWide() {
        useCase.execute(user("MANAGER"), null, null);

        verify(taskPerformanceRepository).taskPerformanceAggregates(isNull(), any(), any(), any());
    }

    @Test
    @DisplayName("a sales user is scoped to their own tasks, in SQL")
    void salesScopeIsOwnTasksOnly() {
        UserEntity sales = user("SALES");

        TaskPerformanceReportResponse report = useCase.execute(sales, null, null);

        ArgumentCaptor<String> owner = ArgumentCaptor.forClass(String.class);
        verify(taskPerformanceRepository).taskPerformanceAggregates(owner.capture(), any(), any(), any());
        assertThat(owner.getValue()).isEqualTo(sales.getUserId().toString());
        assertThat(report.isOwnScope())
                .as("the screen has to say which scope it is showing")
                .isTrue();
    }

    @Test
    @DisplayName("the SLA rows are scoped the same way as the aggregates")
    void slaRowsFollowTheSameScope() {
        UserEntity sales = user("SALES");

        useCase.execute(sales, null, null);

        verify(taskPerformanceRepository).taskSlaRows(
                org.mockito.ArgumentMatchers.eq(sales.getUserId().toString()), any(), any());
    }

    @Test
    @DisplayName("the cache partitions team-wide viewers together and scoped users apart")
    void cacheKeyFollowsTheScopeRule() {
        UserEntity sales = user("SALES");
        UserEntity otherSales = user("SALES");

        assertThat(GetTaskPerformanceReportUseCase.scopeKey(user("MANAGER"))).isEqualTo("all");
        assertThat(GetTaskPerformanceReportUseCase.scopeKey(user("ADMIN"))).isEqualTo("all");
        assertThat(GetTaskPerformanceReportUseCase.scopeKey(sales))
                .isNotEqualTo(GetTaskPerformanceReportUseCase.scopeKey(otherSales));
        assertThat(GetTaskPerformanceReportUseCase.scopeKey(user("  manager  "))).isEqualTo("all");
    }

    // ── Shape ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("the whole report costs two round trips")
    void reportIsTwoRoundTrips() {
        run();

        verify(taskPerformanceRepository, times(1)).taskPerformanceAggregates(any(), any(), any(), any());
        verify(taskPerformanceRepository, times(1)).taskSlaRows(any(), any(), any());
    }

    @Test
    @DisplayName("the report names the time zone its day boundaries were cut in")
    void reportStatesItsTimeZone() {
        assertThat(run().getTimezone()).isEqualTo("Asia/Ho_Chi_Minh");
    }

    @Test
    @DisplayName("an empty period renders zeroes rather than dividing by zero")
    void emptyPeriodIsSafe() {
        TaskPerformanceReportResponse report = run();

        assertThat(report.getTotalTasks()).isZero();
        assertThat(report.getCompletionRate()).isZero();
        assertThat(report.getPunctualityRate())
                .as("an empty period judged nobody — that is not the same as everybody being late")
                .isNull();
        assertThat(report.getOverdueRate()).isZero();
        assertThat(report.getStaff()).isEmpty();
    }

    @Test
    @DisplayName("the @Cacheable key expression parses and evaluates against a real caller")
    void cacheKeyExpressionIsValid() throws Exception {
        // A broken SpEL expression in a cache key fails neither the compiler nor any test that
        // calls the method directly — Spring only evaluates it behind the proxy, on the first real
        // request. The expression is read off the annotation rather than retyped here, so this
        // cannot drift away from the one that actually runs.
        String expression = GetTaskPerformanceReportUseCase.class
                .getMethod("execute", UserEntity.class, LocalDate.class, LocalDate.class)
                .getAnnotation(org.springframework.cache.annotation.Cacheable.class)
                .key();

        var ctx = new org.springframework.expression.spel.support.StandardEvaluationContext();
        ctx.setVariable("actor", user("MANAGER"));
        ctx.setVariable("from", LocalDate.of(2026, 7, 1));
        ctx.setVariable("to", LocalDate.of(2026, 7, 31));

        Object key = new org.springframework.expression.spel.standard.SpelExpressionParser()
                .parseExpression(expression).getValue(ctx);

        assertThat(key)
                .as("every team-wide viewer shares one entry rather than one entry per manager")
                .isEqualTo("all_2026-07-01_2026-07-31");
    }

    @Test
    @DisplayName("two scoped users never share a cache entry")
    void scopedUsersGetSeparateCacheEntries() throws Exception {
        String expression = GetTaskPerformanceReportUseCase.class
                .getMethod("execute", UserEntity.class, LocalDate.class, LocalDate.class)
                .getAnnotation(org.springframework.cache.annotation.Cacheable.class)
                .key();
        var parser = new org.springframework.expression.spel.standard.SpelExpressionParser();

        java.util.function.Function<UserEntity, Object> keyFor = actor -> {
            var ctx = new org.springframework.expression.spel.support.StandardEvaluationContext();
            ctx.setVariable("actor", actor);
            ctx.setVariable("from", LocalDate.of(2026, 7, 1));
            ctx.setVariable("to", LocalDate.of(2026, 7, 31));
            return parser.parseExpression(expression).getValue(ctx);
        };

        assertThat(keyFor.apply(user("SALES")))
                .as("one salesperson's task counts must never be served to another")
                .isNotEqualTo(keyFor.apply(user("SALES")));
    }

    @Test
    @DisplayName("nothing finished with a deadline gives no punctuality figure, not a zero")
    void noJudgeableWorkGivesNoPunctualityFigure() {
        // Every completed task in the period had no due date set. Printing 0.0% under a red meter
        // would read as total lateness — the same accidental bias this report exists to remove,
        // pointing the other way.
        agg("RESOLVED", "UNDATED", 12);

        TaskPerformanceReportResponse report = run();

        assertThat(report.getResolvedTotal()).isEqualTo(12);
        assertThat(report.getPunctualityRate()).isNull();
    }

    @Test
    @DisplayName("a reopened task stops counting as finished work")
    void reopenedTasksLeaveTheResolvedAxis() {
        // TaskEntity clears completed_at when the status leaves COMPLETED, and the query also
        // guards on the status, so a task completed in July and reopened in August cannot be
        // counted as both finished and overdue in the same report.
        agg("RAISED", "OPEN", 1);
        agg("OPEN_OVERDUE", "D1_3", 1, 2.0);

        TaskPerformanceReportResponse report = run();

        assertThat(report.getResolvedTotal()).isZero();
        assertThat(report.getOpenOverdue()).isEqualTo(1);
    }
}
