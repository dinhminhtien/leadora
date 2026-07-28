package com.novax.leadora.application.usecase.lead;

import com.novax.leadora.api.dto.request.LinkLeadToCustomerRequest;
import com.novax.leadora.api.dto.response.ConvertLeadResponse;
import com.novax.leadora.api.dto.response.LeadResponse;
import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.CustomerEntity;
import com.novax.leadora.infrastructure.persistence.entity.LeadEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.repository.CustomerRepository;
import com.novax.leadora.infrastructure.persistence.repository.LeadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * UC-8.5 exception E6 — "The user may link the lead to the existing customer
 * profile or cancel."
 *
 * <p>
 * <b>Why this needed to exist.</b> The duplicate check refused the conversion
 * with a 409 and
 * stopped there, which implemented only the "or cancel" half of E6. A sales rep
 * who found that
 * their lead was an existing guest had no way to record that fact: the lead
 * stayed in the pipeline
 * forever, the guest's second enquiry was never attached to their history, and
 * the only way out was
 * to invent a fake email so the duplicate check would pass — the exact
 * split-record outcome
 * {@code CustomerDuplicatePolicy} was written to prevent.
 *
 * <p>
 * The refusal already carried the existing customer's id in its {@code details}
 * field, so the
 * missing piece was only ever this endpoint.
 *
 * <p>
 * <b>What it deliberately does not do:</b> it does not touch the customer.
 * Linking says "this
 * enquiry came from that guest", not "replace that guest's record with this
 * enquiry".
 */
@Service
@RequiredArgsConstructor
public class LinkLeadToCustomerUseCase {

        private final LeadRepository leadRepository;
        private final CustomerRepository customerRepository;
        private final LeadAccessPolicy leadAccessPolicy;
        private final LeadConversionPolicy leadConversionPolicy;
        private final LeadConversionCompleter leadConversionCompleter;

        @Transactional
        public ConvertLeadResponse execute(UUID leadId, LinkLeadToCustomerRequest request) {

                LeadEntity lead = leadRepository.findWithUsersByIdForUpdate(leadId)
                                .orElseThrow(() -> new ResourceNotFoundException("Lead", leadId));

                UserEntity currentUser = leadAccessPolicy.currentUser();
                leadAccessPolicy.assertCanView(currentUser, lead);

                // Same eligibility gate as a normal conversion: the lead ends up CONVERTED and
                // locked
                // either way, so it must be just as entitled to get there.
                String overrideReason = leadConversionPolicy.assertEligible(lead, currentUser, request.getReason());

                CustomerEntity customer = customerRepository.findById(request.getCustomerId())
                                .orElseThrow(() -> new ResourceNotFoundException("Customer", request.getCustomerId()));

                // A customer already claimed by another lead must not be re-pointed:
                // customers.lead_id is a
                // single column, so overwriting it would erase the first lead's link and leave
                // that lead
                // CONVERTED but pointing at a customer that no longer points back. Rejecting is
                // right —
                // one customer profile can only record which enquiry originated it.
                if (customer.getLeadId() != null && !customer.getLeadId().equals(lead.getLeadId())) {
                        throw new BusinessException("CUSTOMER_ALREADY_LINKED",
                                        "That customer profile was already created from a different lead.",
                                        customer.getLeadId().toString(),
                                        HttpStatus.CONFLICT);
                }

                customer.setLeadId(lead.getLeadId());
                CustomerEntity savedCustomer = customerRepository.save(customer);

                LeadEntity savedLead = leadConversionCompleter.complete(
                                lead, savedCustomer, currentUser, overrideReason, "LINKED_TO_CUSTOMER");

                return ConvertLeadResponse.builder()
                                .customerId(savedCustomer.getCustomerId())
                                .lead(LeadResponse.from(savedLead))
                                .build();
        }
}
