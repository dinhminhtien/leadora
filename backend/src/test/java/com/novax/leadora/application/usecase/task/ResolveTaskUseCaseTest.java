package com.novax.leadora.application.usecase.task;

import com.novax.leadora.api.dto.response.TaskResponse;
import com.novax.leadora.application.usecase.sla.ResolveSlaBreachUseCase;
import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.infrastructure.persistence.entity.TaskEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.TaskStatus;
import com.novax.leadora.infrastructure.persistence.repository.ReminderRepository;
import com.novax.leadora.infrastructure.persistence.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ResolveTaskUseCaseTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private ResolveSlaBreachUseCase resolveSlaBreachUseCase;

    @Mock
    private ReminderRepository reminderRepository;

    @Mock
    private TaskAccessPolicy accessPolicy;

    @Mock
    private TaskNotifier taskNotifier;

    private ResolveTaskUseCase resolveTaskUseCase;

    @BeforeEach
    void setUp() {
        resolveTaskUseCase = new ResolveTaskUseCase(
                taskRepository,
                resolveSlaBreachUseCase,
                reminderRepository,
                accessPolicy,
                taskNotifier
        );
    }

    @Test
    void execute_withNote_shouldCompleteTask() {
        UUID taskId = UUID.randomUUID();
        TaskEntity task = TaskEntity.builder()
                .taskId(taskId)
                .status(TaskStatus.OPEN)
                .build();

        UserEntity currentUser = new UserEntity();
        when(accessPolicy.currentUser()).thenReturn(currentUser);
        when(taskRepository.findWithRelationsById(taskId)).thenReturn(Optional.of(task));
        when(reminderRepository.findByRelatedEntityAndRelatedId("TASK", taskId)).thenReturn(new ArrayList<>());

        TaskResponse response = resolveTaskUseCase.execute(taskId, "Completed task successfully");

        assertThat(response.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(task.getResultNote()).isEqualTo("Completed task successfully");
        verify(taskRepository).save(task);
    }

    @Test
    void execute_withoutNote_shouldThrowException() {
        UUID taskId = UUID.randomUUID();

        assertThatThrownBy(() -> resolveTaskUseCase.execute(taskId, ""))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("httpStatus", HttpStatus.BAD_REQUEST)
                .hasMessageContaining("Task completion note is required.");
    }
}
