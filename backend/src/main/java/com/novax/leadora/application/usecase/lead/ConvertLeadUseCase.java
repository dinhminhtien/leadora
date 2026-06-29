package com.novax.leadora.application.usecase.lead;

import com.novax.leadora.api.dto.request.ConvertLeadRequest;
import com.novax.leadora.api.dto.response.ConvertLeadResponse;
import com.novax.leadora.api.dto.response.LeadResponse;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.CustomerEntity;
import com.novax.leadora.infrastructure.persistence.entity.LeadEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.CustomerStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.CustomerType;
import com.novax.leadora.infrastructure.persistence.entity.enums.LeadStatus;
import com.novax.leadora.infrastructure.persistence.repository.CustomerRepository;
import com.novax.leadora.infrastructure.persistence.repository.LeadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConvertLeadUseCase {

    private final LeadRepository leadRepository;
    private final CustomerRepository customerRepository;

    @Transactional
    public ConvertLeadResponse execute(UUID leadId, ConvertLeadRequest request) {

        // 1. Load lead (eager-fetch assignedUser + createdBy)
        LeadEntity lead = leadRepository.findWithUsersById(leadId)
                .orElseThrow(() -> new ResourceNotFoundException("Lead", leadId));

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

        // 3. BR-07: status must be QUALIFIED
        if (lead.getStatus() != LeadStatus.QUALIFIED) {
            throw new IllegalStateException(
                    "Lead must be QUALIFIED before conversion. Current status: " + lead.getStatus()
            );
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
