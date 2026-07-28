package com.novax.leadora.unit.activitylog;

import com.novax.leadora.application.usecase.activitylog.GetActivityLogUseCase;
import com.novax.leadora.infrastructure.persistence.entity.ActivityLogEntity;
import com.novax.leadora.infrastructure.persistence.repository.ActivityLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentMatchers;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetActivityLogUseCaseTest {

    @Mock
    private ActivityLogRepository activityLogRepository;

    @InjectMocks
    private GetActivityLogUseCase getActivityLogUseCase;

    @Test
    @DisplayName("should query repository with specifications and return paginated page")
    void shouldQueryRepositoryWithSpecs() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<ActivityLogEntity> expectedPage = new PageImpl<>(List.of(new ActivityLogEntity()));

        when(activityLogRepository.findAll(ArgumentMatchers.<Specification<ActivityLogEntity>>any(), eq(pageable)))
                .thenReturn(expectedPage);

        GetActivityLogUseCase.FilterQuery query = GetActivityLogUseCase.FilterQuery.builder()
                .keyword("test")
                .view("EFFECTIVE")
                .build();

        Page<ActivityLogEntity> result = getActivityLogUseCase.execute(query, pageable);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        verify(activityLogRepository, times(1)).findAll(ArgumentMatchers.<Specification<ActivityLogEntity>>any(),
                eq(pageable));
    }
}
