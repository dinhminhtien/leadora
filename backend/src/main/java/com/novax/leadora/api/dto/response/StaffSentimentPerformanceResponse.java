package com.novax.leadora.api.dto.response;

import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaffSentimentPerformanceResponse {
    private UUID staffId;
    private String staffName;
    private String email;
    private String avatarUrl;

    // Feedback metrics
    private long totalFeedbacks;
    private long positiveFeedbacks;
    private long neutralFeedbacks;
    private long negativeFeedbacks;
    private double satisfactionRatio; // Overall CSAT (%)

    // 5-Aspect Satisfaction Matrix (% Positive)
    private double attitudePositiveRatio;
    private double speedPositiveRatio;
    private double accuracyPositiveRatio;
    private double facilityPositiveRatio;
    private double pricePositiveRatio;

    // Sales Performance Correlation
    private long totalDeals;
    private long wonDeals;
    private long lostDeals;
    private double conversionRate; // won / (won + lost) %
    private BigDecimal totalRevenueWon;

    // SLA & Task Correlation
    private long completedTasks;
    private long onTimeTasks;
    private double taskPunctualityRate; // onTime / completed %
    private long overdueTasksCount;

    // AI Highlight Tags
    private String topStrongAspect;
    private String topWeakAspect;
}
