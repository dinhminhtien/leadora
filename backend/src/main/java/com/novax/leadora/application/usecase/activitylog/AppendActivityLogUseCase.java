package com.novax.leadora.application.usecase.activitylog;

import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.ActivityLogEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ActorType;
import com.novax.leadora.infrastructure.persistence.entity.enums.RecordOperation;
import com.novax.leadora.infrastructure.persistence.repository.ActivityLogRepository;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppendActivityLogUseCase {

    private final ActivityLogRepository activityLogRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void execute(ActivityLogCommand command) {
        ActorType actorType = command.getActorType();
        UUID actorUserId = command.getActorUserId();
        String actorRoleSnapshot = command.getActorRoleSnapshot();

        // Resolve actor automatically if not specified
        if (actorType == null) {
            try {
                UserEntity currentUser = currentUserProvider.resolve(null);
                if (currentUser != null) {
                    actorType = ActorType.USER;
                    actorUserId = currentUser.getUserId();
                    actorRoleSnapshot = currentUser.getRole() != null ? currentUser.getRole().getRoleName() : null;
                } else {
                    actorType = ActorType.SYSTEM;
                }
            } catch (Exception e) {
                actorType = ActorType.SYSTEM;
            }
        }

        UserEntity actorUser = null;
        if (actorUserId != null) {
            actorUser = userRepository.findById(actorUserId).orElse(null);
        }

        RecordOperation recordOperation = command.getRecordOperation();
        if (recordOperation == null) {
            recordOperation = RecordOperation.NORMAL;
        }

        ActivityLogEntity entity = ActivityLogEntity.builder()
                .actorType(actorType)
                .actorUser(actorUser)
                .actorRoleSnapshot(actorRoleSnapshot)
                .activityType(command.getActivityType())
                .entityType(command.getEntityType())
                .entityId(command.getEntityId())
                .summary(command.getSummary())
                .payload(command.getPayload())
                .reason(command.getReason())
                .correlationId(command.getCorrelationId())
                .recordOperation(recordOperation)
                .refActivityId(command.getRefActivityId())
                .build();

        try {
            activityLogRepository.save(entity);
            log.info("Appended activity log entry: id={}, type={}, entity={}", entity.getId(), entity.getActivityType(),
                    entity.getEntityId());
        } catch (Exception ex) {
            log.error("Failed to append activity log entry for activityType={}, entityId={}. Error: {}",
                    command.getActivityType(), command.getEntityId(), ex.getMessage(), ex);
        }
    }
}
