package com.novax.leadora.application.usecase.audit;

import com.novax.leadora.infrastructure.persistence.entity.SystemAuditLogEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.repository.SystemAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Generic audit trail writer shared across modules (Quotation, Reminder, SLA, ...) — BR-37.
 * Non-fatal: a logging failure must never roll back the caller's business transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemAuditLogService {

    private final SystemAuditLogRepository auditLogRepository;

    public void log(String module, String entityType, UUID entityId, String action,
                     UserEntity actor, String oldValue, String newValue, String notes) {
        try {
            SystemAuditLogEntity entry = SystemAuditLogEntity.builder()
                    .module(module)
                    .entityType(entityType)
                    .entityId(entityId)
                    .action(action)
                    .actorId(actor != null ? actor.getUserId() : null)
                    .actorName(actor != null ? actor.getFullName() : null)
                    .actorRole(actor != null && actor.getRole() != null ? actor.getRole().getRoleName() : null)
                    .oldValue(oldValue)
                    .newValue(newValue)
                    .notes(notes)
                    .build();
            auditLogRepository.save(entry);
        } catch (Exception e) {
            log.warn("Audit log write failed: module={}, entityType={}, entityId={}, action={}: {}",
                    module, entityType, entityId, action, e.getMessage());
        }
    }
}
