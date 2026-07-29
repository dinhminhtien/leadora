package com.novax.leadora.application.usecase.activitylog;

import com.novax.leadora.infrastructure.persistence.entity.ActivityLogEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ActivityLogType;
import com.novax.leadora.infrastructure.persistence.entity.enums.RecordOperation;
import com.novax.leadora.infrastructure.persistence.repository.ActivityLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditCorrectionService {

    private final ActivityLogRepository activityLogRepository;
    private final ActivityLogPublisher activityLogPublisher;

    private static final Collection<RecordOperation> OPERATIONS_FOR_HEAD = Arrays.asList(
            RecordOperation.NORMAL,
            RecordOperation.CORRECTED,
            RecordOperation.VOIDED);

    @Transactional
    public void voidPriorActivity(UUID entityId, List<ActivityLogType> familyTypes, String reason) {
        Optional<ActivityLogEntity> latestLogOpt = activityLogRepository
                .findFirstByEntityIdAndActivityTypeInAndRecordOperationInOrderByCreatedAtDesc(
                        entityId, familyTypes, OPERATIONS_FOR_HEAD);

        if (latestLogOpt.isEmpty()) {
            log.warn("No prior activity log found to void for entityId={} and familyTypes={}", entityId, familyTypes);
            return;
        }

        ActivityLogEntity latestLog = latestLogOpt.get();

        // Idempotency guard
        if (latestLog.getRecordOperation() == RecordOperation.VOIDED) {
            log.warn("Prior activity log for entityId={} is already VOIDED. No-op.", entityId);
            return;
        }

        ActivityLogCommand voidCommand = ActivityLogCommand.builder()
                .actorType(latestLog.getActorType())
                .actorUserId(latestLog.getActorUser() != null ? latestLog.getActorUser().getUserId() : null)
                .actorRoleSnapshot(latestLog.getActorRoleSnapshot())
                .activityType(latestLog.getActivityType())
                .entityType(latestLog.getEntityType())
                .entityId(latestLog.getEntityId())
                .summary("Voided prior activity: " + latestLog.getSummary())
                .payload(latestLog.getPayload())
                .reason(reason)
                .correlationId(latestLog.getCorrelationId())
                .recordOperation(RecordOperation.VOIDED)
                .refActivityId(latestLog.getId())
                .build();

        activityLogPublisher.publish(voidCommand);
        log.info("Published VOIDED audit log pointing to refActivityId={} for entityId={}", latestLog.getId(),
                entityId);
    }

    @Transactional
    public void correctPriorActivity(UUID entityId, List<ActivityLogType> familyTypes, ActivityLogCommand command) {
        Optional<ActivityLogEntity> latestLogOpt = activityLogRepository
                .findFirstByEntityIdAndActivityTypeInAndRecordOperationInOrderByCreatedAtDesc(
                        entityId, familyTypes, OPERATIONS_FOR_HEAD);

        if (latestLogOpt.isEmpty()) {
            command.setRecordOperation(RecordOperation.NORMAL);
            command.setRefActivityId(null);
            log.info("No prior activity log found for entityId={} and familyTypes={}. Starting new chain (NORMAL).",
                    entityId, familyTypes);
        } else {
            ActivityLogEntity latestLog = latestLogOpt.get();

            if (latestLog.getRecordOperation() == RecordOperation.VOIDED) {
                // Correcting a VOIDED head -> start new chain (NORMAL)
                command.setRecordOperation(RecordOperation.NORMAL);
                command.setRefActivityId(null);
                log.info("Prior activity log for entityId={} is VOIDED. Starting new chain (NORMAL) for correction.",
                        entityId);
            } else {
                command.setRecordOperation(RecordOperation.CORRECTED);
                command.setRefActivityId(latestLog.getId());
                log.info(
                        "Prior activity log for entityId={} is active. Linking correction (CORRECTED) to refActivityId={}",
                        entityId, latestLog.getId());
            }
        }

        activityLogPublisher.publish(command);
    }
}
