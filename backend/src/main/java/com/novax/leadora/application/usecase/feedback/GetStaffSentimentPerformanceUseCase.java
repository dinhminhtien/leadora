package com.novax.leadora.application.usecase.feedback;

import com.novax.leadora.api.dto.response.StaffSentimentPerformanceResponse;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.UserStatus;
import com.novax.leadora.infrastructure.persistence.repository.SalesFeedbackRepository;
import com.novax.leadora.infrastructure.persistence.repository.DealRepository;
import com.novax.leadora.infrastructure.persistence.repository.TaskRepository;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import com.novax.leadora.infrastructure.persistence.repository.projection.StaffFeedbackPerformanceProjection;
import com.novax.leadora.infrastructure.persistence.repository.projection.StaffDealPerformanceProjection;
import com.novax.leadora.infrastructure.persistence.repository.projection.StaffTaskPerformanceProjection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GetStaffSentimentPerformanceUseCase {

    private final UserRepository userRepository;
    private final SalesFeedbackRepository salesFeedbackRepository;
    private final DealRepository dealRepository;
    private final TaskRepository taskRepository;

    @Transactional(readOnly = true)
    public List<StaffSentimentPerformanceResponse> execute(OffsetDateTime startDate, OffsetDateTime endDate) {
        OffsetDateTime start = startDate != null ? startDate : OffsetDateTime.of(1970, 1, 1, 0, 0, 0, 0, java.time.ZoneOffset.UTC);
        OffsetDateTime end = endDate != null ? endDate : OffsetDateTime.of(9999, 12, 31, 23, 59, 59, 0, java.time.ZoneOffset.UTC);

        // 1. Fetch active sales reps
        List<UserEntity> salesReps = userRepository.findAll().stream()
                .filter(u -> u.getStatus() == UserStatus.ACTIVE && u.getRole() != null &&
                        (u.getRole().getRoleName().trim().toUpperCase().contains("SALES") ||
                         u.getRole().getRoleName().trim().toUpperCase().contains("STAFF")))
                .toList();

        if (salesReps.isEmpty()) {
            return Collections.emptyList();
        }

        List<UUID> staffIds = salesReps.stream().map(u -> u.getUserId()).toList();

        // 2. Fetch aggregated performance metrics from database using projections
        Map<UUID, StaffFeedbackPerformanceProjection> feedbackMap = salesFeedbackRepository
                .aggregateFeedbackPerformance(start, end).stream()
                .collect(Collectors.toMap(p -> p.getStaffId(), p -> p, (p1, p2) -> p1));

        Map<UUID, StaffDealPerformanceProjection> dealMap = dealRepository
                .aggregateDealPerformance(start, end, staffIds).stream()
                .collect(Collectors.toMap(p -> p.getStaffId(), p -> p, (p1, p2) -> p1));

        Map<UUID, StaffTaskPerformanceProjection> taskMap = taskRepository
                .aggregateTaskPerformance(start, end, staffIds).stream()
                .collect(Collectors.toMap(p -> p.getStaffId(), p -> p, (p1, p2) -> p1));

        List<StaffSentimentPerformanceResponse> response = new ArrayList<>();

        // 3. Compile responses for each sales rep
        for (UserEntity user : salesReps) {
            UUID userId = user.getUserId();
            StaffFeedbackPerformanceProjection fProj = feedbackMap.get(userId);
            StaffDealPerformanceProjection dProj = dealMap.get(userId);
            StaffTaskPerformanceProjection tProj = taskMap.get(userId);

            long totalFeedbacks = fProj != null ? fProj.getTotalFeedbacks() : 0;
            long analyzed = fProj != null ? fProj.getTotalAnalyzedFeedbacks() : 0;
            long positiveFeedbacks = fProj != null ? fProj.getPositiveFeedbacks() : 0;
            long neutralFeedbacks = fProj != null ? fProj.getNeutralFeedbacks() : 0;
            long negativeFeedbacks = fProj != null ? fProj.getNegativeFeedbacks() : 0;

            // Ratios calculations (CSAT is strictly calculated based on analyzed feedbacks)
            double satisfactionRatio = analyzed > 0 ? Math.round((positiveFeedbacks * 100.0 / analyzed) * 100.0) / 100.0 : 0.0;

            long attPos = fProj != null ? fProj.getAttitudePos() : 0;
            long attCount = fProj != null ? fProj.getAttitudeCount() : 0;
            double attitudePositiveRatio = attCount > 0 ? Math.round((attPos * 100.0 / attCount) * 100.0) / 100.0 : 0.0;

            long spdPos = fProj != null ? fProj.getSpeedPos() : 0;
            long spdCount = fProj != null ? fProj.getSpeedCount() : 0;
            double speedPositiveRatio = spdCount > 0 ? Math.round((spdPos * 100.0 / spdCount) * 100.0) / 100.0 : 0.0;

            long accPos = fProj != null ? fProj.getAccuracyPos() : 0;
            long accCount = fProj != null ? fProj.getAccuracyCount() : 0;
            double accuracyPositiveRatio = accCount > 0 ? Math.round((accPos * 100.0 / accCount) * 100.0) / 100.0 : 0.0;

            long facPos = fProj != null ? fProj.getFacilityPos() : 0;
            long facCount = fProj != null ? fProj.getFacilityCount() : 0;
            double facilityPositiveRatio = facCount > 0 ? Math.round((facPos * 100.0 / facCount) * 100.0) / 100.0 : 0.0;

            long prcPos = fProj != null ? fProj.getPricePos() : 0;
            long prcCount = fProj != null ? fProj.getPriceCount() : 0;
            double pricePositiveRatio = prcCount > 0 ? Math.round((prcPos * 100.0 / prcCount) * 100.0) / 100.0 : 0.0;

            // Highlight aspects
            Map<String, Double> posRatios = new LinkedHashMap<>();
            posRatios.put("Attitude", attitudePositiveRatio);
            posRatios.put("Speed", speedPositiveRatio);
            posRatios.put("Accuracy", accuracyPositiveRatio);
            posRatios.put("Facility", facilityPositiveRatio);
            posRatios.put("Price", pricePositiveRatio);

            String topStrongAspect = "N/A";
            if (analyzed > 0) {
                double maxPos = -1.0;
                for (Map.Entry<String, Double> entry : posRatios.entrySet()) {
                    if (entry.getValue() > maxPos) {
                        maxPos = entry.getValue();
                        topStrongAspect = entry.getKey();
                    }
                }
            }

            // Negative ratios
            long attNeg = fProj != null ? (fProj.getAttitudeCount() - fProj.getAttitudePos()) : 0; // estimate negative/neutral
            double attitudeNegativeRatio = attCount > 0 ? Math.round((attNeg * 100.0 / attCount) * 100.0) / 100.0 : 0.0;

            long spdNeg = fProj != null ? (fProj.getSpeedCount() - fProj.getSpeedPos()) : 0;
            double speedNegativeRatio = spdCount > 0 ? Math.round((spdNeg * 100.0 / spdCount) * 100.0) / 100.0 : 0.0;

            long accNeg = fProj != null ? (fProj.getAccuracyCount() - fProj.getAccuracyPos()) : 0;
            double accuracyNegativeRatio = accCount > 0 ? Math.round((accNeg * 100.0 / accCount) * 100.0) / 100.0 : 0.0;

            long facNeg = fProj != null ? (fProj.getFacilityCount() - fProj.getFacilityPos()) : 0;
            double facilityNegativeRatio = facCount > 0 ? Math.round((facNeg * 100.0 / facCount) * 100.0) / 100.0 : 0.0;

            long prcNeg = fProj != null ? (fProj.getPriceCount() - fProj.getPricePos()) : 0;
            double priceNegativeRatio = prcCount > 0 ? Math.round((prcNeg * 100.0 / prcCount) * 100.0) / 100.0 : 0.0;

            Map<String, Double> negRatios = new LinkedHashMap<>();
            negRatios.put("Attitude", attitudeNegativeRatio);
            negRatios.put("Speed", speedNegativeRatio);
            negRatios.put("Accuracy", accuracyNegativeRatio);
            negRatios.put("Facility", facilityNegativeRatio);
            negRatios.put("Price", priceNegativeRatio);

            String topWeakAspect = "N/A";
            if (analyzed > 0) {
                double maxNeg = -1.0;
                for (Map.Entry<String, Double> entry : negRatios.entrySet()) {
                    if (entry.getValue() > maxNeg) {
                        maxNeg = entry.getValue();
                        topWeakAspect = entry.getKey();
                    }
                }
            }

            // Deal metrics
            long totalDeals = dProj != null ? dProj.getTotalDeals() : 0;
            long wonDeals = dProj != null ? dProj.getWonDeals() : 0;
            long lostDeals = dProj != null ? dProj.getLostDeals() : 0;
            BigDecimal totalRevenueWon = dProj != null && dProj.getTotalRevenueWon() != null ? dProj.getTotalRevenueWon() : BigDecimal.ZERO;
            double conversionRate = (wonDeals + lostDeals) > 0 ? Math.round((wonDeals * 100.0 / (wonDeals + lostDeals)) * 100.0) / 100.0 : 0.0;

            // Task metrics
            long completedTasks = tProj != null ? tProj.getCompletedTasks() : 0;
            long onTimeTasks = tProj != null ? tProj.getOnTimeTasks() : 0;
            long overdueTasksCount = tProj != null ? tProj.getOverdueTasks() : 0;
            double taskPunctualityRate = completedTasks > 0 ? Math.round((onTimeTasks * 100.0 / completedTasks) * 100.0) / 100.0 : 0.0;

            response.add(StaffSentimentPerformanceResponse.builder()
                    .staffId(user.getUserId())
                    .staffName(user.getFullName())
                    .email(user.getEmail())
                    .avatarUrl(user.getAvatarUrl())
                    .totalFeedbacks(totalFeedbacks)
                    .positiveFeedbacks(positiveFeedbacks)
                    .neutralFeedbacks(neutralFeedbacks)
                    .negativeFeedbacks(negativeFeedbacks)
                    .satisfactionRatio(satisfactionRatio)
                    .attitudePositiveRatio(attitudePositiveRatio)
                    .speedPositiveRatio(speedPositiveRatio)
                    .accuracyPositiveRatio(accuracyPositiveRatio)
                    .facilityPositiveRatio(facilityPositiveRatio)
                    .pricePositiveRatio(pricePositiveRatio)
                    .totalDeals(totalDeals)
                    .wonDeals(wonDeals)
                    .lostDeals(lostDeals)
                    .conversionRate(conversionRate)
                    .totalRevenueWon(totalRevenueWon)
                    .completedTasks(completedTasks)
                    .onTimeTasks(onTimeTasks)
                    .taskPunctualityRate(taskPunctualityRate)
                    .overdueTasksCount(overdueTasksCount)
                    .topStrongAspect(topStrongAspect)
                    .topWeakAspect(topWeakAspect)
                    .build());
        }

        return response;
    }
}
