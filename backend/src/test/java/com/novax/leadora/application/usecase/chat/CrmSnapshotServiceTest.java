package com.novax.leadora.application.usecase.chat;

import com.novax.leadora.application.usecase.chat.dto.ChatCounts;
import com.novax.leadora.application.usecase.chat.dto.RepLeadCount;
import com.novax.leadora.application.usecase.chat.dto.StatusBucket;
import com.novax.leadora.application.usecase.chat.intent.CrmArea;
import com.novax.leadora.infrastructure.persistence.entity.LeadEntity;
import com.novax.leadora.infrastructure.persistence.entity.RoleEntity;
import com.novax.leadora.infrastructure.persistence.entity.TaskEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.LeadStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.TaskStatus;
import com.novax.leadora.infrastructure.persistence.repository.BookingRepository;
import com.novax.leadora.infrastructure.persistence.repository.ChatAggregateRepository;
import com.novax.leadora.infrastructure.persistence.repository.CustomerRepository;
import com.novax.leadora.infrastructure.persistence.repository.DealRepository;
import com.novax.leadora.infrastructure.persistence.repository.LeadRepository;
import com.novax.leadora.infrastructure.persistence.repository.PaymentRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationRepository;
import com.novax.leadora.infrastructure.persistence.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Behaviour of the reference block the assistant is given: what it says when a scope is empty,
 * what it refuses to say to a user who may not see it, and how much of a long list it shows.
 *
 * <p>The repositories are mocked. The queries themselves are covered elsewhere —
 * {@code ChatQueryCompilationTest} for the declared JPQL and {@code ChatAggregateRepositoryTest}
 * for the batched statement — and what matters here is the branching on top of them.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CrmSnapshotServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock private ChatAggregateRepository chatAggregateRepository;
    @Mock private LeadRepository leadRepository;
    @Mock private DealRepository dealRepository;
    @Mock private TaskRepository taskRepository;
    @Mock private QuotationRepository quotationRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private CustomerRepository customerRepository;

    private CrmSnapshotService service;

    @BeforeEach
    void setUp() {
        service = new CrmSnapshotService(chatAggregateRepository, leadRepository, dealRepository,
                taskRepository, quotationRepository, bookingRepository, paymentRepository,
                customerRepository);

        // Default: nothing anywhere. Individual tests fill in what they need.
        when(chatAggregateRepository.countAll(any())).thenReturn(counts(Map.of(), 0));
        when(leadRepository.findRecentForChat(any(), any())).thenReturn(List.of());
        when(dealRepository.findRecentForChat(any(), any())).thenReturn(List.of());
        when(taskRepository.findOpenForChat(any(), anyList(), any())).thenReturn(List.of());
        when(leadRepository.countPerAssignee(any())).thenReturn(List.of());
    }

    // ── fixtures ──────────────────────────────────────────────────────────────

    private static ChatCounts counts(Map<CrmArea, List<StatusBucket>> byArea, long overdue) {
        // Built key-first: EnumMap's copy constructor cannot infer the key type from an empty map.
        Map<CrmArea, List<StatusBucket>> map = new EnumMap<>(CrmArea.class);
        map.putAll(byArea);
        return new ChatCounts(map, overdue);
    }

    private static StatusBucket bucket(String status, long count) {
        return new StatusBucket(status, count, null);
    }

    private static ChatActor user(String role) {
        return ChatActor.from(UserEntity.builder()
                .userId(USER_ID)
                .fullName("Trần Nhật Minh")
                .role(RoleEntity.builder().roleId(1).roleName(role).build())
                .build());
    }

    /** The user's own scope: no leads, but a deal and some tasks — the shape real data takes. */
    private void givenOwnScopeHasDealsAndTasksButNoLeads() {
        when(chatAggregateRepository.countAll(eq(USER_ID))).thenReturn(counts(Map.of(
                CrmArea.DEALS, List.of(new StatusBucket("OPEN", 1, new BigDecimal("5000000"))),
                CrmArea.TASKS, List.of(bucket("OPEN", 2))), 0));
    }

    private void givenCompanyWideLeadsExist() {
        when(chatAggregateRepository.countAll(isNull())).thenReturn(counts(Map.of(
                CrmArea.LEADS, List.of(bucket("NEW", 8), bucket("CONVERTED", 12))), 0));
        when(leadRepository.countPerAssignee(any())).thenReturn(List.of(
                new RepLeadCount("Alice Smith", 6L),
                new RepLeadCount("Tiến Đinh", 4L)));
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Guidance is emitted per area")
    class PerArea {

        /**
         * The case that motivated per-area guidance: a Manager with no leads of their own but a
         * deal and two tasks. Requiring the whole snapshot to be empty meant this shape — the
         * common one in real data — produced no guidance at all.
         */
        @Test
        @DisplayName("empty leads still get guidance when deals and tasks have rows")
        void emptyLeadsGetGuidanceEvenWhenOtherAreasHaveData() {
            givenOwnScopeHasDealsAndTasksButNoLeads();
            givenCompanyWideLeadsExist();

            String snapshot = service.personalSnapshot(user("MANAGER"), CrmArea.defaults());

            assertThat(snapshot).contains("WHAT YOU MAY OFFER");
            assertThat(snapshot).contains("Empty for this scope: leads.");
            assertThat(snapshot).contains("Company-wide leads: 20");
            assertThat(snapshot).contains("Alice Smith=6");
        }

        @Test
        @DisplayName("areas that have data are not named as empty")
        void doesNotClaimNonEmptyAreasAreEmpty() {
            givenOwnScopeHasDealsAndTasksButNoLeads();
            givenCompanyWideLeadsExist();

            String snapshot = service.personalSnapshot(user("MANAGER"), CrmArea.defaults());

            assertThat(snapshot).doesNotContain("Empty for this scope: leads, deals, tasks.");
            assertThat(snapshot).doesNotContain("Company-wide deals:");
        }

        @Test
        @DisplayName("no guidance at all when every asked-about area has data")
        void noGuidanceWhenNothingIsEmpty() {
            when(chatAggregateRepository.countAll(eq(USER_ID))).thenReturn(counts(Map.of(
                    CrmArea.LEADS, List.of(bucket("NEW", 3)),
                    CrmArea.DEALS, List.of(bucket("OPEN", 1)),
                    CrmArea.TASKS, List.of(bucket("OPEN", 2))), 0));

            assertThat(service.personalSnapshot(user("MANAGER"), CrmArea.defaults()))
                    .doesNotContain("WHAT YOU MAY OFFER");
        }
    }

    @Nested
    @DisplayName("BR-36: guidance must not leak beyond the caller's scope")
    class ScopeLeakage {

        /**
         * Naming who does hold the leads would disclose exactly what a Sales user may not read,
         * so the suggestions have to stay inside their own scope.
         */
        @Test
        @DisplayName("a Sales user is never told about colleagues or company totals")
        void salesUserGetsNoColleagueNames() {
            givenCompanyWideLeadsExist();

            String snapshot = service.personalSnapshot(user("SALES"), CrmArea.defaults());

            assertThat(snapshot).contains("WHAT YOU MAY OFFER");
            assertThat(snapshot).doesNotContain("Alice Smith");
            assertThat(snapshot).doesNotContain("Company-wide");
            assertThat(snapshot).contains("do NOT name other staff members");
        }

        @Test
        @DisplayName("a Manager may be given the per-staff breakdown")
        void managerGetsColleagueNames() {
            givenCompanyWideLeadsExist();

            assertThat(service.personalSnapshot(user("MANAGER"), CrmArea.defaults()))
                    .contains("Alice Smith=6");
        }
    }

    @Nested
    @DisplayName("Scope selection")
    class Scope {

        @Test
        @DisplayName("personalSnapshot queries the user's own records, even for a Manager")
        void personalSnapshotIsPinnedToTheUser() {
            service.personalSnapshot(user("MANAGER"), CrmArea.defaults());
            // A null argument would mean "every record"; the personal snapshot must not use it.
            verify(chatAggregateRepository).countAll(eq(USER_ID));
        }

        @Test
        @DisplayName("scopedSnapshot widens to all records for a Manager")
        void scopedSnapshotWidensForManager() {
            service.scopedSnapshot(user("MANAGER"), CrmArea.defaults());
            verify(chatAggregateRepository).countAll(isNull());
        }

        @Test
        @DisplayName("scopedSnapshot stays personal for a Sales user")
        void scopedSnapshotStaysPersonalForSales() {
            service.scopedSnapshot(user("SALES"), CrmArea.defaults());
            verify(chatAggregateRepository).countAll(eq(USER_ID));
        }
    }

    @Nested
    @DisplayName("Detail is proportionate to the question")
    class AreaScoping {

        /**
         * Counts are one line each and let the assistant answer "how many bookings?" whatever was
         * asked, so they are always present — unlike listings, which are not cheap.
         */
        @Test
        @DisplayName("every area contributes its counts, whatever the question was about")
        void alwaysIncludesCountsForEveryArea() {
            String snapshot = service.personalSnapshot(user("SALES"), Set.of(CrmArea.LEADS));

            assertThat(snapshot)
                    .contains("Leads: total")
                    .contains("Deals: total")
                    .contains("Tasks: total")
                    .contains("Quotations: total")
                    .contains("Bookings: total")
                    .contains("Payments: total")
                    .contains("Customers: total");
        }

        @Test
        @DisplayName("only the asked-about area is listed row by row")
        void listsOnlyTheRequestedArea() {
            when(chatAggregateRepository.countAll(eq(USER_ID))).thenReturn(counts(Map.of(
                    CrmArea.LEADS, List.of(bucket("NEW", 2)),
                    CrmArea.DEALS, List.of(bucket("OPEN", 1)),
                    CrmArea.TASKS, List.of(bucket("OPEN", 2))), 0));
            when(leadRepository.findRecentForChat(eq(USER_ID), any()))
                    .thenReturn(List.of(LeadEntity.builder().leadId(UUID.randomUUID())
                            .fullName("Bruce Wayne").status(LeadStatus.NEW).build()));

            String leadsOnly = service.personalSnapshot(user("SALES"), Set.of(CrmArea.LEADS));

            assertThat(leadsOnly).contains("Lead list");
            assertThat(leadsOnly).doesNotContain("Deal details");
            assertThat(leadsOnly).doesNotContain("Open tasks");
        }

        @Test
        @DisplayName("no guidance for empty areas the question did not mention")
        void guidanceIgnoresUnaskedAreas() {
            when(chatAggregateRepository.countAll(eq(USER_ID))).thenReturn(counts(Map.of(
                    CrmArea.LEADS, List.of(bucket("NEW", 2))), 0));

            // Bookings and payments are empty too, but were not asked about.
            assertThat(service.personalSnapshot(user("MANAGER"), Set.of(CrmArea.LEADS)))
                    .doesNotContain("WHAT YOU MAY OFFER");
        }

        @Test
        @DisplayName("an empty asked-about area does get guidance")
        void guidanceForTheAskedArea() {
            String snapshot = service.personalSnapshot(user("MANAGER"), Set.of(CrmArea.BOOKINGS));

            assertThat(snapshot).contains("Empty for this scope: bookings.");
            assertThat(snapshot).contains("Company-wide bookings:");
        }
    }

    @Nested
    @DisplayName("Listing headers state coverage and where the rest is")
    class ListingHeaders {

        private void givenLeads(long total, int returned) {
            when(chatAggregateRepository.countAll(eq(USER_ID))).thenReturn(counts(Map.of(
                    CrmArea.LEADS, List.of(bucket("NEW", total))), 0));
            List<LeadEntity> rows = new ArrayList<>();
            for (int i = 0; i < returned; i++) {
                rows.add(LeadEntity.builder().leadId(UUID.randomUUID())
                        .fullName("Lead " + i).status(LeadStatus.NEW).build());
            }
            when(leadRepository.findRecentForChat(eq(USER_ID), any())).thenReturn(rows);
        }

        /**
         * The failure this guards against is quiet: 10 rows presented as "your leads" is a wrong
         * answer to "show me all my leads" when there are 143.
         */
        @Test
        @DisplayName("a capped listing is marked TRUNCATED with the real total")
        void marksTruncation() {
            // Not asserting the cap itself — it is a tuning knob, and a test that pins it would
            // need editing every time the listings are made shorter.
            givenLeads(143, 10);

            String snapshot = service.personalSnapshot(user("SALES"), Set.of(CrmArea.LEADS));

            assertThat(snapshot).contains(" of 143");
            assertThat(snapshot).contains("TRUNCATED");
            assertThat(snapshot).containsPattern("the remaining \\d+ are only on the screen");
        }

        @Test
        @DisplayName("a complete listing is not marked TRUNCATED")
        void doesNotMarkCompleteListings() {
            givenLeads(3, 3);

            String snapshot = service.personalSnapshot(user("SALES"), Set.of(CrmArea.LEADS));

            assertThat(snapshot).contains("showing 3 of 3");
            assertThat(snapshot).doesNotContain("TRUNCATED");
        }

        /**
         * The path is supplied as data rather than left to the model's memory: an invented link
         * looks authoritative and 404s, which is worse than naming the screen in words.
         */
        @Test
        @DisplayName("the header carries the screen label and path")
        void carriesTheScreenPath() {
            givenLeads(143, 10);

            assertThat(service.personalSnapshot(user("SALES"), Set.of(CrmArea.LEADS)))
                    .contains("Leads screen at /leads");
        }

        @Test
        @DisplayName("every area knows a screen path")
        void everyAreaHasAScreen() {
            for (CrmArea area : CrmArea.values()) {
                assertThat(area.screenPath()).startsWith("/");
                assertThat(area.screenLabel()).isNotBlank();
            }
        }
    }

    @Nested
    @DisplayName("Task listing")
    class Tasks {

        private void givenOneOpenTask(String title, OffsetDateTime deadline, long overdue) {
            when(chatAggregateRepository.countAll(eq(USER_ID))).thenReturn(counts(Map.of(
                    CrmArea.TASKS, List.of(bucket("OPEN", 1))), overdue));
            when(taskRepository.findOpenForChat(eq(USER_ID), anyList(), any()))
                    .thenReturn(List.of(TaskEntity.builder()
                            .taskId(UUID.randomUUID())
                            .title(title)
                            .status(TaskStatus.OPEN)
                            .endAt(deadline)
                            .build()));
        }

        @Test
        @DisplayName("open tasks are listed even when none are overdue")
        void listsOpenTasksNotOnlyOverdue() {
            givenOneOpenTask("Gọi lại khách hàng ACME", OffsetDateTime.now().plusDays(3), 0);

            String snapshot = service.personalSnapshot(user("SALES"), Set.of(CrmArea.TASKS));

            assertThat(snapshot).contains("Open tasks");
            assertThat(snapshot).contains("Gọi lại khách hàng ACME");
            assertThat(snapshot).doesNotContain("OVERDUE");
        }

        @Test
        @DisplayName("an overdue task is flagged in the listing")
        void flagsOverdueTasks() {
            givenOneOpenTask("Gửi báo giá trễ hạn", OffsetDateTime.now().minusDays(2), 1);

            String snapshot = service.personalSnapshot(user("SALES"), Set.of(CrmArea.TASKS));

            assertThat(snapshot).contains("Gửi báo giá trễ hạn");
            assertThat(snapshot).contains("OVERDUE");
        }
    }
}
