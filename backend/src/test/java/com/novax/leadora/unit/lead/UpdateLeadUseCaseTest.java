package com.novax.leadora.unit.lead;

import com.novax.leadora.api.dto.request.UpdateLeadRequest;
import com.novax.leadora.application.usecase.customer.CustomerDuplicatePolicy;
import com.novax.leadora.application.usecase.lead.LeadAccessPolicy;
import com.novax.leadora.application.usecase.lead.LeadContactPolicy;
import com.novax.leadora.application.usecase.lead.UpdateLeadUseCase;
import com.novax.leadora.application.usecase.sla.ResolveSlaBreachUseCase;
import com.novax.leadora.application.usecase.sla.StartSlaTrackingUseCase;
import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.common.exception.DuplicateLeadException;
import com.novax.leadora.infrastructure.persistence.entity.LeadEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.LeadStatus;
import com.novax.leadora.infrastructure.persistence.repository.CustomerRepository;
import com.novax.leadora.infrastructure.persistence.repository.LeadRepository;
import com.novax.leadora.infrastructure.persistence.repository.NotificationRepository;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import com.novax.leadora.application.usecase.activitylog.AuditCorrectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * The lead status machine and the record invariants around it (UC-8.4).
 *
 * <p>
 * Nothing covered this before: every transition rule, BR-05 and the duplicate
 * check lived in one
 * method with no test behind it, so a wrong edit there would have surfaced only
 * in the UI.
 *
 * <p>
 * Lenient stubbing — several tests refuse before the save path is reached, and
 * marking each
 * unused stub individually would bury what each case is actually asserting.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UpdateLeadUseCaseTest {

    @Mock
    private LeadRepository leadRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ResolveSlaBreachUseCase resolveSlaBreachUseCase;
    @Mock
    private StartSlaTrackingUseCase startSlaTrackingUseCase;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private LeadAccessPolicy leadAccessPolicy;
    @Mock
    private AuditCorrectionService auditCorrectionService;
    @Mock
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    @Mock
    private CustomerRepository customerRepository;

    // Built by hand rather than @InjectMocks so the real LeadContactPolicy runs:
    // the reachability
    // and duplicate rules moved into it, and a mocked policy would leave those
    // cases asserting
    // that a method was called rather than that an edit was actually refused.
    private UpdateLeadUseCase useCase;

    private UUID leadId;
    private UserEntity owner;

    @BeforeEach
    void setUp() {
        leadId = UUID.randomUUID();
        owner = UserEntity.builder().userId(UUID.randomUUID()).fullName("Rep").build();
        useCase = new UpdateLeadUseCase(leadRepository, userRepository, resolveSlaBreachUseCase,
                startSlaTrackingUseCase, notificationRepository, leadAccessPolicy,
                auditCorrectionService, objectMapper,
                new LeadContactPolicy(leadRepository, new CustomerDuplicatePolicy(customerRepository)));
        when(leadRepository.save(any(LeadEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(leadRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(anyString()))
                .thenReturn(Optional.empty());
        when(leadRepository.findFirstByPhoneOrderByCreatedAtDesc(anyString()))
                .thenReturn(Optional.empty());
        when(objectMapper.createObjectNode())
                .thenAnswer(inv -> new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode());
        when(customerRepository.findFirstByEmail(anyString())).thenReturn(Optional.empty());
        when(customerRepository.findFirstByPhone(anyString())).thenReturn(Optional.empty());
    }

    /** A lead that satisfies BR-05, so a test can change one thing at a time. */
    private LeadEntity readyLead(LeadStatus status) {
        LeadEntity lead = LeadEntity.builder()
                .leadId(leadId)
                .fullName("Lead")
                .email("lead@hotel.vn")
                .phone("0912345678")
                .source("Referral")
                .interestedService("Wedding banquet")
                .status(status)
                .assignedUser(owner)
                .createdBy(owner)
                .build();
        when(leadRepository.findWithUsersByIdForUpdate(leadId)).thenReturn(Optional.of(lead));
        return lead;
    }

    private static UpdateLeadRequest statusChange(LeadStatus to) {
        UpdateLeadRequest req = new UpdateLeadRequest();
        req.setStatus(to);
        return req;
    }

    // ── Status machine ───────────────────────────────────────────────────────

    @Test
    @DisplayName("NEW advances to CONTACTED")
    void advancesOneStage() {
        LeadEntity lead = readyLead(LeadStatus.NEW);

        useCase.execute(leadId, statusChange(LeadStatus.CONTACTED));

        assertThat(lead.getStatus()).isEqualTo(LeadStatus.CONTACTED);
    }

    @Test
    @DisplayName("NEW cannot jump straight to QUALIFIED")
    void refusesSkippingAStage() {
        readyLead(LeadStatus.NEW);

        assertThatThrownBy(() -> useCase.execute(leadId, statusChange(LeadStatus.QUALIFIED)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo("LEAD_INVALID_TRANSITION");
    }

    @Test
    @DisplayName("a lead cannot move backwards")
    void refusesGoingBackwards() {
        readyLead(LeadStatus.QUALIFIED);

        assertThatThrownBy(() -> useCase.execute(leadId, statusChange(LeadStatus.CONTACTED)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("an active lead can always be marked LOST")
    void allowsLostFromAnyActiveStage() {
        LeadEntity lead = readyLead(LeadStatus.NEW);

        useCase.execute(leadId, statusChange(LeadStatus.LOST));

        assertThat(lead.getStatus()).isEqualTo(LeadStatus.LOST);
    }

    @Test
    @DisplayName("LOST is terminal")
    void refusesReopeningALostLead() {
        readyLead(LeadStatus.LOST);

        assertThatThrownBy(() -> useCase.execute(leadId, statusChange(LeadStatus.CONTACTED)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("CONVERTED can only be reached through the conversion flow, never a status edit")
    void refusesSettingConvertedDirectly() {
        readyLead(LeadStatus.QUALIFIED);

        assertThatThrownBy(() -> useCase.execute(leadId, statusChange(LeadStatus.CONVERTED)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo("LEAD_INVALID_TRANSITION");
    }

    @Test
    @DisplayName("a converted lead is a locked historical record (BR-08)")
    void refusesEditingAConvertedLead() {
        readyLead(LeadStatus.CONVERTED);

        UpdateLeadRequest req = new UpdateLeadRequest();
        req.setFullName("New name");

        assertThatThrownBy(() -> useCase.execute(leadId, req))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo("LEAD_LOCKED");
    }

    @Test
    @DisplayName("an unassigned lead cannot change status")
    void refusesStatusChangeWhileUnassigned() {
        LeadEntity lead = readyLead(LeadStatus.NEW);
        lead.setAssignedUser(null);

        assertThatThrownBy(() -> useCase.execute(leadId, statusChange(LeadStatus.CONTACTED)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo("LEAD_UNASSIGNED");
    }

    // ── BR-05 as a record invariant, not just a transition guard ─────────────

    @Test
    @DisplayName("entering follow-up without an interested service is refused, naming the field")
    void refusesFollowUpWithoutInterestedService() {
        LeadEntity lead = readyLead(LeadStatus.NEW);
        lead.setInterestedService(null);

        assertThatThrownBy(() -> useCase.execute(leadId, statusChange(LeadStatus.CONTACTED)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException ex = (BusinessException) e;
                    assertThat(ex.getErrorCode()).isEqualTo("LEAD_NOT_READY_FOR_FOLLOW_UP");
                    // The field list is what lets the form mark the offending input.
                    assertThat(ex.getDetails()).contains("interestedService");
                });
    }

    @Test
    @DisplayName("all missing qualifying fields are reported together, not one save at a time")
    void reportsEveryMissingFieldAtOnce() {
        LeadEntity lead = readyLead(LeadStatus.NEW);
        // Contact details are no longer among these: LeadContactPolicy requires them on
        // every
        // write, so a lead cannot reach this check without one. What is left is what
        // genuinely
        // only becomes necessary once follow-up starts.
        lead.setSource(null);
        lead.setInterestedService(null);

        assertThatThrownBy(() -> useCase.execute(leadId, statusChange(LeadStatus.CONTACTED)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getDetails())
                .satisfies(details -> assertThat((String) details)
                        .contains("source").contains("interestedService"));
    }

    @Test
    @DisplayName("a lead cannot have its last contact detail erased, at any status")
    void refusesClearingContactOnALeadAlreadyInFollowUp() {
        // Previously this was only caught for a lead in active follow-up, and only
        // because BR-05
        // happened to list the field. It now holds everywhere, including for a NEW
        // lead.
        LeadEntity lead = readyLead(LeadStatus.CONTACTED);
        lead.setPhone(null);

        UpdateLeadRequest req = new UpdateLeadRequest();
        req.setEmail("");

        assertThatThrownBy(() -> useCase.execute(leadId, req))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode", "details")
                .containsExactly("LEAD_NOT_REACHABLE", "phoneOrEmail");
    }

    @Test
    @DisplayName("a NEW lead cannot have its last contact detail erased either")
    void refusesClearingContactOnANewLead() {
        LeadEntity lead = readyLead(LeadStatus.NEW);
        lead.setEmail(null);

        UpdateLeadRequest req = new UpdateLeadRequest();
        req.setPhone("");

        assertThatThrownBy(() -> useCase.execute(leadId, req))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo("LEAD_NOT_REACHABLE");
    }

    @Test
    @DisplayName("an edit that collides with an existing CUSTOMER is refused, not just one with a lead")
    void refusesEmailBelongingToACustomer() {
        readyLead(LeadStatus.CONTACTED);
        UUID customerId = UUID.randomUUID();
        when(customerRepository.findFirstByEmail("guest@hotel.vn")).thenReturn(
                Optional.of(com.novax.leadora.infrastructure.persistence.entity.CustomerEntity.builder()
                        .customerId(customerId).build()));

        UpdateLeadRequest req = new UpdateLeadRequest();
        req.setEmail("guest@hotel.vn");

        assertThatThrownBy(() -> useCase.execute(leadId, req))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode", "details")
                .containsExactly("DUPLICATE_CUSTOMER_EMAIL", customerId.toString());
    }

    @Test
    @DisplayName("a NEW lead may be saved without qualifying details — capture first, qualify later")
    void allowsIncompleteLeadWhileStillNew() {
        LeadEntity lead = readyLead(LeadStatus.NEW);
        lead.setSource(null);
        lead.setInterestedService(null);

        UpdateLeadRequest req = new UpdateLeadRequest();
        req.setFullName("Walk-in guest");

        useCase.execute(leadId, req);

        assertThat(lead.getFullName()).isEqualTo("Walk-in guest");
    }

    @Test
    @DisplayName("a lead can be marked LOST without ever completing its details")
    void allowsLosingAnIncompleteLead() {
        LeadEntity lead = readyLead(LeadStatus.NEW);
        lead.setSource(null);
        lead.setInterestedService(null);

        useCase.execute(leadId, statusChange(LeadStatus.LOST));

        assertThat(lead.getStatus()).isEqualTo(LeadStatus.LOST);
    }

    // ── Duplicate detection on edit ──────────────────────────────────────────

    @Test
    @DisplayName("an edit cannot move this lead's email onto one another lead already holds")
    void refusesDuplicateEmailOnEdit() {
        readyLead(LeadStatus.NEW);
        LeadEntity other = LeadEntity.builder().leadId(UUID.randomUUID()).build();
        when(leadRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc("taken@hotel.vn"))
                .thenReturn(Optional.of(other));

        UpdateLeadRequest req = new UpdateLeadRequest();
        req.setEmail("taken@hotel.vn");

        assertThatThrownBy(() -> useCase.execute(leadId, req))
                .isInstanceOf(DuplicateLeadException.class);
    }

    @Test
    @DisplayName("re-saving a lead's own email is not a duplicate of itself")
    void allowsSavingItsOwnEmailUnchanged() {
        LeadEntity lead = readyLead(LeadStatus.NEW);
        when(leadRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc("lead@hotel.vn"))
                .thenReturn(Optional.of(lead));

        UpdateLeadRequest req = new UpdateLeadRequest();
        req.setEmail("lead@hotel.vn");

        useCase.execute(leadId, req);

        assertThat(lead.getEmail()).isEqualTo("lead@hotel.vn");
    }
}
