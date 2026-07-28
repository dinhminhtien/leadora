package com.novax.leadora.application.usecase.lead;

import com.novax.leadora.api.dto.request.ConvertLeadRequest;
import com.novax.leadora.api.dto.response.ConvertLeadResponse;
import com.novax.leadora.api.dto.response.LeadResponse;
import com.novax.leadora.application.usecase.customer.CustomerDuplicatePolicy;
import com.novax.leadora.application.usecase.customer.CustomerProfilePolicy;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.CustomerEntity;
import com.novax.leadora.infrastructure.persistence.entity.LeadEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.CustomerStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.CustomerType;
import com.novax.leadora.infrastructure.persistence.repository.CustomerRepository;
import com.novax.leadora.infrastructure.persistence.repository.LeadRepository;
import com.novax.leadora.application.usecase.activitylog.ActivityLogPublisher;
import com.novax.leadora.infrastructure.persistence.entity.enums.ActivityLogType;
import com.novax.leadora.infrastructure.persistence.entity.enums.EntityType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static com.novax.leadora.common.util.TextUtils.blankToNull;

/**
 * UC-8.5 — Convert Lead to Customer Profile.
 *
 * <p>
 * The customer is built from the <b>lead</b>, not from the request. See
 * {@link ConvertLeadRequest} for why the payload no longer carries the identity
 * fields at all.
 *
 * <p>
 * Everything after the validation block is delegated to
 * {@link LeadConversionCompleter}, which
 * this use case shares with {@link LinkLeadToCustomerUseCase} (exception E6).
 * Both end the same
 * way — the lead becomes an immutable CONVERTED snapshot, its SLA tracking
 * stops, the action is
 * audited — and writing that ending twice is how the two would drift apart.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConvertLeadUseCase {

    private final LeadRepository leadRepository;
    private final CustomerRepository customerRepository;
    private final LeadAccessPolicy leadAccessPolicy;
    private final LeadConversionPolicy leadConversionPolicy;
    private final CustomerDuplicatePolicy customerDuplicatePolicy;
    private final ActivityLogPublisher activityLogPublisher;
    private final ObjectMapper objectMapper;
    private final CustomerProfilePolicy customerProfilePolicy;
    private final LeadConversionCompleter leadConversionCompleter;

    @Transactional
    public ConvertLeadResponse execute(UUID leadId, ConvertLeadRequest request) {

        // 1. Load lead (eager-fetch assignedUser + createdBy)
        LeadEntity lead = leadRepository.findWithUsersById(leadId)
                .orElseThrow(() -> new ResourceNotFoundException("Lead", leadId));

        // 2. UC-8.5 step 2 / BR-02 — same owner-scoping as viewing: a Sales Staff may
        // only convert
        // leads assigned to (or created by) them; MANAGER/ADMIN unscoped.
        UserEntity currentUser = leadAccessPolicy.currentUser();
        leadAccessPolicy.assertCanView(currentUser, lead);

        // 3. UC-8.5 step 4 / E4 + BR-07 — is this lead eligible at all? Shared with the
        // E6 link
        // path, which must answer exactly the same question.
        String overrideReason = leadConversionPolicy.assertEligible(lead, currentUser, request.getReason());

        // 4. BR-09 — Individual or Corporate. The lead's own flag decides unless the
        // caller
        // deliberately overrides it at conversion time.
        CustomerType customerType = request.getCustomerType() != null
                ? request.getCustomerType()
                : (Boolean.TRUE.equals(lead.getIsCorporate()) ? CustomerType.CORPORATE : CustomerType.INDIVIDUAL);

        // 5. The profile about to be created must be a usable one. Both rules read the
        // LEAD's
        // fields, because those are the fields the customer is built from — validating
        // the
        // request instead is what let a corporate customer be created with no company
        // name.
        customerProfilePolicy.assertCorporateHasCompany(customerType, lead.getCompanyName());
        customerProfilePolicy.assertReachable(lead.getEmail(), lead.getPhone());

        // 6. Two different leads can still describe the same person, so the duplicate
        // check done
        // when each LEAD was created protects nothing here. Same rule
        // CreateCustomerUseCase
        // applies, shared rather than copied — see CustomerDuplicatePolicy. On a hit
        // this throws
        // 409 carrying the existing customer's id, which is what lets the UI offer E6's
        // "link to the existing profile" instead of a dead end.
        customerDuplicatePolicy.assertNoDuplicate(lead.getEmail(), lead.getPhone());

        // 7. UC-8.5 step 6 — the customer profile, built from the lead. Owner and
        // creator are
        // inherited so the new profile lands in the same person's queue as the lead
        // did.
        CustomerEntity customer = CustomerEntity.builder()
                .customerType(customerType)
                .fullName(lead.getFullName())
                .email(blankToNull(lead.getEmail()))
                .phone(blankToNull(lead.getPhone()))
                .companyName(blankToNull(lead.getCompanyName()))
                .taxCode(blankToNull(request.getTaxCode()))
                .address(blankToNull(lead.getAddress()))
                .leadId(lead.getLeadId())
                .assignedUser(lead.getAssignedUser())
                .createdBy(lead.getCreatedBy())
                .status(CustomerStatus.ACTIVE)
                .build();

        CustomerEntity savedCustomer = customerRepository.save(customer);

        // 8. Keep leads.is_corporate consistent with the individual/organization
        // decision that was
        // just finalised, so the retained snapshot does not contradict the customer it
        // produced.
        if (Boolean.TRUE.equals(lead.getIsCorporate()) != (customerType == CustomerType.CORPORATE)) {
            lead.setIsCorporate(customerType == CustomerType.CORPORATE);
        }

        // 9. Steps 7-9 — mark converted, link both ways, stop SLA, write the audit
        // entry.
        LeadEntity savedLead = leadConversionCompleter.complete(
                lead, savedCustomer, currentUser, overrideReason, "CONVERTED");

        // Publish Activity Log event
        try {
            ObjectNode payload = objectMapper.createObjectNode()
                    .put("customerId", savedCustomer.getCustomerId().toString())
                    .put("customerType", savedCustomer.getCustomerType().name())
                    .put("fullName", savedCustomer.getFullName())
                    .put("email", savedCustomer.getEmail())
                    .put("phone", savedCustomer.getPhone());
            if (request.getReason() != null) {
                payload.put("reason", request.getReason());
            }
            activityLogPublisher.publish(
                    ActivityLogType.LEAD_CONVERTED,
                    EntityType.LEAD,
                    savedLead.getLeadId(),
                    "Lead converted to customer: " + savedCustomer.getFullName(),
                    payload);
        } catch (Exception e) {
            log.warn("Failed to publish lead conversion activity: {}", e.getMessage());
        }

        return ConvertLeadResponse.builder()
                .customerId(savedCustomer.getCustomerId())
                .lead(LeadResponse.from(savedLead))
                .build();
    }
}
