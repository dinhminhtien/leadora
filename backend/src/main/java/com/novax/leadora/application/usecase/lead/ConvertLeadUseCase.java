package com.novax.leadora.application.usecase.lead;

import com.novax.leadora.api.dto.request.ConvertLeadRequest;
import com.novax.leadora.api.dto.response.ConvertLeadResponse;
import com.novax.leadora.api.dto.response.LeadResponse;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.CustomerEntity;
import com.novax.leadora.infrastructure.persistence.entity.LeadEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.CustomerStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.CustomerType;
import com.novax.leadora.infrastructure.persistence.entity.enums.LeadStatus;
import com.novax.leadora.infrastructure.persistence.repository.CustomerRepository;
import com.novax.leadora.infrastructure.persistence.repository.LeadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConvertLeadUseCase {

    private final LeadRepository leadRepository;
    private final CustomerRepository customerRepository;
    private final LeadAccessPolicy leadAccessPolicy;

    @Transactional
    public ConvertLeadResponse execute(UUID leadId, ConvertLeadRequest request) {

        // 1. Load lead (eager-fetch assignedUser + createdBy)
        LeadEntity lead = leadRepository.findWithUsersById(leadId)
                .orElseThrow(() -> new ResourceNotFoundException("Lead", leadId));

        // UC-8.5 RBAC: same owner-scoping as viewing — a Sales Staff may only
        // convert leads assigned to (or created by) them; MANAGER/ADMIN unscoped.
        UserEntity currentUser = leadAccessPolicy.currentUser();
        leadAccessPolicy.assertCanView(currentUser, lead);

        // 2. Idempotency — already converted
        if (lead.getStatus() == LeadStatus.CONVERTED) {
            throw new IllegalStateException("This lead has already been converted to a customer.");
        }

        // 3a. A lead must be assigned to a sales rep (by a Manager) before conversion.
        //     Unassigned leads are drafts that never progress past NEW.
        if (lead.getAssignedUser() == null) {
            throw new IllegalStateException(
                    "Lead must be assigned to a sales rep before it can be converted.");
        }

        // 3. BR-07: a lead may be converted when QUALIFIED. Otherwise it may still be
        //    converted only via a Sales Manager override with a documented reason
        //    (covers the "confirmed booking / customer request exists" exception).
        if (lead.getStatus() != LeadStatus.QUALIFIED) {
            if (!StringUtils.hasText(request.getReason())) {
                throw new IllegalStateException(
                        "Lead must be QUALIFIED before conversion, or a Sales Manager must approve with a reason. "
                                + "Current status: " + lead.getStatus());
            }
            // Only a Manager/Admin may approve the exception.
            leadAccessPolicy.assertFullAccess(currentUser);
            // Preserve the approval reason on the lead itself — it is about to become an
            // immutable CONVERTED snapshot, so this is the audit trail for the override.
            String note = "[Converted with manager approval by " + currentUser.getFullName()
                    + ": " + request.getReason().trim() + "]";
            lead.setNotes(StringUtils.hasText(lead.getNotes()) ? lead.getNotes() + "\n" + note : note);
        }

        // 4. Create customer record from payload + inherit owner from lead
        CustomerEntity customer = CustomerEntity.builder()
                .customerType(request.getCustomerType())
                .fullName(request.getFullName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .companyName(request.getCompanyName())
                .taxCode(request.getTaxCode())
                .address(request.getAddress())
                .leadId(lead.getLeadId())
                .assignedUser(lead.getAssignedUser())
                .createdBy(lead.getCreatedBy())
                .status(CustomerStatus.ACTIVE)
                .build();

        CustomerEntity savedCustomer = customerRepository.save(customer);

        // 5. Mark lead as converted (BR-08: lead record is preserved, not deleted).
        //    Sync the lead's corporate flag with the type chosen at conversion time,
        //    so leads.is_corporate reflects the final individual/organization decision.
        lead.setStatus(LeadStatus.CONVERTED);
        lead.setConvertedAt(OffsetDateTime.now());
        lead.setIsCorporate(request.getCustomerType() == CustomerType.CORPORATE);
        lead.setCustomer(savedCustomer);

        LeadEntity savedLead = leadRepository.save(lead);

        return ConvertLeadResponse.builder()
                .customerId(savedCustomer.getCustomerId())
                .lead(LeadResponse.from(savedLead))
                .build();
    }
}
