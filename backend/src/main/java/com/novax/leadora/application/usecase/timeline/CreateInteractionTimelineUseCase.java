package com.novax.leadora.application.usecase.timeline;

import com.novax.leadora.api.dto.request.CreateInteractionTimelineRequest;
import com.novax.leadora.api.dto.response.InteractionTimelineResponse;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.CustomerEntity;
import com.novax.leadora.infrastructure.persistence.entity.DealEntity;
import com.novax.leadora.infrastructure.persistence.entity.InteractTimelineEntity;
import com.novax.leadora.infrastructure.persistence.entity.LeadEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.repository.CustomerRepository;
import com.novax.leadora.infrastructure.persistence.repository.DealRepository;
import com.novax.leadora.infrastructure.persistence.repository.InteractTimelineRepository;
import com.novax.leadora.infrastructure.persistence.repository.LeadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreateInteractionTimelineUseCase {

    private final InteractTimelineRepository interactTimelineRepository;
    private final LeadRepository leadRepository;
    private final CustomerRepository customerRepository;
    private final DealRepository dealRepository;
    private final InteractionTimelineMapper mapper;
    private final InteractionTimelineAccessPolicy accessPolicy;
    private final InteractionAuditService interactionAuditService;

    @Transactional
    public InteractionTimelineResponse execute(CreateInteractionTimelineRequest request) {
        if (request.getLeadId() == null && request.getCustomerId() == null && request.getDealId() == null) {
            throw new IllegalArgumentException("At least one association (lead, customer, or deal) is required.");
        }

        UserEntity currentUser = accessPolicy.currentUser();

        LeadEntity lead = null;
        CustomerEntity customer = null;
        DealEntity deal = null;

        if (request.getLeadId() != null) {
            lead = leadRepository.findById(request.getLeadId())
                    .orElseThrow(() -> new ResourceNotFoundException("Lead", request.getLeadId()));
            if (lead.getCustomer() != null) {
                customer = lead.getCustomer();
            }
        }

        if (request.getDealId() != null) {
            deal = dealRepository.findById(request.getDealId())
                    .orElseThrow(() -> new ResourceNotFoundException("Deal", request.getDealId()));
            customer = deal.getCustomer();
        }

        if (request.getCustomerId() != null) {
            CustomerEntity directCustomer = customerRepository.findById(request.getCustomerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Customer", request.getCustomerId()));
            customer = directCustomer;
        }

        InteractTimelineEntity entity = InteractTimelineEntity.builder()
                .user(currentUser)
                .interactionType(request.getType())
                .description(request.getDescription())
                .occurredAt(request.getOccurredAt())
                .lead(lead)
                .customer(customer)
                .deal(deal)
                .build();

        InteractTimelineEntity saved = interactTimelineRepository.save(entity);
        interactionAuditService.logCreation(saved, currentUser);
        return mapper.mapToResponse(saved);
    }
}
