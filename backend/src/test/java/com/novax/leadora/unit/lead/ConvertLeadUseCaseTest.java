package com.novax.leadora.unit.lead;

import com.novax.leadora.api.dto.request.ConvertLeadRequest;
import com.novax.leadora.api.dto.response.ConvertLeadResponse;
import com.novax.leadora.application.usecase.audit.SystemAuditLogService;
import com.novax.leadora.application.usecase.customer.CustomerDuplicatePolicy;
import com.novax.leadora.application.usecase.customer.CustomerProfilePolicy;
import com.novax.leadora.application.usecase.lead.ConvertLeadUseCase;
import com.novax.leadora.application.usecase.lead.LeadAccessPolicy;
import com.novax.leadora.application.usecase.lead.LeadConversionCompleter;
import com.novax.leadora.application.usecase.lead.LeadConversionPolicy;
import com.novax.leadora.application.usecase.sla.ResolveSlaBreachUseCase;
import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.infrastructure.persistence.entity.CustomerEntity;
import com.novax.leadora.infrastructure.persistence.entity.LeadEntity;
import com.novax.leadora.infrastructure.persistence.entity.RoleEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.CustomerType;
import com.novax.leadora.infrastructure.persistence.entity.enums.LeadStatus;
import com.novax.leadora.infrastructure.persistence.repository.CustomerRepository;
import com.novax.leadora.infrastructure.persistence.repository.LeadRepository;
import com.novax.leadora.application.usecase.activitylog.AuditCorrectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UC-8.5 — Convert Lead to Customer Profile.
 *
 * <p>
 * Replaces {@code integration/lead/ConvertLeadIntegrationTest}, which was a
 * Mockito unit test
 * filed under {@code integration} — the name promised a coverage level (Spring
 * context, real
 * repositories, actual column constraints) that nothing in it delivered, which
 * is worse than having
 * no integration test at all because it looks like one exists.
 *
 * <p>
 * The collaborators that carry real logic — {@link LeadConversionPolicy} and
 * {@link LeadConversionCompleter} — are built for real here rather than mocked,
 * with only their own
 * repository/service dependencies stubbed. Mocking them would have meant
 * asserting that this use
 * case calls a method, not that a lead actually ends up converted, audited and
 * off the SLA clock,
 * which is the part that was silently broken before.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConvertLeadUseCaseTest {

    @Mock
    private LeadRepository leadRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private LeadAccessPolicy leadAccessPolicy;
    @Mock
    private ResolveSlaBreachUseCase resolveSlaBreachUseCase;
    @Mock
    private SystemAuditLogService systemAuditLogService;
    @Mock
    private AuditCorrectionService auditCorrectionService;
    @Mock
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    private ConvertLeadUseCase useCase;

    private UUID leadId;
    private UserEntity salesRep;
    private UserEntity manager;

    @BeforeEach
    void setUp() {
        leadId = UUID.randomUUID();
        salesRep = user("Sales Rep", "SALES");
        manager = user("Sales Manager", "MANAGER");

        CustomerDuplicatePolicy duplicatePolicy = new CustomerDuplicatePolicy(customerRepository);
        CustomerProfilePolicy profilePolicy = new CustomerProfilePolicy();
        LeadConversionPolicy conversionPolicy = new LeadConversionPolicy(leadAccessPolicy);
        LeadConversionCompleter completer = new LeadConversionCompleter(
                leadRepository, resolveSlaBreachUseCase, systemAuditLogService);

        useCase = new ConvertLeadUseCase(leadRepository, customerRepository, leadAccessPolicy,
                conversionPolicy, duplicatePolicy, auditCorrectionService, objectMapper,
                profilePolicy, completer);

        when(objectMapper.createObjectNode())
                .thenAnswer(inv -> new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode());
        when(leadRepository.save(any(LeadEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(customerRepository.save(any(CustomerEntity.class))).thenAnswer(inv -> {
            CustomerEntity c = inv.getArgument(0);
            c.setCustomerId(UUID.randomUUID());
            return c;
        });
        when(customerRepository.findFirstByEmail(any())).thenReturn(Optional.empty());
        when(customerRepository.findFirstByPhone(any())).thenReturn(Optional.empty());
        when(leadAccessPolicy.currentUser()).thenReturn(salesRep);
    }

    private static UserEntity user(String name, String role) {
        return UserEntity.builder()
                .userId(UUID.randomUUID())
                .fullName(name)
                .role(RoleEntity.builder().roleName(role).build())
                .build();
    }

    /**
     * A lead that satisfies every conversion precondition, so a test can change one
     * thing.
     */
    private LeadEntity qualifiedLead() {
        LeadEntity lead = LeadEntity.builder()
                .leadId(leadId)
                .fullName("Nguyen Van A")
                .email("guest@hotel.vn")
                .phone("0912345678")
                .address("12 Nguyen Hue, HCMC")
                .isCorporate(false)
                .status(LeadStatus.QUALIFIED)
                .assignedUser(salesRep)
                .createdBy(salesRep)
                .build();
        when(leadRepository.findWithUsersByIdForUpdate(leadId)).thenReturn(Optional.of(lead));
        return lead;
    }

    private static ConvertLeadRequest request() {
        return new ConvertLeadRequest();
    }

    private CustomerEntity savedCustomer() {
        ArgumentCaptor<CustomerEntity> captor = ArgumentCaptor.forClass(CustomerEntity.class);
        verify(customerRepository).save(captor.capture());
        return captor.getValue();
    }

    // ── Normal flow ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("UT-CONVERT-01: QUALIFIED lead converts — customer created, lead CONVERTED and linked both ways")
    void convertsQualifiedLead() {
        LeadEntity lead = qualifiedLead();

        ConvertLeadResponse response = useCase.execute(leadId, request());

        assertThat(response.getCustomerId()).isNotNull();
        assertThat(lead.getStatus()).isEqualTo(LeadStatus.CONVERTED);
        assertThat(lead.getConvertedAt()).isNotNull();
        // POST-1/BR-07: the link exists in both directions, or the audit trail has only
        // one half.
        assertThat(lead.getCustomer()).isNotNull();
        assertThat(savedCustomer().getLeadId()).isEqualTo(leadId);
    }

    @Test
    @DisplayName("UT-CONVERT-02: the customer is built from the LEAD, not from the request")
    void buildsCustomerFromLead() {
        LeadEntity lead = qualifiedLead();

        useCase.execute(leadId, request());

        CustomerEntity customer = savedCustomer();
        assertThat(customer.getFullName()).isEqualTo(lead.getFullName());
        assertThat(customer.getEmail()).isEqualTo(lead.getEmail());
        assertThat(customer.getPhone()).isEqualTo(lead.getPhone());
        assertThat(customer.getAddress()).isEqualTo(lead.getAddress());
        // The owner follows the lead, so the profile lands in the same queue.
        assertThat(customer.getAssignedUser()).isEqualTo(salesRep);
        assertThat(customer.getCreatedBy()).isEqualTo(salesRep);
    }

    @Test
    @DisplayName("UT-CONVERT-03: customerType defaults to the lead's isCorporate flag")
    void inheritsCorporateFlag() {
        LeadEntity lead = qualifiedLead();
        lead.setIsCorporate(true);
        lead.setCompanyName("Novax Co");

        useCase.execute(leadId, request());

        assertThat(savedCustomer().getCustomerType()).isEqualTo(CustomerType.CORPORATE);
    }

    @Test
    @DisplayName("UT-CONVERT-04: an explicit customerType overrides the lead flag and syncs it back")
    void explicitCustomerTypeWins() {
        LeadEntity lead = qualifiedLead();
        lead.setCompanyName("Novax Co");
        ConvertLeadRequest request = request();
        request.setCustomerType(CustomerType.CORPORATE);

        useCase.execute(leadId, request);

        assertThat(savedCustomer().getCustomerType()).isEqualTo(CustomerType.CORPORATE);
        // The retained snapshot must not contradict the customer it produced.
        assertThat(lead.getIsCorporate()).isTrue();
    }

    @Test
    @DisplayName("UT-CONVERT-05: blank optional fields are stored as NULL, not as empty strings")
    void normalisesBlanksToNull() {
        LeadEntity lead = qualifiedLead();
        lead.setAddress("   ");
        lead.setCompanyName("");

        useCase.execute(leadId, request());

        CustomerEntity customer = savedCustomer();
        assertThat(customer.getAddress()).isNull();
        assertThat(customer.getCompanyName()).isNull();
        assertThat(customer.getTaxCode()).isNull();
    }

    // ── Step 9 / BR-08: the conversion is recorded ───────────────────────────

    @Test
    @DisplayName("UT-CONVERT-06: the conversion is written to the audit log with the acting user")
    void writesAuditLog() {
        qualifiedLead();

        useCase.execute(leadId, request());

        verify(systemAuditLogService).log(
                org.mockito.ArgumentMatchers.eq("LEAD"),
                org.mockito.ArgumentMatchers.eq("LEAD"),
                org.mockito.ArgumentMatchers.eq(leadId),
                org.mockito.ArgumentMatchers.eq("CONVERTED"),
                org.mockito.ArgumentMatchers.eq(salesRep),
                org.mockito.ArgumentMatchers.eq("QUALIFIED"),
                org.mockito.ArgumentMatchers.eq("CONVERTED"),
                org.mockito.ArgumentMatchers.contains("customerId="));
    }

    @Test
    @DisplayName("UT-CONVERT-07: SLA tracking for the lead is resolved — the scheduler must not chase a customer")
    void resolvesSlaTracking() {
        qualifiedLead();

        useCase.execute(leadId, request());

        verify(resolveSlaBreachUseCase).executeByEntity("LEAD", leadId);
    }

    @Test
    @DisplayName("UT-CONVERT-08: a failing audit/SLA write does not roll back a completed conversion")
    void loggingFailureIsNonFatal() {
        LeadEntity lead = qualifiedLead();
        org.mockito.Mockito.doThrow(new RuntimeException("db down"))
                .when(resolveSlaBreachUseCase).executeByEntity("LEAD", leadId);

        assertThat(useCase.execute(leadId, request()).getCustomerId()).isNotNull();
        assertThat(lead.getStatus()).isEqualTo(LeadStatus.CONVERTED);
    }

    // ── E4 — lead not eligible ───────────────────────────────────────────────

    @Nested
    @DisplayName("E4 — lead not eligible")
    class NotEligible {

        @Test
        @DisplayName("UT-CONVERT-09: an already CONVERTED lead is refused with 409")
        void alreadyConverted() {
            qualifiedLead().setStatus(LeadStatus.CONVERTED);

            assertThatThrownBy(() -> useCase.execute(leadId, request()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode", "httpStatus")
                    .containsExactly("LEAD_ALREADY_CONVERTED", HttpStatus.CONFLICT);
            verify(customerRepository, never()).save(any());
        }

        @Test
        @DisplayName("UT-CONVERT-10: a LOST lead is refused even with a manager's reason")
        void lostIsTerminal() {
            qualifiedLead().setStatus(LeadStatus.LOST);
            when(leadAccessPolicy.currentUser()).thenReturn(manager);
            ConvertLeadRequest request = request();
            request.setReason("Guest came back");

            assertThatThrownBy(() -> useCase.execute(leadId, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo("LEAD_LOST");
            verify(customerRepository, never()).save(any());
        }

        @Test
        @DisplayName("UT-CONVERT-11: an unassigned lead is refused — the customer would have no owner")
        void unassigned() {
            qualifiedLead().setAssignedUser(null);

            assertThatThrownBy(() -> useCase.execute(leadId, request()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo("LEAD_UNASSIGNED");
        }

        @Test
        @DisplayName("UT-CONVERT-12: a non-QUALIFIED lead without a reason is refused, pointing at `reason`")
        void notQualifiedWithoutReason() {
            qualifiedLead().setStatus(LeadStatus.CONTACTED);

            assertThatThrownBy(() -> useCase.execute(leadId, request()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode", "details")
                    .containsExactly("LEAD_NOT_QUALIFIED", "reason");
        }
    }

    // ── BR-07 override ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("BR-07 — Sales Manager override")
    class ManagerOverride {

        @Test
        @DisplayName("UT-CONVERT-13: a Manager with a reason converts a NEW lead, and the reason is kept")
        void managerConvertsNewLead() {
            LeadEntity lead = qualifiedLead();
            lead.setStatus(LeadStatus.NEW);
            when(leadAccessPolicy.currentUser()).thenReturn(manager);
            ConvertLeadRequest request = request();
            request.setReason("Walk-in guest with a confirmed booking");

            useCase.execute(leadId, request);

            assertThat(lead.getStatus()).isEqualTo(LeadStatus.CONVERTED);
            assertThat(lead.getNotes()).contains("Walk-in guest with a confirmed booking")
                    .contains("Sales Manager");
            // The override reason belongs in the audit trail, not only in a free-text
            // field.
            verify(systemAuditLogService).log(any(), any(), any(), any(), any(), any(), any(),
                    org.mockito.ArgumentMatchers.contains("managerOverride="));
        }

        @Test
        @DisplayName("UT-CONVERT-14: a Sales Staff cannot approve their own override")
        void staffCannotOverride() {
            qualifiedLead().setStatus(LeadStatus.NEW);
            org.mockito.Mockito.doThrow(new AccessDeniedException("Only a manager can perform this action."))
                    .when(leadAccessPolicy).assertFullAccess(salesRep);
            ConvertLeadRequest request = request();
            request.setReason("Please let me");

            assertThatThrownBy(() -> useCase.execute(leadId, request))
                    .isInstanceOf(AccessDeniedException.class);
            verify(customerRepository, never()).save(any());
        }

        @Test
        @DisplayName("UT-CONVERT-15: a blank reason is not a reason")
        void blankReasonRejected() {
            qualifiedLead().setStatus(LeadStatus.NEW);
            when(leadAccessPolicy.currentUser()).thenReturn(manager);
            ConvertLeadRequest request = request();
            request.setReason("   ");

            assertThatThrownBy(() -> useCase.execute(leadId, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo("LEAD_NOT_QUALIFIED");
        }
    }

    // ── Profile validity + E6 ────────────────────────────────────────────────

    @Test
    @DisplayName("UT-CONVERT-16: a corporate conversion without a company name is refused (BR-09)")
    void corporateNeedsCompanyName() {
        LeadEntity lead = qualifiedLead();
        lead.setIsCorporate(true);
        lead.setCompanyName(null);

        assertThatThrownBy(() -> useCase.execute(leadId, request()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo("CUSTOMER_COMPANY_REQUIRED");
    }

    @Test
    @DisplayName("UT-CONVERT-17: a lead with neither phone nor email cannot become a customer")
    void unreachableLeadRefused() {
        LeadEntity lead = qualifiedLead();
        lead.setStatus(LeadStatus.NEW); // BR-05 is waived here — which is exactly the gap
        lead.setEmail(null);
        lead.setPhone("");
        when(leadAccessPolicy.currentUser()).thenReturn(manager);
        ConvertLeadRequest request = request();
        request.setReason("Walk-in");

        assertThatThrownBy(() -> useCase.execute(leadId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo("CUSTOMER_NOT_REACHABLE");
    }

    @Test
    @DisplayName("UT-CONVERT-18: E6 — a duplicate refuses with 409 carrying the existing customer's id")
    void duplicateCarriesExistingCustomerId() {
        qualifiedLead();
        UUID existingId = UUID.randomUUID();
        when(customerRepository.findFirstByEmail("guest@hotel.vn"))
                .thenReturn(Optional.of(CustomerEntity.builder().customerId(existingId).build()));

        assertThatThrownBy(() -> useCase.execute(leadId, request()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode", "httpStatus", "details")
                .containsExactly("DUPLICATE_CUSTOMER_EMAIL", HttpStatus.CONFLICT, existingId.toString());
    }

    @Test
    @DisplayName("UT-CONVERT-19: a missing lead is a 404, not a converted ghost")
    void leadNotFound() {
        UUID unknown = UUID.randomUUID();
        when(leadRepository.findWithUsersById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(unknown, request()))
                .isInstanceOf(com.novax.leadora.common.exception.ResourceNotFoundException.class);
    }
}
