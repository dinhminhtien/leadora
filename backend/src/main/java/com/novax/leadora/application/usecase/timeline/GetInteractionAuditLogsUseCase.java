package com.novax.leadora.application.usecase.timeline;

import com.novax.leadora.api.dto.response.InteractionAuditLogResponse;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.InteractTimelineEntity;
import com.novax.leadora.infrastructure.persistence.entity.InteractionAuditLogEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.repository.InteractTimelineRepository;
import com.novax.leadora.infrastructure.persistence.repository.InteractionAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GetInteractionAuditLogsUseCase {

    private final InteractTimelineRepository interactTimelineRepository;
    private final InteractionAuditLogRepository interactionAuditLogRepository;
    private final InteractionTimelineAccessPolicy accessPolicy;

    @Transactional(readOnly = true)
    public List<InteractionAuditLogResponse> execute(UUID interactionId) {
        InteractTimelineEntity entity = interactTimelineRepository.findById(interactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Interaction timeline", interactionId));

        UserEntity currentUser = accessPolicy.currentUser();
        accessPolicy.assertCanView(currentUser, entity);

        List<InteractionAuditLogEntity> auditLogs = interactionAuditLogRepository.findByInteractionIdOrderByCreatedAtDesc(interactionId);

        return auditLogs.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private InteractionAuditLogResponse mapToResponse(InteractionAuditLogEntity entity) {
        return InteractionAuditLogResponse.builder()
                .auditId(entity.getAuditId())
                .action(entity.getAction())
                .changedByName(entity.getChangedByName())
                .changedByRole(entity.getChangedByRole())
                .timestamp(entity.getCreatedAt())
                .fieldName(entity.getFieldName())
                .oldValue(entity.getOldValue())
                .newValue(entity.getNewValue())
                .build();
    }
}
