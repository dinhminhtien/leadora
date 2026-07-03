package com.novax.leadora.application.usecase.timeline;

import com.novax.leadora.api.dto.request.UpdateInteractionTimelineRequest;
import com.novax.leadora.api.dto.response.InteractionTimelineResponse;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.InteractTimelineEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.repository.InteractTimelineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UpdateInteractionTimelineUseCase {

    private final InteractTimelineRepository interactTimelineRepository;
    private final InteractionTimelineMapper mapper;
    private final InteractionTimelineAccessPolicy accessPolicy;
    private final InteractionAuditService interactionAuditService;

    @Transactional
    public InteractionTimelineResponse execute(UUID id, UpdateInteractionTimelineRequest request) {
        InteractTimelineEntity entity = interactTimelineRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Interaction timeline", id));

        UserEntity currentUser = accessPolicy.currentUser();
        accessPolicy.assertCanView(currentUser, entity);

        InteractTimelineEntity oldSnapshot = InteractTimelineEntity.builder()
                .interactionId(entity.getInteractionId())
                .user(entity.getUser())
                .interactionType(entity.getInteractionType())
                .description(entity.getDescription())
                .occurredAt(entity.getOccurredAt())
                .lead(entity.getLead())
                .customer(entity.getCustomer())
                .deal(entity.getDeal())
                .createdAt(entity.getCreatedAt())
                .build();

        entity.setInteractionType(request.getType());
        entity.setDescription(request.getDescription());
        entity.setOccurredAt(request.getOccurredAt());

        InteractTimelineEntity saved = interactTimelineRepository.save(entity);
        interactionAuditService.logUpdate(oldSnapshot, saved, currentUser);
        return mapper.mapToResponse(saved);
    }
}
