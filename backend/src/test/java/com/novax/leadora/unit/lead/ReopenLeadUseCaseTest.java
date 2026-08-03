package com.novax.leadora.unit.lead;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novax.leadora.api.dto.request.ReopenLeadRequest;
import com.novax.leadora.api.dto.response.LeadResponse;
import com.novax.leadora.application.usecase.activitylog.AuditCorrectionService;
import com.novax.leadora.application.usecase.audit.SystemAuditLogService;
import com.novax.leadora.application.usecase.lead.LeadAccessPolicy;
import com.novax.leadora.application.usecase.lead.ReopenLeadUseCase;
import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.LeadEntity;
import com.novax.leadora.infrastructure.persistence.entity.NotificationEntity;
import com.novax.leadora.infrastructure.persistence.entity.RoleEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.LeadStatus;
import com.novax.leadora.infrastructure.persistence.repository.LeadRepository;
import com.novax.leadora.infrastructure.persistence.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UC-8.4 — reopening a lead closed as LOST.
 *
 * <p>The route did not exist before: {@code LeadConversionPolicy} told the user to "reopen it
 * first" while {@code UpdateLeadUseCase} refused every transition out of {@code LOST}, so a
 * returning guest was a dead end. These tests pin the three things that make the new route safe to
 * have at all — it is Manager-only, it only applies to a lost lead, and it lands on {@code NEW}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReopenLeadUseCaseTest {

    @Mock private LeadRepository leadRepository;
    @Mock private LeadAccessPolicy leadAccessPolicy;
    @Mock private AuditCorrectionService auditCorrectionService;
    @Mock private SystemAuditLogService systemAuditLogService;
    @Mock private NotificationRepository notificationRepository;

    private ReopenLeadUseCase useCase;

    private UUID leadId;
    private UserEntity manager;
    private UserEntity owner;

    @BeforeEach
    void setUp() {
        leadId = UUID.randomUUID();
        manager = UserEntity.builder()
                .userId(UUID.randomUUID())
                .fullName("Sales Manager")
                .role(RoleEntity.builder().roleName("MANAGER").build())
                .build();
        owner = UserEntity.builder()
                .userId(UUID.randomUUID())
                .fullName("Sales Rep")
                .role(RoleEntity.builder().roleName("SALES").build())
                .build();

        useCase = new ReopenLeadUseCase(leadRepository, leadAccessPolicy, auditCorrectionService,
                systemAuditLogService, notificationRepository, new ObjectMapper());

        when(leadRepository.save(any(LeadEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(leadAccessPolicy.currentUser()).thenReturn(manager);
    }

    private LeadEntity leadWith(LeadStatus status, String notes) {
        LeadEntity lead = LeadEntity.builder()
                .leadId(leadId)
                .fullName("Nguyen Van A")
                .email("guest@hotel.vn")
                .phone("0912345678")
                .source("Referral")
                .interestedService("Banquet hall")
                .status(status)
                .notes(notes)
                .assignedUser(owner)
                .build();
        when(leadRepository.findWithUsersByIdForUpdate(leadId)).thenReturn(Optional.of(lead));
        return lead;
    }

    private static ReopenLeadRequest request(String reason) {
        ReopenLeadRequest r = new ReopenLeadRequest();
        r.setReason(reason);
        return r;
    }

    @Test
    @DisplayName("a lost lead reopens to NEW, not to the stage it was in before")
    void reopensToNew() {
        LeadEntity lead = leadWith(LeadStatus.LOST, null);

        LeadResponse response = useCase.execute(leadId, request("Guest called back about March"));

        assertThat(lead.getStatus()).isEqualTo(LeadStatus.NEW);
        assertThat(response.getStatus()).isEqualTo(LeadStatus.NEW);
    }

    @Test
    @DisplayName("the reason is appended to notes, not written over them")
    void appendsReasonToNotes() {
        LeadEntity lead = leadWith(LeadStatus.LOST, "Original enquiry: 40 pax");

        useCase.execute(leadId, request("Guest called back"));

        assertThat(lead.getNotes())
                .startsWith("Original enquiry: 40 pax")
                .contains("[Reopened by Sales Manager: Guest called back]");
    }

    @Test
    @DisplayName("the reason reaches the audit trail with both sides of the transition")
    void auditsTheTransition() {
        leadWith(LeadStatus.LOST, null);

        useCase.execute(leadId, request("Guest called back"));

        verify(systemAuditLogService).log(eq("LEAD"), eq("LEAD"), eq(leadId), eq("REOPENED"),
                eq(manager), eq("LOST"), eq("NEW"), eq("Guest called back"));
    }

    @Test
    @DisplayName("the owner is told their lost lead is back in their pipeline")
    void notifiesTheOwner() {
        leadWith(LeadStatus.LOST, null);

        useCase.execute(leadId, request("Guest called back"));

        verify(notificationRepository).save(any(NotificationEntity.class));
    }

    @Test
    @DisplayName("a sales rep cannot reopen — the Lost figure is not theirs to edit")
    void requiresFullAccess() {
        leadWith(LeadStatus.LOST, null);
        doThrow(new AccessDeniedException("Only a manager can perform this action."))
                .when(leadAccessPolicy).assertFullAccess(any());

        assertThatThrownBy(() -> useCase.execute(leadId, request("Guest called back")))
                .isInstanceOf(AccessDeniedException.class);

        verify(leadRepository, never()).save(any());
    }

    @Test
    @DisplayName("an active lead is refused — reopen is not a way to reset the pipeline")
    void refusesLeadThatIsNotLost() {
        leadWith(LeadStatus.QUALIFIED, null);

        assertThatThrownBy(() -> useCase.execute(leadId, request("Guest called back")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "LEAD_NOT_LOST")
                .hasFieldOrPropertyWithValue("httpStatus", HttpStatus.UNPROCESSABLE_ENTITY);

        verify(leadRepository, never()).save(any());
    }

    @Test
    @DisplayName("a converted lead is told it is converted, not that it is 'not lost'")
    void refusesConvertedLeadWithTheSpecificReason() {
        leadWith(LeadStatus.CONVERTED, null);

        assertThatThrownBy(() -> useCase.execute(leadId, request("Guest called back")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "LEAD_LOCKED");
    }

    @Test
    @DisplayName("an unknown lead is a 404, not a silent no-op")
    void refusesUnknownLead() {
        when(leadRepository.findWithUsersByIdForUpdate(leadId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(leadId, request("Guest called back")))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
