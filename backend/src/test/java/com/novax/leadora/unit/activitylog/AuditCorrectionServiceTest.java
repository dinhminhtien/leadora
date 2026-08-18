package com.novax.leadora.unit.activitylog;

import com.novax.leadora.application.event.AuditCorrectionEvent;
import com.novax.leadora.application.event.BusinessActivityEvent;
import com.novax.leadora.application.listener.ActivityLogListener;
import com.novax.leadora.application.usecase.activitylog.ActivityLogCommand;
import com.novax.leadora.application.usecase.activitylog.ActivityLogPublisher;
import com.novax.leadora.application.usecase.activitylog.AppendActivityLogSameTransactionUseCase;
import com.novax.leadora.application.usecase.activitylog.AppendActivityLogUseCase;
import com.novax.leadora.application.usecase.activitylog.AuditCorrectionService;
import com.novax.leadora.infrastructure.activitylog.SpringActivityLogPublisher;
import com.novax.leadora.infrastructure.persistence.entity.ActivityLogEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ActivityLogType;
import com.novax.leadora.infrastructure.persistence.entity.enums.EntityType;
import com.novax.leadora.infrastructure.persistence.entity.enums.RecordOperation;
import com.novax.leadora.infrastructure.persistence.repository.ActivityLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditCorrectionServiceTest {

        @Mock
        private ActivityLogRepository activityLogRepository;

        @Mock
        private ActivityLogPublisher activityLogPublisher;

        @InjectMocks
        private AuditCorrectionService auditCorrectionService;

        @Mock
        private AppendActivityLogUseCase appendActivityLogUseCase;

        @Mock
        private AppendActivityLogSameTransactionUseCase appendActivityLogSameTransactionUseCase;

        @InjectMocks
        private ActivityLogListener activityLogListener;

        @Mock
        private ApplicationEventPublisher eventPublisher;

        @InjectMocks
        private SpringActivityLogPublisher springActivityLogPublisher;

        private static final List<ActivityLogType> LEAD_FAMILY_TYPES = List.of(
                        ActivityLogType.LEAD_CREATED,
                        ActivityLogType.LEAD_STATUS_UPDATED,
                        ActivityLogType.LEAD_CONVERTED,
                        ActivityLogType.LEAD_UPDATED);

        @Test
        @DisplayName("correctPriorActivity: should link correction to prior active head (NORMAL)")
        void shouldLinkCorrectionToPriorActiveHeadNormal() {
                UUID entityId = UUID.randomUUID();
                UUID priorLogId = UUID.randomUUID();
                ActivityLogEntity priorLog = ActivityLogEntity.builder()
                                .id(priorLogId)
                                .activityType(ActivityLogType.LEAD_CREATED)
                                .entityType(EntityType.LEAD)
                                .entityId(entityId)
                                .summary("Initial creation")
                                .recordOperation(RecordOperation.NORMAL)
                                .build();

                when(activityLogRepository.findFirstByEntityIdAndActivityTypeInAndRecordOperationInOrderByCreatedAtDesc(
                                eq(entityId), eq(LEAD_FAMILY_TYPES), any()))
                                .thenReturn(Optional.of(priorLog));

                ActivityLogCommand command = ActivityLogCommand.builder()
                                .activityType(ActivityLogType.LEAD_UPDATED)
                                .entityType(EntityType.LEAD)
                                .entityId(entityId)
                                .summary("First update")
                                .build();

                auditCorrectionService.correctPriorActivity(entityId, LEAD_FAMILY_TYPES, command);

                assertThat(command.getRecordOperation()).isEqualTo(RecordOperation.CORRECTED);
                assertThat(command.getRefActivityId()).isEqualTo(priorLogId);
                verify(activityLogPublisher, times(1)).publish(command);
        }

        @Test
        @DisplayName("correctPriorActivity: should link correction to prior active head (CORRECTED)")
        void shouldLinkCorrectionToPriorActiveHeadCorrected() {
                UUID entityId = UUID.randomUUID();
                UUID priorLogId = UUID.randomUUID();
                ActivityLogEntity priorLog = ActivityLogEntity.builder()
                                .id(priorLogId)
                                .activityType(ActivityLogType.LEAD_UPDATED)
                                .entityType(EntityType.LEAD)
                                .entityId(entityId)
                                .summary("First update")
                                .recordOperation(RecordOperation.CORRECTED)
                                .build();

                when(activityLogRepository.findFirstByEntityIdAndActivityTypeInAndRecordOperationInOrderByCreatedAtDesc(
                                eq(entityId), eq(LEAD_FAMILY_TYPES), any()))
                                .thenReturn(Optional.of(priorLog));

                ActivityLogCommand command = ActivityLogCommand.builder()
                                .activityType(ActivityLogType.LEAD_UPDATED)
                                .entityType(EntityType.LEAD)
                                .entityId(entityId)
                                .summary("Second update")
                                .build();

                auditCorrectionService.correctPriorActivity(entityId, LEAD_FAMILY_TYPES, command);

                assertThat(command.getRecordOperation()).isEqualTo(RecordOperation.CORRECTED);
                assertThat(command.getRefActivityId()).isEqualTo(priorLogId);
                verify(activityLogPublisher, times(1)).publish(command);
        }

        @Test
        @DisplayName("correctPriorActivity: should start new chain (NORMAL) if prior head is VOIDED")
        void shouldStartNewChainIfPriorHeadIsVoided() {
                UUID entityId = UUID.randomUUID();
                UUID priorLogId = UUID.randomUUID();
                ActivityLogEntity priorLog = ActivityLogEntity.builder()
                                .id(priorLogId)
                                .activityType(ActivityLogType.LEAD_UPDATED)
                                .entityType(EntityType.LEAD)
                                .entityId(entityId)
                                .summary("Voided log")
                                .recordOperation(RecordOperation.VOIDED)
                                .build();

                when(activityLogRepository.findFirstByEntityIdAndActivityTypeInAndRecordOperationInOrderByCreatedAtDesc(
                                eq(entityId), eq(LEAD_FAMILY_TYPES), any()))
                                .thenReturn(Optional.of(priorLog));

                ActivityLogCommand command = ActivityLogCommand.builder()
                                .activityType(ActivityLogType.LEAD_UPDATED)
                                .entityType(EntityType.LEAD)
                                .entityId(entityId)
                                .summary("Correction after void")
                                .build();

                auditCorrectionService.correctPriorActivity(entityId, LEAD_FAMILY_TYPES, command);

                assertThat(command.getRecordOperation()).isEqualTo(RecordOperation.NORMAL);
                assertThat(command.getRefActivityId()).isNull();
                verify(activityLogPublisher, times(1)).publish(command);
        }

        @Test
        @DisplayName("correctPriorActivity: should start new chain (NORMAL) if no prior log exists")
        void shouldStartNewChainIfNoPriorLogExists() {
                UUID entityId = UUID.randomUUID();

                when(activityLogRepository.findFirstByEntityIdAndActivityTypeInAndRecordOperationInOrderByCreatedAtDesc(
                                eq(entityId), eq(LEAD_FAMILY_TYPES), any()))
                                .thenReturn(Optional.empty());

                ActivityLogCommand command = ActivityLogCommand.builder()
                                .activityType(ActivityLogType.LEAD_UPDATED)
                                .entityType(EntityType.LEAD)
                                .entityId(entityId)
                                .summary("Correction on empty")
                                .build();

                auditCorrectionService.correctPriorActivity(entityId, LEAD_FAMILY_TYPES, command);

                assertThat(command.getRecordOperation()).isEqualTo(RecordOperation.NORMAL);
                assertThat(command.getRefActivityId()).isNull();
                verify(activityLogPublisher, times(1)).publish(command);
        }

        @Test
        @DisplayName("voidPriorActivity: should create VOIDED log pointing to prior active head")
        void shouldCreateVoidedLogPointingToPriorActiveHead() {
                UUID entityId = UUID.randomUUID();
                UUID priorLogId = UUID.randomUUID();
                UUID correlationId = UUID.randomUUID();
                UserEntity actor = UserEntity.builder().userId(UUID.randomUUID()).build();
                ActivityLogEntity priorLog = ActivityLogEntity.builder()
                                .id(priorLogId)
                                .actorUser(actor)
                                .actorRoleSnapshot("SALES")
                                .activityType(ActivityLogType.LEAD_CREATED)
                                .entityType(EntityType.LEAD)
                                .entityId(entityId)
                                .summary("Initial creation")
                                .correlationId(correlationId)
                                .recordOperation(RecordOperation.NORMAL)
                                .build();

                when(activityLogRepository.findFirstByEntityIdAndActivityTypeInAndRecordOperationInOrderByCreatedAtDesc(
                                eq(entityId), eq(LEAD_FAMILY_TYPES), any()))
                                .thenReturn(Optional.of(priorLog));

                auditCorrectionService.voidPriorActivity(entityId, LEAD_FAMILY_TYPES, "Incorrect entry");

                ArgumentCaptor<ActivityLogCommand> captor = ArgumentCaptor.forClass(ActivityLogCommand.class);
                verify(activityLogPublisher, times(1)).publish(captor.capture());

                ActivityLogCommand voidCommand = captor.getValue();
                assertThat(voidCommand.getRecordOperation()).isEqualTo(RecordOperation.VOIDED);
                assertThat(voidCommand.getRefActivityId()).isEqualTo(priorLogId);
                assertThat(voidCommand.getActivityType()).isEqualTo(ActivityLogType.LEAD_CREATED);
                assertThat(voidCommand.getReason()).isEqualTo("Incorrect entry");
                assertThat(voidCommand.getCorrelationId()).isEqualTo(correlationId);
                assertThat(voidCommand.getActorUserId()).isEqualTo(actor.getUserId());
        }

        @Test
        @DisplayName("voidPriorActivity: should return early (no-op) if already VOIDED (idempotency)")
        void shouldReturnEarlyIfAlreadyVoided() {
                UUID entityId = UUID.randomUUID();
                ActivityLogEntity priorLog = ActivityLogEntity.builder()
                                .id(UUID.randomUUID())
                                .activityType(ActivityLogType.LEAD_CREATED)
                                .entityType(EntityType.LEAD)
                                .entityId(entityId)
                                .summary("Already voided")
                                .recordOperation(RecordOperation.VOIDED)
                                .build();

                when(activityLogRepository.findFirstByEntityIdAndActivityTypeInAndRecordOperationInOrderByCreatedAtDesc(
                                eq(entityId), eq(LEAD_FAMILY_TYPES), any()))
                                .thenReturn(Optional.of(priorLog));

                auditCorrectionService.voidPriorActivity(entityId, LEAD_FAMILY_TYPES, "Duplicate void request");

                verifyNoInteractions(activityLogPublisher);
        }

        @Test
        @DisplayName("SpringActivityLogPublisher: should route CORRECTED and VOIDED commands via AuditCorrectionEvent")
        void shouldRouteCorrectedAndVoidedViaAuditCorrectionEvent() {
                ActivityLogCommand correctedCmd = ActivityLogCommand.builder()
                                .activityType(ActivityLogType.LEAD_UPDATED)
                                .recordOperation(RecordOperation.CORRECTED)
                                .build();

                ActivityLogCommand voidedCmd = ActivityLogCommand.builder()
                                .activityType(ActivityLogType.LEAD_UPDATED)
                                .recordOperation(RecordOperation.VOIDED)
                                .build();

                ActivityLogCommand normalCmd = ActivityLogCommand.builder()
                                .activityType(ActivityLogType.LEAD_CREATED)
                                .recordOperation(RecordOperation.NORMAL)
                                .build();

                // 1. Publish CORRECTED
                springActivityLogPublisher.publish(correctedCmd);
                ArgumentCaptor<Object> captor1 = ArgumentCaptor.forClass(Object.class);
                verify(eventPublisher, times(1)).publishEvent(captor1.capture());
                assertThat(captor1.getValue()).isInstanceOf(AuditCorrectionEvent.class);
                assertThat(((AuditCorrectionEvent) captor1.getValue()).command()).isEqualTo(correctedCmd);

                // 2. Publish VOIDED
                reset(eventPublisher);
                springActivityLogPublisher.publish(voidedCmd);
                ArgumentCaptor<Object> captor2 = ArgumentCaptor.forClass(Object.class);
                verify(eventPublisher, times(1)).publishEvent(captor2.capture());
                assertThat(captor2.getValue()).isInstanceOf(AuditCorrectionEvent.class);
                assertThat(((AuditCorrectionEvent) captor2.getValue()).command()).isEqualTo(voidedCmd);

                // 3. Publish NORMAL (should go to BusinessActivityEvent)
                reset(eventPublisher);
                springActivityLogPublisher.publish(normalCmd);
                ArgumentCaptor<Object> captor3 = ArgumentCaptor.forClass(Object.class);
                verify(eventPublisher, times(1)).publishEvent(captor3.capture());
                assertThat(captor3.getValue()).isInstanceOf(BusinessActivityEvent.class);
                assertThat(((BusinessActivityEvent) captor3.getValue()).command()).isEqualTo(normalCmd);
        }

        @Test
        @DisplayName("ActivityLogListener: should process AuditCorrectionEvent synchronously using AppendActivityLogSameTransactionUseCase")
        void shouldProcessAuditCorrectionEventSynchronously() {
                ActivityLogCommand command = ActivityLogCommand.builder()
                                .activityType(ActivityLogType.LEAD_UPDATED)
                                .recordOperation(RecordOperation.CORRECTED)
                                .build();
                AuditCorrectionEvent event = new AuditCorrectionEvent(command);

                activityLogListener.handleCorrectionActivity(event);

                verify(appendActivityLogSameTransactionUseCase, times(1)).execute(command);
                verifyNoInteractions(appendActivityLogUseCase);
        }
}
