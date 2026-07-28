package com.novax.leadora.unit.activitylog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.novax.leadora.application.usecase.activitylog.ActivityLogCommand;
import com.novax.leadora.application.usecase.activitylog.AppendActivityLogUseCase;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.ActivityLogEntity;
import com.novax.leadora.infrastructure.persistence.entity.RoleEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ActorType;
import com.novax.leadora.infrastructure.persistence.entity.enums.ActivityLogType;
import com.novax.leadora.infrastructure.persistence.entity.enums.EntityType;
import com.novax.leadora.infrastructure.persistence.entity.enums.RecordOperation;
import com.novax.leadora.infrastructure.persistence.repository.ActivityLogRepository;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppendActivityLogUseCaseTest {

    @Mock
    private ActivityLogRepository activityLogRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private AppendActivityLogUseCase appendActivityLogUseCase;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("should append normal log with resolved user actor")
    void shouldAppendNormalLogWithResolvedUserActor() {
        UUID userId = UUID.randomUUID();
        RoleEntity role = RoleEntity.builder().roleName("SALES").build();
        UserEntity user = UserEntity.builder().userId(userId).role(role).fullName("John Sales").build();

        when(currentUserProvider.resolve(null)).thenReturn(user);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        ObjectNode payload = objectMapper.createObjectNode().put("test", "value");
        ActivityLogCommand command = ActivityLogCommand.builder()
                .activityType(ActivityLogType.LEAD_CREATED)
                .entityType(EntityType.LEAD)
                .entityId(UUID.randomUUID())
                .summary("Lead created for testing")
                .payload(payload)
                .build();

        appendActivityLogUseCase.execute(command);

        ArgumentCaptor<ActivityLogEntity> captor = ArgumentCaptor.forClass(ActivityLogEntity.class);
        verify(activityLogRepository, times(1)).save(captor.capture());

        ActivityLogEntity saved = captor.getValue();
        assertThat(saved.getActorType()).isEqualTo(ActorType.USER);
        assertThat(saved.getActorUser().getUserId()).isEqualTo(userId);
        assertThat(saved.getActorRoleSnapshot()).isEqualTo("SALES");
        assertThat(saved.getRecordOperation()).isEqualTo(RecordOperation.NORMAL);
        assertThat(saved.getSummary()).isEqualTo("Lead created for testing");
        assertThat(saved.getPayload().get("test").asText()).isEqualTo("value");
    }

    @Test
    @DisplayName("should default to SYSTEM actor if currentUserProvider throws or returns null")
    void shouldDefaultToSystemActorOnUnauthenticated() {
        when(currentUserProvider.resolve(null)).thenThrow(new RuntimeException("No user"));

        ActivityLogCommand command = ActivityLogCommand.builder()
                .activityType(ActivityLogType.DEAL_AUTO_WON)
                .entityType(EntityType.DEAL)
                .entityId(UUID.randomUUID())
                .summary("Deal auto won by system")
                .build();

        appendActivityLogUseCase.execute(command);

        ArgumentCaptor<ActivityLogEntity> captor = ArgumentCaptor.forClass(ActivityLogEntity.class);
        verify(activityLogRepository, times(1)).save(captor.capture());

        ActivityLogEntity saved = captor.getValue();
        assertThat(saved.getActorType()).isEqualTo(ActorType.SYSTEM);
        assertThat(saved.getActorUser()).isNull();
        assertThat(saved.getActorRoleSnapshot()).isNull();
    }
}
