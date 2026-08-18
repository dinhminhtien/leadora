package com.novax.leadora.unit.lead;

import com.novax.leadora.api.dto.request.LinkLeadToCustomerRequest;
import com.novax.leadora.api.dto.response.ConvertLeadResponse;
import com.novax.leadora.application.usecase.audit.SystemAuditLogService;
import com.novax.leadora.application.usecase.lead.LeadAccessPolicy;
import com.novax.leadora.application.usecase.lead.LeadConversionCompleter;
import com.novax.leadora.application.usecase.lead.LeadConversionPolicy;
import com.novax.leadora.application.usecase.lead.LinkLeadToCustomerUseCase;
import com.novax.leadora.application.usecase.sla.ResolveSlaBreachUseCase;
import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.CustomerEntity;
import com.novax.leadora.infrastructure.persistence.entity.LeadEntity;
import com.novax.leadora.infrastructure.persistence.entity.RoleEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.CustomerStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.CustomerType;
import com.novax.leadora.infrastructure.persistence.entity.enums.LeadStatus;
import com.novax.leadora.infrastructure.persistence.repository.CustomerRepository;
import com.novax.leadora.infrastructure.persistence.repository.LeadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UC-8.5 exception E6 — "the user may link the lead to the existing customer profile".
 *
 * <p>This half of E6 did not exist before: the duplicate check returned 409 and the flow ended
 * there, so a lead belonging to a returning guest could never leave the pipeline honestly.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LinkLeadToCustomerUseCaseTest {

    @Mock private LeadRepository leadRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private LeadAccessPolicy leadAccessPolicy;
    @Mock private ResolveSlaBreachUseCase resolveSlaBreachUseCase;
    @Mock private SystemAuditLogService systemAuditLogService;

    private LinkLeadToCustomerUseCase useCase;

    private UUID leadId;
    private UUID customerId;
    private UserEntity salesRep;

    @BeforeEach
    void setUp() {
        leadId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        salesRep = UserEntity.builder()
                .userId(UUID.randomUUID())
                .fullName("Sales Rep")
                .role(RoleEntity.builder().roleName("SALES").build())
                .build();

        useCase = new LinkLeadToCustomerUseCase(
                leadRepository, customerRepository, leadAccessPolicy,
                new LeadConversionPolicy(leadAccessPolicy),
                new LeadConversionCompleter(leadRepository, resolveSlaBreachUseCase, systemAuditLogService));

        when(leadRepository.save(any(LeadEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(customerRepository.save(any(CustomerEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(leadAccessPolicy.currentUser()).thenReturn(salesRep);
    }

    private LeadEntity qualifiedLead() {
        LeadEntity lead = LeadEntity.builder()
                .leadId(leadId)
                .fullName("Nguyen Van A")
                .email("guest@hotel.vn")
                .phone("0912345678")
                .status(LeadStatus.QUALIFIED)
                .assignedUser(salesRep)
                .createdBy(salesRep)
                .build();
        when(leadRepository.findWithUsersByIdForUpdate(leadId)).thenReturn(Optional.of(lead));
        return lead;
    }

    private CustomerEntity existingCustomer(UUID originLeadId) {
        CustomerEntity customer = CustomerEntity.builder()
                .customerId(customerId)
                .customerType(CustomerType.INDIVIDUAL)
                .fullName("Nguyen Van A")
                .email("guest@hotel.vn")
                .leadId(originLeadId)
                .status(CustomerStatus.ACTIVE)
                .build();
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        return customer;
    }

    private LinkLeadToCustomerRequest request() {
        LinkLeadToCustomerRequest request = new LinkLeadToCustomerRequest();
        request.setCustomerId(customerId);
        return request;
    }

    @Test
    @DisplayName("UT-LINK-01: the lead becomes CONVERTED and points at the existing customer")
    void linksLeadToExistingCustomer() {
        LeadEntity lead = qualifiedLead();
        CustomerEntity customer = existingCustomer(null);

        ConvertLeadResponse response = useCase.execute(leadId, request());

        assertThat(response.getCustomerId()).isEqualTo(customerId);
        assertThat(lead.getStatus()).isEqualTo(LeadStatus.CONVERTED);
        assertThat(lead.getConvertedAt()).isNotNull();
        assertThat(lead.getCustomer()).isEqualTo(customer);
        assertThat(customer.getLeadId()).isEqualTo(leadId);
    }

    @Test
    @DisplayName("UT-LINK-02: linking never edits the existing profile")
    void doesNotOverwriteCustomerFields() {
        LeadEntity lead = qualifiedLead();
        lead.setFullName("Typo In The Lead");
        lead.setPhone("0987654321");
        CustomerEntity customer = existingCustomer(null);

        useCase.execute(leadId, request());

        assertThat(customer.getFullName()).isEqualTo("Nguyen Van A");
        assertThat(customer.getPhone()).isNull();
    }

    @Test
    @DisplayName("UT-LINK-03: the link is audited as LINKED_TO_CUSTOMER, distinct from a conversion")
    void auditsAsLink() {
        qualifiedLead();
        existingCustomer(null);

        useCase.execute(leadId, request());

        verify(systemAuditLogService).log(eq("LEAD"), eq("LEAD"), eq(leadId), eq("LINKED_TO_CUSTOMER"),
                eq(salesRep), eq("QUALIFIED"), eq("CONVERTED"), contains("customerId="));
        verify(resolveSlaBreachUseCase).executeByEntity("LEAD", leadId);
    }

    @Test
    @DisplayName("UT-LINK-04: a customer already created from another lead is refused with 409")
    void refusesCustomerOwnedByAnotherLead() {
        qualifiedLead();
        UUID otherLeadId = UUID.randomUUID();
        existingCustomer(otherLeadId);

        assertThatThrownBy(() -> useCase.execute(leadId, request()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode", "httpStatus", "details")
                .containsExactly("CUSTOMER_ALREADY_LINKED", HttpStatus.CONFLICT, otherLeadId.toString());
        verify(leadRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT-LINK-05: re-linking the same pair is allowed (retry after a dropped response)")
    void relinkingSamePairIsAllowed() {
        LeadEntity lead = qualifiedLead();
        existingCustomer(leadId);

        useCase.execute(leadId, request());

        assertThat(lead.getStatus()).isEqualTo(LeadStatus.CONVERTED);
    }

    @Test
    @DisplayName("UT-LINK-06: E4 still applies — a LOST lead cannot be linked either")
    void eligibilityStillApplies() {
        qualifiedLead().setStatus(LeadStatus.LOST);
        existingCustomer(null);

        assertThatThrownBy(() -> useCase.execute(leadId, request()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo("LEAD_LOST");
    }

    @Test
    @DisplayName("UT-LINK-07: an unknown customer id is a 404")
    void unknownCustomer() {
        qualifiedLead();
        when(customerRepository.findById(customerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(leadId, request()))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
