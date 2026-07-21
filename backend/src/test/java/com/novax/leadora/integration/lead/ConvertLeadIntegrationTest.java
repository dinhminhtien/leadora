package com.novax.leadora.integration.lead;

import com.novax.leadora.api.dto.request.ConvertLeadRequest;
import com.novax.leadora.api.dto.response.ConvertLeadResponse;
import com.novax.leadora.application.usecase.lead.ConvertLeadUseCase;
import com.novax.leadora.application.usecase.lead.LeadAccessPolicy;
import com.novax.leadora.infrastructure.persistence.entity.CustomerEntity;
import com.novax.leadora.infrastructure.persistence.entity.LeadEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.CustomerType;
import com.novax.leadora.infrastructure.persistence.entity.enums.LeadStatus;
import com.novax.leadora.infrastructure.persistence.repository.CustomerRepository;
import com.novax.leadora.infrastructure.persistence.repository.LeadRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConvertLeadIntegrationTest {

    @Mock
    private LeadRepository leadRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private LeadAccessPolicy leadAccessPolicy;

    @InjectMocks
    private ConvertLeadUseCase convertLeadUseCase;

    private UserEntity buildCurrentUser() {
        return UserEntity.builder()
                .userId(UUID.randomUUID())
                .fullName("Sales Staff")
                .build();
    }

    private LeadEntity buildQualifiedLead() {
        UserEntity assignedUser = buildCurrentUser();
        return LeadEntity.builder()
                .leadId(UUID.randomUUID())
                .fullName("Lead Customer")
                .email("lead@hotel.vn")
                .phone("0912345678")
                .status(LeadStatus.QUALIFIED)
                .assignedUser(assignedUser)
                .createdBy(assignedUser)
                .build();
    }

    private ConvertLeadRequest buildConvertRequest() {
        ConvertLeadRequest req = new ConvertLeadRequest();
        req.setFullName("Converted Customer");
        req.setEmail("converted@hotel.vn");
        req.setPhone("0987654321");
        req.setCustomerType(CustomerType.INDIVIDUAL);
        return req;
    }

    @Test
    @DisplayName("IT-CONVERT-01: Convert QUALIFIED lead → creates customer + status = CONVERTED")
    void testConvertQualifiedLead() {
        UUID leadId = UUID.randomUUID();
        LeadEntity lead = buildQualifiedLead();
        lead.setLeadId(leadId);
        UserEntity currentUser = lead.getAssignedUser();

        when(leadRepository.findWithUsersById(leadId)).thenReturn(Optional.of(lead));
        when(leadAccessPolicy.currentUser()).thenReturn(currentUser);
        doNothing().when(leadAccessPolicy).assertCanView(currentUser, lead);
        when(customerRepository.save(any(CustomerEntity.class))).thenAnswer(inv -> {
            CustomerEntity c = inv.getArgument(0);
            c.setCustomerId(UUID.randomUUID());
            return c;
        });
        when(leadRepository.save(any(LeadEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ConvertLeadResponse response = convertLeadUseCase.execute(leadId, buildConvertRequest());

        assertNotNull(response);
        assertNotNull(response.getCustomerId());
        assertEquals(LeadStatus.CONVERTED, lead.getStatus());
    }

    @Test
    @DisplayName("IT-CONVERT-02: Convert non-QUALIFIED lead without reason → throws IllegalStateException")
    void testConvertNonQualifiedWithoutReasonThrows() {
        UUID leadId = UUID.randomUUID();
        LeadEntity lead = buildQualifiedLead();
        lead.setLeadId(leadId);
        lead.setStatus(LeadStatus.NEW);
        UserEntity currentUser = lead.getAssignedUser();

        when(leadRepository.findWithUsersById(leadId)).thenReturn(Optional.of(lead));
        when(leadAccessPolicy.currentUser()).thenReturn(currentUser);
        doNothing().when(leadAccessPolicy).assertCanView(currentUser, lead);

        ConvertLeadRequest request = buildConvertRequest();
        request.setReason(null);

        assertThrows(IllegalStateException.class,
                () -> convertLeadUseCase.execute(leadId, request));
    }

    @Test
    @DisplayName("IT-CONVERT-03: Convert already CONVERTED lead → throws IllegalStateException")
    void testConvertAlreadyConvertedThrows() {
        UUID leadId = UUID.randomUUID();
        LeadEntity lead = buildQualifiedLead();
        lead.setLeadId(leadId);
        lead.setStatus(LeadStatus.CONVERTED);
        UserEntity currentUser = lead.getAssignedUser();

        when(leadRepository.findWithUsersById(leadId)).thenReturn(Optional.of(lead));
        when(leadAccessPolicy.currentUser()).thenReturn(currentUser);
        doNothing().when(leadAccessPolicy).assertCanView(currentUser, lead);

        assertThrows(IllegalStateException.class,
                () -> convertLeadUseCase.execute(leadId, buildConvertRequest()));
    }

    @Test
    @DisplayName("IT-CONVERT-04: Convert unassigned lead → throws IllegalStateException")
    void testConvertUnassignedLeadThrows() {
        UUID leadId = UUID.randomUUID();
        LeadEntity lead = buildQualifiedLead();
        lead.setLeadId(leadId);
        lead.setAssignedUser(null);
        UserEntity currentUser = buildCurrentUser();

        when(leadRepository.findWithUsersById(leadId)).thenReturn(Optional.of(lead));
        when(leadAccessPolicy.currentUser()).thenReturn(currentUser);
        doNothing().when(leadAccessPolicy).assertCanView(currentUser, lead);

        assertThrows(IllegalStateException.class,
                () -> convertLeadUseCase.execute(leadId, buildConvertRequest()));
    }

    @Test
    @DisplayName("IT-CONVERT-05: Lead not found → throws ResourceNotFoundException")
    void testLeadNotFoundThrows() {
        UUID leadId = UUID.randomUUID();
        when(leadRepository.findWithUsersById(leadId)).thenReturn(Optional.empty());

        assertThrows(Exception.class,
                () -> convertLeadUseCase.execute(leadId, buildConvertRequest()));
    }
}
