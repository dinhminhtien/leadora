package com.novax.leadora.unit.activitylog;

import com.novax.leadora.application.usecase.activitylog.ActivityLogCommand;
import com.novax.leadora.application.usecase.activitylog.AppendActivityLogUseCase;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.ActivityLogEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ActivityLogType;
import com.novax.leadora.infrastructure.persistence.entity.enums.EntityType;
import com.novax.leadora.infrastructure.persistence.repository.ActivityLogRepository;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActivityLogConcurrentTest {

    @Mock
    private ActivityLogRepository activityLogRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private AppendActivityLogUseCase appendActivityLogUseCase;

    @Test
    @DisplayName("should handle 50 concurrent appends without data loss or race conditions")
    void testConcurrentAppends() throws InterruptedException {
        // Setup a thread-safe list to collect saved entities
        List<ActivityLogEntity> savedEntities = Collections.synchronizedList(new ArrayList<>());
        
        when(currentUserProvider.resolve(null)).thenReturn(null); // defaults to SYSTEM actor
        when(activityLogRepository.save(any(ActivityLogEntity.class))).thenAnswer(invocation -> {
            ActivityLogEntity entity = invocation.getArgument(0);
            savedEntities.add(entity);
            return entity;
        });

        int threadCount = 50;
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    ActivityLogCommand command = ActivityLogCommand.builder()
                            .activityType(ActivityLogType.LEAD_CREATED)
                            .entityType(EntityType.LEAD)
                            .entityId(UUID.randomUUID())
                            .summary("Concurrent test log " + index)
                            .build();
                    appendActivityLogUseCase.execute(command);
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean finished = latch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        assertThat(finished).isTrue();
        assertThat(savedEntities).hasSize(threadCount);

        // Ensure each saved entity is unique
        Set<String> summaries = new HashSet<>();
        for (ActivityLogEntity entity : savedEntities) {
            summaries.add(entity.getSummary());
        }
        assertThat(summaries).hasSize(threadCount);
    }
}
