package com.novax.leadora.unit.lead;

import com.novax.leadora.api.dto.request.CreateLeadRequest;
import com.novax.leadora.api.dto.response.LeadResponse;
import com.novax.leadora.application.usecase.customer.CustomerDuplicatePolicy;
import com.novax.leadora.application.usecase.lead.CreateLeadUseCase;
import com.novax.leadora.application.usecase.lead.LeadContactPolicy;
import com.novax.leadora.application.usecase.sla.StartSlaTrackingUseCase;
import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.common.exception.DuplicateLeadException;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.CustomerEntity;
import com.novax.leadora.infrastructure.persistence.entity.LeadEntity;
import com.novax.leadora.infrastructure.persistence.entity.RoleEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.LeadStatus;
import com.novax.leadora.infrastructure.persistence.repository.CustomerRepository;
import com.novax.leadora.infrastructure.persistence.repository.LeadRepository;
import com.novax.leadora.infrastructure.persistence.repository.NotificationRepository;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import com.novax.leadora.application.usecase.activitylog.ActivityLogPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UC-8.1 — Create Lead.
 *
 * <p>Replaces {@code integration/lead/LeadIntegrationTest}, which asserted one happy path through
 * an {@code @InjectMocks} use case whose other five dependencies were left null — so every rule in
 * the method (duplicate detection, assignment, SLA start) was untested by construction, and adding
 * a sixth dependency broke it with an NPE rather than a failed assertion.
 *
 * <p>Two behaviours here were changed deliberately and are pinned by name below: a new lead has a
 * creator but <b>no owner</b>, and a lead that duplicates an existing <b>customer</b> is refused.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CreateLeadUseCaseTest {

    @Mock private LeadRepository leadRepository;
    @Mock private UserRepository userRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private StartSlaTrackingUseCase startSlaTrackingUseCase;
    @Mock private CurrentUserProvider currentUserProvider;
    @Mock private NotificationRepository notificationRepository;
    @Mock private ActivityLogPublisher activityLogPublisher;
    @Mock private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    private CreateLeadUseCase useCase;

    private UserEntity salesRep;

    @BeforeEach
    void setUp() {
        salesRep = UserEntity.builder()
                .userId(UUID.randomUUID())
                .fullName("Sales Rep")
                .role(RoleEntity.builder().roleName("SALES").build())
                .build();

        useCase = new CreateLeadUseCase(leadRepository, userRepository, startSlaTrackingUseCase,
                currentUserProvider, notificationRepository, activityLogPublisher, objectMapper,
                new LeadContactPolicy(leadRepository, new CustomerDuplicatePolicy(customerRepository)));

        when(objectMapper.createObjectNode()).thenAnswer(inv -> new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode());
        when(leadRepository.save(any(LeadEntity.class))).thenAnswer(inv -> {
            LeadEntity lead = inv.getArgument(0);
            if (lead.getLeadId() == null) lead.setLeadId(UUID.randomUUID());
            return lead;
        });
        when(leadRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(anyString()))
                .thenReturn(Optional.empty());
        when(leadRepository.findFirstByPhoneOrderByCreatedAtDesc(anyString()))
                .thenReturn(Optional.empty());
        when(customerRepository.findFirstByEmail(anyString())).thenReturn(Optional.empty());
        when(customerRepository.findFirstByPhone(anyString())).thenReturn(Optional.empty());
        when(currentUserProvider.resolve(null)).thenReturn(salesRep);
    }

    private static CreateLeadRequest request() {
        CreateLeadRequest request = new CreateLeadRequest();
        request.setFullName("Tran Van B");
        request.setEmail("tranvanb@leadora.vn");
        request.setPhone("0987654321");
        request.setSource("Website Inquiry");
        request.setInterestedService("Rooms");
        return request;
    }

    private LeadEntity savedLead() {
        ArgumentCaptor<LeadEntity> captor = ArgumentCaptor.forClass(LeadEntity.class);
        verify(leadRepository).save(captor.capture());
        return captor.getValue();
    }

    // ── Ownership ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("UT-CREATE-01: a sales rep's new lead has a creator but NO owner — a Manager assigns it")
    void createdByIsNotOwner() {
        LeadResponse response = useCase.execute(request());

        assertThat(response.getStatus()).isEqualTo(LeadStatus.NEW);
        LeadEntity lead = savedLead();
        assertThat(lead.getCreatedBy()).isEqualTo(salesRep);
        assertThat(lead.getAssignedUser()).isNull();
    }

    @Test
    @DisplayName("UT-CREATE-02: an unassigned lead starts no SLA clock (BR-06) and notifies nobody")
    void unassignedLeadStartsNoSla() {
        useCase.execute(request());

        verify(startSlaTrackingUseCase, never()).execute(anyString(), anyString(), any());
        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT-CREATE-03: a Manager may still create a lead already assigned — SLA starts, rep is notified")
    void explicitAssigneeIsHonoured() {
        UserEntity assignee = UserEntity.builder()
                .userId(UUID.randomUUID()).fullName("Other Rep")
                .role(RoleEntity.builder().roleName("SALES").build())
                .build();
        when(userRepository.findById(assignee.getUserId())).thenReturn(Optional.of(assignee));
        CreateLeadRequest request = request();
        request.setAssignedUserId(assignee.getUserId());

        useCase.execute(request);

        assertThat(savedLead().getAssignedUser()).isEqualTo(assignee);
        verify(startSlaTrackingUseCase).execute(anyString(), anyString(), any());
        verify(notificationRepository).save(any());
    }

    // ── Duplicate detection ──────────────────────────────────────────────────

    @Test
    @DisplayName("UT-CREATE-04: a lead matching an existing CUSTOMER is refused, carrying that customer's id")
    void duplicateOfExistingCustomerRefused() {
        UUID customerId = UUID.randomUUID();
        when(customerRepository.findFirstByEmail("tranvanb@leadora.vn"))
                .thenReturn(Optional.of(CustomerEntity.builder().customerId(customerId).build()));

        assertThatThrownBy(() -> useCase.execute(request()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode", "httpStatus", "details")
                .containsExactly("DUPLICATE_CUSTOMER_EMAIL", HttpStatus.CONFLICT, customerId.toString());
        verify(leadRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT-CREATE-05: a phone matching an existing customer is refused too")
    void duplicateCustomerPhoneRefused() {
        UUID customerId = UUID.randomUUID();
        when(customerRepository.findFirstByPhone("0987654321"))
                .thenReturn(Optional.of(CustomerEntity.builder().customerId(customerId).build()));

        assertThatThrownBy(() -> useCase.execute(request()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode", "details")
                .containsExactly("DUPLICATE_CUSTOMER_PHONE", customerId.toString());
    }

    @Test
    @DisplayName("UT-CREATE-06: when both a customer and a lead match, the CUSTOMER is reported")
    void customerWinsOverLead() {
        UUID customerId = UUID.randomUUID();
        when(customerRepository.findFirstByEmail("tranvanb@leadora.vn"))
                .thenReturn(Optional.of(CustomerEntity.builder().customerId(customerId).build()));
        when(leadRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc("tranvanb@leadora.vn"))
                .thenReturn(Optional.of(LeadEntity.builder().leadId(UUID.randomUUID()).build()));

        assertThatThrownBy(() -> useCase.execute(request()))
                .extracting("errorCode")
                .isEqualTo("DUPLICATE_CUSTOMER_EMAIL");
    }

    @Test
    @DisplayName("UT-CREATE-07: a lead duplicating another LEAD is still refused, carrying that lead's id")
    void duplicateOfExistingLeadRefused() {
        UUID existingLeadId = UUID.randomUUID();
        when(leadRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc("tranvanb@leadora.vn"))
                .thenReturn(Optional.of(LeadEntity.builder().leadId(existingLeadId).build()));

        assertThatThrownBy(() -> useCase.execute(request()))
                .isInstanceOf(DuplicateLeadException.class)
                .extracting("errorCode", "details")
                .containsExactly("DUPLICATE_LEAD", existingLeadId.toString());
    }

    // ── Field rules ──────────────────────────────────────────────────────────

    /**
     * The bug this pins: `leads` carries partial unique indexes
     * ({@code ... WHERE email IS NOT NULL}) which exclude NULL but not "". Saving a blank field as
     * "" put it in the index, so the second lead created without an email failed on a constraint —
     * surfacing to the user as a duplicate warning about a field they had left empty.
     */
    @Test
    @DisplayName("UT-CREATE-10: blank optional fields are stored as NULL, never as empty strings")
    void blankFieldsBecomeNull() {
        // The phone stays: a lead now needs one contact detail, so blanking both is refused
        // outright (see contactIsRequired below) and could not exercise this rule.
        CreateLeadRequest request = request();
        request.setEmail("");
        request.setCompanyName("");
        request.setAddress("");
        request.setNotes("");

        useCase.execute(request);

        LeadEntity lead = savedLead();
        assertThat(lead.getEmail()).isNull();
        assertThat(lead.getPhone()).isEqualTo("0987654321");
        assertThat(lead.getCompanyName()).isNull();
        assertThat(lead.getAddress()).isNull();
        assertThat(lead.getNotes()).isNull();
    }

    @Test
    @DisplayName("UT-CREATE-11: surrounding whitespace is trimmed rather than stored")
    void valuesAreTrimmed() {
        CreateLeadRequest request = request();
        request.setFullName("  Tran Van B  ");
        request.setEmail("  tranvanb@leadora.vn  ");

        useCase.execute(request);

        assertThat(savedLead().getFullName()).isEqualTo("Tran Van B");
        assertThat(savedLead().getEmail()).isEqualTo("tranvanb@leadora.vn");
    }

    @Test
    @DisplayName("UT-CREATE-12: a lead with neither email nor phone is refused")
    void contactIsRequired() {
        CreateLeadRequest request = request();
        request.setEmail("");
        request.setPhone(null);

        assertThatThrownBy(() -> useCase.execute(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode", "details")
                .containsExactly("LEAD_NOT_REACHABLE", "phoneOrEmail");
        verify(leadRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT-CREATE-13: either contact field on its own is enough")
    void eitherContactAlone() {
        CreateLeadRequest emailOnly = request();
        emailOnly.setPhone("");
        assertThat(useCase.execute(emailOnly)).isNotNull();

        CreateLeadRequest phoneOnly = request();
        phoneOnly.setEmail("");
        assertThat(useCase.execute(phoneOnly)).isNotNull();
    }

    @Test
    @DisplayName("UT-CREATE-08: an organization lead must name its company")
    void corporateNeedsCompanyName() {
        CreateLeadRequest request = request();
        request.setIsCorporate(true);

        assertThatThrownBy(() -> useCase.execute(request))
                .isInstanceOf(IllegalArgumentException.class);
        verify(leadRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT-CREATE-09: an unresolvable creator does not block the lead being recorded")
    void creatorResolutionIsNonFatal() {
        when(currentUserProvider.resolve(null)).thenThrow(new RuntimeException("no principal"));

        assertThat(useCase.execute(request()).getStatus()).isEqualTo(LeadStatus.NEW);
        assertThat(savedLead().getCreatedBy()).isNull();
    }

    @Test
    @DisplayName("UT-CREATE-14: boundary limit test for field lengths and minimal entries")
    void createLeadWithBoundaryFieldsSuccess() {
        CreateLeadRequest request = new CreateLeadRequest();
        request.setFullName("A");
        request.setEmail("a@b.co");
        request.setPhone("12345678");
        request.setSource("S");
        request.setInterestedService("I");

        LeadResponse response = useCase.execute(request);
        assertThat(response).isNotNull();

        LeadEntity lead = savedLead();
        assertThat(lead.getFullName()).isEqualTo("A");
        assertThat(lead.getEmail()).isEqualTo("a@b.co");
        assertThat(lead.getPhone()).isEqualTo("12345678");
    }
}
