package com.novax.leadora.application.usecase.chat;

import com.novax.leadora.application.usecase.chat.dto.DealStatusAggregate;
import com.novax.leadora.application.usecase.chat.dto.LeadStatusCount;
import com.novax.leadora.application.usecase.chat.dto.RepLeadCount;
import com.novax.leadora.application.usecase.chat.dto.TaskStatusCount;
import com.novax.leadora.infrastructure.persistence.entity.LeadEntity;
import com.novax.leadora.infrastructure.persistence.entity.RoleEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.DealStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.LeadStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.TaskStatus;
import com.novax.leadora.application.usecase.chat.intent.CrmArea;
import com.novax.leadora.infrastructure.persistence.repository.BookingRepository;
import com.novax.leadora.infrastructure.persistence.repository.CustomerRepository;
import com.novax.leadora.infrastructure.persistence.repository.DealRepository;
import com.novax.leadora.infrastructure.persistence.repository.PaymentRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationRepository;
import com.novax.leadora.infrastructure.persistence.repository.LeadRepository;
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
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/**
 * Tests for the guidance the snapshot attaches when a scope comes back empty.
 *
 * <p>This is the part of the assistant that decides whether "show my leads" ends at "you have
 * none" or continues with something the user can actually do next, so its trigger conditions and
 * its data-scope rules are worth pinning down. The repositories are mocked: the queries themselves
 * are covered by {@code ChatQueryCompilationTest}, and what matters here is the branching.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CrmSnapshotServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private LeadRepository leadRepository;

    @Mock
    private DealRepository dealRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private QuotationRepository quotationRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private CustomerRepository customerRepository;

    private CrmSnapshotService service;

    @BeforeEach
    void setUp() {
        service = new CrmSnapshotService(leadRepository, dealRepository, taskRepository,
                quotationRepository, bookingRepository, paymentRepository, customerRepository);

        // Default: every scope is empty. Individual tests fill in what they need.
        when(leadRepository.countByStatusForChat(any())).thenReturn(List.of());
        when(dealRepository.aggregateByStatusForChat(any())).thenReturn(List.of());
        when(taskRepository.countByStatusForChat(any())).thenReturn(List.of());
        when(taskRepository.countOverdueForChat(any(), anyList(), any())).thenReturn(0L);
        when(leadRepository.findRecentForChat(any(), any())).thenReturn(List.of());
        when(dealRepository.findRecentForChat(any(), any())).thenReturn(List.of());
        when(taskRepository.findOpenForChat(any(), anyList(), any())).thenReturn(List.of());
        when(leadRepository.countPerAssignee(any())).thenReturn(List.of());
        when(quotationRepository.aggregateByStatusForChat(any())).thenReturn(List.of());
        when(bookingRepository.aggregateByStatusForChat(any())).thenReturn(List.of());
        when(paymentRepository.aggregateByStatusForChat(any())).thenReturn(List.of());
        when(customerRepository.countByStatusForChat(any())).thenReturn(List.of());
    }

    private static ChatActor user(String role) {
        return ChatActor.from(UserEntity.builder()
                .userId(USER_ID)
                .fullName("Trần Nhật Minh")
                .role(RoleEntity.builder().roleId(1).roleName(role).build())
                .build());
    }

    /** The user's own scope: no leads, but a deal and some tasks. */
    private void givenPersonalScopeHasDealsAndTasksButNoLeads() {
        when(dealRepository.aggregateByStatusForChat(eq(USER_ID)))
                .thenReturn(List.of(new DealStatusAggregate(
                        DealStatus.OPEN, 1L, new BigDecimal("5000000"))));
        when(taskRepository.countByStatusForChat(eq(USER_ID)))
                .thenReturn(List.of(new TaskStatusCount(TaskStatus.OPEN, 2L)));
    }

    private void givenCompanyWideLeadsExist() {
        when(leadRepository.countByStatusForChat(isNull())).thenReturn(List.of(
                new LeadStatusCount(LeadStatus.NEW, 8L),
                new LeadStatusCount(LeadStatus.CONVERTED, 12L)));
        when(leadRepository.countPerAssignee(any())).thenReturn(List.of(
                new RepLeadCount("Alice Smith", 6L),
                new RepLeadCount("Tiến Đinh", 4L)));
    }

    @Nested
    @DisplayName("Guidance is emitted per area")
    class PerArea {

        /**
         * The case that motivated the change: a Manager with no leads of their own but a deal and
         * two tasks. Requiring the WHOLE snapshot to be empty meant this — the common shape of
         * real data — produced no guidance at all.
         */
        @Test
        @DisplayName("empty leads still get guidance when deals and tasks have rows")
        void emptyLeadsGetGuidanceEvenWhenOtherAreasHaveData() {
            givenPersonalScopeHasDealsAndTasksButNoLeads();
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
            givenPersonalScopeHasDealsAndTasksButNoLeads();
            givenCompanyWideLeadsExist();

            String snapshot = service.personalSnapshot(user("MANAGER"), CrmArea.defaults());

            assertThat(snapshot).doesNotContain("Empty for this scope: leads, deals, tasks.");
            assertThat(snapshot).doesNotContain("Company-wide deals:");
        }

        @Test
        @DisplayName("no guidance at all when every area has data")
        void noGuidanceWhenNothingIsEmpty() {
            when(leadRepository.countByStatusForChat(eq(USER_ID)))
                    .thenReturn(List.of(new LeadStatusCount(LeadStatus.NEW, 3L)));
            givenPersonalScopeHasDealsAndTasksButNoLeads();

            String snapshot = service.personalSnapshot(user("MANAGER"), CrmArea.defaults());

            assertThat(snapshot).doesNotContain("WHAT YOU MAY OFFER");
        }
    }

    @Nested
    @DisplayName("BR-36: guidance must not leak beyond the caller's scope")
    class ScopeLeakage {

        /**
         * Naming who does hold the leads would disclose exactly what a Sales user is not allowed
         * to read, so the suggestions have to stay inside their own scope.
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

            String snapshot = service.personalSnapshot(user("MANAGER"), CrmArea.defaults());

            assertThat(snapshot).contains("Alice Smith=6");
        }
    }

    @Nested
    @DisplayName("Scope selection")
    class Scope {

        @Test
        @DisplayName("personalSnapshot always queries the user's own records, even for a Manager")
        void personalSnapshotIsAlwaysPinnedToTheUser() {
            service.personalSnapshot(user("MANAGER"), CrmArea.defaults());
            // A null argument would mean "every record"; the personal snapshot must not use it.
            org.mockito.Mockito.verify(leadRepository).countByStatusForChat(eq(USER_ID));
        }

        @Test
        @DisplayName("scopedSnapshot widens to all records for a Manager")
        void scopedSnapshotWidensForManager() {
            service.scopedSnapshot(user("MANAGER"), CrmArea.defaults());
            org.mockito.Mockito.verify(leadRepository).countByStatusForChat(isNull());
        }

        @Test
        @DisplayName("scopedSnapshot stays personal for a Sales user")
        void scopedSnapshotStaysPersonalForSales() {
            service.scopedSnapshot(user("SALES"), CrmArea.defaults());
            org.mockito.Mockito.verify(leadRepository).countByStatusForChat(eq(USER_ID));
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

            assertThat(snapshot).contains("Leads: total");
            assertThat(snapshot).contains("Deals: total");
            assertThat(snapshot).contains("Tasks: total");
            assertThat(snapshot).contains("Quotations: total");
            assertThat(snapshot).contains("Bookings: total");
            assertThat(snapshot).contains("Payments: total");
            assertThat(snapshot).contains("Customers: total");
        }

        @Test
        @DisplayName("only the asked-about area is listed row by row")
        void listsOnlyTheRequestedArea() {
            when(leadRepository.countByStatusForChat(eq(USER_ID)))
                    .thenReturn(List.of(new LeadStatusCount(LeadStatus.NEW, 2L)));
            givenPersonalScopeHasDealsAndTasksButNoLeads();

            String leadsOnly = service.personalSnapshot(user("SALES"), Set.of(CrmArea.LEADS));

            assertThat(leadsOnly).contains("Lead list");
            assertThat(leadsOnly).doesNotContain("Deal details");
            assertThat(leadsOnly).doesNotContain("Open tasks");
        }

        /**
         * Guidance follows the same rule: an empty payments area is not worth explaining when the
         * question was about leads.
         */
        @Test
        @DisplayName("no guidance for empty areas the question did not mention")
        void guidanceIgnoresUnaskedAreas() {
            when(leadRepository.countByStatusForChat(eq(USER_ID)))
                    .thenReturn(List.of(new LeadStatusCount(LeadStatus.NEW, 2L)));

            String snapshot = service.personalSnapshot(user("MANAGER"), Set.of(CrmArea.LEADS));

            // Bookings and payments are empty too, but were not asked about.
            assertThat(snapshot).doesNotContain("WHAT YOU MAY OFFER");
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
            when(leadRepository.countByStatusForChat(eq(USER_ID)))
                    .thenReturn(List.of(new LeadStatusCount(LeadStatus.NEW, total)));
            List<LeadEntity> rows = new java.util.ArrayList<>();
            for (int i = 0; i < returned; i++) {
                rows.add(LeadEntity.builder().leadId(UUID.randomUUID())
                        .fullName("Lead " + i).status(LeadStatus.NEW).build());
            }
            when(leadRepository.findRecentForChat(eq(USER_ID), any())).thenReturn(rows);
        }

        /**
         * The failure this guards against is quiet: 25 rows presented as "your leads" is a wrong
         * answer to "show me all my leads" when there are 143. The header has to carry both
         * numbers so the assistant can say which it is.
         */
        @Test
        @DisplayName("a capped listing is marked TRUNCATED with the real total")
        void marksTruncation() {
            // Deliberately not asserting the cap itself — that is a tuning knob, and a test that
            // pins it would have to be edited every time the listings are made shorter.
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
            givenLeads(143, 25);

            String snapshot = service.personalSnapshot(user("SALES"), Set.of(CrmArea.LEADS));

            assertThat(snapshot).contains("Leads screen at /leads");
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

        @Test
        @DisplayName("open tasks are listed even when none are overdue")
        void listsOpenTasksNotOnlyOverdue() {
            when(taskRepository.countByStatusForChat(eq(USER_ID)))
                    .thenReturn(List.of(new TaskStatusCount(TaskStatus.OPEN, 1L)));
            when(taskRepository.findOpenForChat(eq(USER_ID), anyList(), any()))
                    .thenReturn(List.of(com.novax.leadora.infrastructure.persistence.entity.TaskEntity
                            .builder()
                            .taskId(UUID.randomUUID())
                            .title("Gọi lại khách hàng ACME")
                            .status(TaskStatus.OPEN)
                            .endAt(OffsetDateTime.now().plusDays(3))
                            .build()));

            String snapshot = service.personalSnapshot(user("SALES"), CrmArea.defaults());

            assertThat(snapshot).contains("Open tasks");
            assertThat(snapshot).contains("Gọi lại khách hàng ACME");
            assertThat(snapshot).doesNotContain("OVERDUE");
        }

        @Test
        @DisplayName("an overdue task is flagged in the listing")
        void flagsOverdueTasks() {
            when(taskRepository.countByStatusForChat(eq(USER_ID)))
                    .thenReturn(List.of(new TaskStatusCount(TaskStatus.OPEN, 1L)));
            when(taskRepository.countOverdueForChat(eq(USER_ID), anyList(), any())).thenReturn(1L);
            when(taskRepository.findOpenForChat(eq(USER_ID), anyList(), any()))
                    .thenReturn(List.of(com.novax.leadora.infrastructure.persistence.entity.TaskEntity
                            .builder()
                            .taskId(UUID.randomUUID())
                            .title("Gửi báo giá trễ hạn")
                            .status(TaskStatus.OPEN)
                            .endAt(OffsetDateTime.now().minusDays(2))
                            .build()));

            String snapshot = service.personalSnapshot(user("SALES"), CrmArea.defaults());

            assertThat(snapshot).contains("Gửi báo giá trễ hạn");
            assertThat(snapshot).contains("OVERDUE");
        }
    }
}
