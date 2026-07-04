package com.novax.leadora.application.usecase.timeline;

import com.novax.leadora.infrastructure.persistence.entity.InteractTimelineEntity;
import com.novax.leadora.infrastructure.persistence.entity.InteractionAuditLogEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.repository.InteractionAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InteractionAuditService {

    private final InteractionAuditLogRepository auditLogRepository;

    @Transactional
    public void logCreation(InteractTimelineEntity entity, UserEntity user) {
        String roleName = user.getRole() != null ? user.getRole().getRoleName() : "USER";

        InteractionAuditLogEntity log = InteractionAuditLogEntity.builder()
                .interactionId(entity.getInteractionId())
                .action("CREATED")
                .changedBy(user.getUserId())
                .changedByName(user.getFullName())
                .changedByRole(roleName)
                .fieldName(null)
                .oldValue(null)
                .newValue("Interaction logged with type: " + entity.getInteractionType())
                .build();

        auditLogRepository.save(log);
    }

    @Transactional
    public void logUpdate(InteractTimelineEntity oldEntity, InteractTimelineEntity newEntity, UserEntity user) {
        String roleName = user.getRole() != null ? user.getRole().getRoleName() : "USER";
        List<InteractionAuditLogEntity> logs = new ArrayList<>();

        // 1. Interaction Type
        if (!Objects.equals(oldEntity.getInteractionType(), newEntity.getInteractionType())) {
            logs.add(buildUpdateLog(newEntity.getInteractionId(), user, roleName,
                    "interactionType", oldEntity.getInteractionType(), newEntity.getInteractionType()));
        }

        // 2. Description
        if (!Objects.equals(oldEntity.getDescription(), newEntity.getDescription())) {
            logs.add(buildUpdateLog(newEntity.getInteractionId(), user, roleName,
                    "description", oldEntity.getDescription(), newEntity.getDescription()));
        }

        // 3. Occurred At
        if (!Objects.equals(oldEntity.getOccurredAt(), newEntity.getOccurredAt())) {
            String oldValStr = oldEntity.getOccurredAt() != null ? oldEntity.getOccurredAt().toString() : null;
            String newValStr = newEntity.getOccurredAt() != null ? newEntity.getOccurredAt().toString() : null;
            logs.add(buildUpdateLog(newEntity.getInteractionId(), user, roleName,
                    "occurredAt", oldValStr, newValStr));
        }

        // 4. Lead
        UUID oldLeadId = oldEntity.getLead() != null ? oldEntity.getLead().getLeadId() : null;
        UUID newLeadId = newEntity.getLead() != null ? newEntity.getLead().getLeadId() : null;
        if (!Objects.equals(oldLeadId, newLeadId)) {
            String oldName = oldEntity.getLead() != null ? oldEntity.getLead().getFullName() : null;
            String newName = newEntity.getLead() != null ? newEntity.getLead().getFullName() : null;
            logs.add(buildUpdateLog(newEntity.getInteractionId(), user, roleName,
                    "lead", oldName, newName));
        }

        // 5. Customer
        UUID oldCustomerId = oldEntity.getCustomer() != null ? oldEntity.getCustomer().getCustomerId() : null;
        UUID newCustomerId = newEntity.getCustomer() != null ? newEntity.getCustomer().getCustomerId() : null;
        if (!Objects.equals(oldCustomerId, newCustomerId)) {
            String oldName = oldEntity.getCustomer() != null ? oldEntity.getCustomer().getFullName() : null;
            String newName = newEntity.getCustomer() != null ? newEntity.getCustomer().getFullName() : null;
            logs.add(buildUpdateLog(newEntity.getInteractionId(), user, roleName,
                    "customer", oldName, newName));
        }

        // 6. Deal
        UUID oldDealId = oldEntity.getDeal() != null ? oldEntity.getDeal().getDealId() : null;
        UUID newDealId = newEntity.getDeal() != null ? newEntity.getDeal().getDealId() : null;
        if (!Objects.equals(oldDealId, newDealId)) {
            String oldName = oldEntity.getDeal() != null ? oldEntity.getDeal().getDealName() : null;
            String newName = newEntity.getDeal() != null ? newEntity.getDeal().getDealName() : null;
            logs.add(buildUpdateLog(newEntity.getInteractionId(), user, roleName,
                    "deal", oldName, newName));
        }

        if (!logs.isEmpty()) {
            auditLogRepository.saveAll(logs);
        }
    }

    private InteractionAuditLogEntity buildUpdateLog(UUID interactionId, UserEntity user, String roleName,
                                                     String fieldName, String oldValue, String newValue) {
        return InteractionAuditLogEntity.builder()
                .interactionId(interactionId)
                .action("UPDATED")
                .changedBy(user.getUserId())
                .changedByName(user.getFullName())
                .changedByRole(roleName)
                .fieldName(fieldName)
                .oldValue(oldValue)
                .newValue(newValue)
                .build();
    }
}
