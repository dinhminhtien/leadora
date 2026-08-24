package com.novax.leadora.unit.task;

import com.novax.leadora.api.dto.response.TaskStatsResponse;
import com.novax.leadora.application.usecase.task.GetTaskStatsUseCase;
import com.novax.leadora.application.usecase.task.TaskAccessPolicy;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class GetTaskStatsUseCaseTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private TaskAccessPolicy accessPolicy;

    private GetTaskStatsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetTaskStatsUseCase(taskRepository, accessPolicy);
    }

    @Test
    void execute_unscopedUser_returnsAggregatedCounts() {
        UserEntity manager = UserEntity.builder().userId(UUID.randomUUID()).build();
        when(accessPolicy.currentUser()).thenReturn(manager);
        when(accessPolicy.listScopeOwnerId(manager)).thenReturn(null);

        when(taskRepository.count(any(Specification.class)))
                .thenReturn(100L) // total
                .thenReturn(40L)  // open
                .thenReturn(50L)  // completed
                .thenReturn(10L); // overdue

        TaskStatsResponse stats = useCase.execute(null, null, null, null);

        assertThat(stats).isNotNull();
        assertThat(stats.getTotal()).isEqualTo(100L);
        assertThat(stats.getOpen()).isEqualTo(40L);
        assertThat(stats.getCompleted()).isEqualTo(50L);
        assertThat(stats.getOverdue()).isEqualTo(10L);
    }

    @Test
    void execute_salesScopedUser_appliesScopedUser() {
        UUID salesId = UUID.randomUUID();
        UserEntity salesRep = UserEntity.builder().userId(salesId).build();
        when(accessPolicy.currentUser()).thenReturn(salesRep);
        when(accessPolicy.listScopeOwnerId(salesRep)).thenReturn(salesId);

        when(taskRepository.count(any(Specification.class)))
                .thenReturn(25L)
                .thenReturn(15L)
                .thenReturn(8L)
                .thenReturn(2L);

        TaskStatsResponse stats = useCase.execute("Urgent", "HIGH", null, null);

        assertThat(stats).isNotNull();
        assertThat(stats.getTotal()).isEqualTo(25L);
        assertThat(stats.getOpen()).isEqualTo(15L);
        assertThat(stats.getCompleted()).isEqualTo(8L);
        assertThat(stats.getOverdue()).isEqualTo(2L);
    }
}
