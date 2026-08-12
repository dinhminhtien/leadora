package com.novax.leadora.unit.feedback;

import com.novax.leadora.api.dto.response.StaffSentimentPerformanceResponse;
import com.novax.leadora.application.usecase.feedback.GetStaffSentimentPerformanceUseCase;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.RoleEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.UserStatus;
import com.novax.leadora.infrastructure.persistence.repository.SalesFeedbackRepository;
import com.novax.leadora.infrastructure.persistence.repository.DealRepository;
import com.novax.leadora.infrastructure.persistence.repository.TaskRepository;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import com.novax.leadora.infrastructure.persistence.repository.projection.StaffFeedbackPerformanceProjection;
import com.novax.leadora.infrastructure.persistence.repository.projection.StaffDealPerformanceProjection;
import com.novax.leadora.infrastructure.persistence.repository.projection.StaffTaskPerformanceProjection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetStaffSentimentPerformanceUseCaseTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private SalesFeedbackRepository salesFeedbackRepository;

    @Mock
    private DealRepository dealRepository;

    @Mock
    private TaskRepository taskRepository;

    @InjectMocks
    private GetStaffSentimentPerformanceUseCase getStaffSentimentPerformanceUseCase;

    @Test
    @DisplayName("UT-FEEDBACK-05: Retrieve empty list when no sales staff are active")
    void testExecuteWithNoSalesStaff() {
        when(userRepository.findAll()).thenReturn(Collections.emptyList());

        List<StaffSentimentPerformanceResponse> result = getStaffSentimentPerformanceUseCase.execute(null, null);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(salesFeedbackRepository, never()).aggregateFeedbackPerformance(any(), any());
    }

    @Test
    @DisplayName("UT-FEEDBACK-06: Compile performance metrics successfully for active sales staff")
    void testExecuteSuccess() {
        UUID staffId = UUID.randomUUID();
        RoleEntity role = RoleEntity.builder().roleId(1).roleName("SALES_STAFF").build();
        UserEntity salesStaff = UserEntity.builder()
                .userId(staffId)
                .fullName("John Doe")
                .email("john.doe@leadora.com")
                .status(UserStatus.ACTIVE)
                .role(role)
                .build();

        when(userRepository.findAll()).thenReturn(List.of(salesStaff));

        // 1. Mock Feedback Projection
        StaffFeedbackPerformanceProjection fProj = mock(StaffFeedbackPerformanceProjection.class);
        when(fProj.getStaffId()).thenReturn(staffId);
        when(fProj.getTotalFeedbacks()).thenReturn(1L);
        when(fProj.getTotalAnalyzedFeedbacks()).thenReturn(1L);
        when(fProj.getPositiveFeedbacks()).thenReturn(1L);
        when(fProj.getNeutralFeedbacks()).thenReturn(0L);
        when(fProj.getNegativeFeedbacks()).thenReturn(0L);

        when(fProj.getAttitudePos()).thenReturn(1L);
        when(fProj.getAttitudeCount()).thenReturn(1L);
        when(fProj.getSpeedPos()).thenReturn(1L);
        when(fProj.getSpeedCount()).thenReturn(1L);
        when(fProj.getAccuracyPos()).thenReturn(0L);
        when(fProj.getAccuracyCount()).thenReturn(1L);
        when(fProj.getFacilityPos()).thenReturn(0L);
        when(fProj.getFacilityCount()).thenReturn(0L);
        when(fProj.getPricePos()).thenReturn(0L);
        when(fProj.getPriceCount()).thenReturn(0L);

        when(salesFeedbackRepository.aggregateFeedbackPerformance(any(), any())).thenReturn(List.of(fProj));

        // 2. Mock Deal Projection
        StaffDealPerformanceProjection dProj = mock(StaffDealPerformanceProjection.class);
        when(dProj.getStaffId()).thenReturn(staffId);
        when(dProj.getTotalDeals()).thenReturn(1L);
        when(dProj.getWonDeals()).thenReturn(1L);
        when(dProj.getLostDeals()).thenReturn(0L);
        when(dProj.getTotalRevenueWon()).thenReturn(BigDecimal.valueOf(50000000));

        when(dealRepository.aggregateDealPerformance(any(), any(), any())).thenReturn(List.of(dProj));

        // 3. Mock Task Projection
        StaffTaskPerformanceProjection tProj = mock(StaffTaskPerformanceProjection.class);
        when(tProj.getStaffId()).thenReturn(staffId);
        when(tProj.getCompletedTasks()).thenReturn(1L);
        when(tProj.getOnTimeTasks()).thenReturn(1L);
        when(tProj.getOverdueTasks()).thenReturn(1L);

        when(taskRepository.aggregateTaskPerformance(any(), any(), any())).thenReturn(List.of(tProj));

        List<StaffSentimentPerformanceResponse> result = getStaffSentimentPerformanceUseCase.execute(null, null);

        assertNotNull(result);
        assertEquals(1, result.size());

        StaffSentimentPerformanceResponse response = result.get(0);
        assertEquals(staffId, response.getStaffId());
        assertEquals("John Doe", response.getStaffName());
        assertEquals(1, response.getTotalFeedbacks());
        assertEquals(1, response.getPositiveFeedbacks());
        assertEquals(100.0, response.getSatisfactionRatio());

        // Aspect positive CSAT ratios
        assertEquals(100.0, response.getAttitudePositiveRatio());
        assertEquals(100.0, response.getSpeedPositiveRatio());
        assertEquals(0.0, response.getAccuracyPositiveRatio());
        assertEquals("Attitude", response.getTopStrongAspect());
        assertEquals("Accuracy", response.getTopWeakAspect());

        // Deals
        assertEquals(1, response.getTotalDeals());
        assertEquals(1, response.getWonDeals());
        assertEquals(100.0, response.getConversionRate());
        assertEquals(BigDecimal.valueOf(50000000), response.getTotalRevenueWon());

        // Tasks
        assertEquals(1, response.getCompletedTasks());
        assertEquals(1, response.getOnTimeTasks());
        assertEquals(100.0, response.getTaskPunctualityRate());
        assertEquals(1, response.getOverdueTasksCount());
    }
}
