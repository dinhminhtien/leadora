package com.novax.leadora.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.novax.leadora.infrastructure.persistence.entity.enums.ReviewStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FeedbackResponse {
    private UUID feedbackId;
    private UUID customerId;
    private String customerName;
    private String bookingCode;
    private String salesStaffName;
    private Short rating;
    private Short ratingAttitude;
    private Short ratingSpeed;
    private Short ratingAccuracy;
    private String comment;
    private ReviewStatus reviewStatus;
    private OffsetDateTime submittedAt;
    private String reviewedByName;
    private OffsetDateTime reviewedAt;
    private OffsetDateTime createdAt;

    private String absaAttitudeSentiment;
    private java.math.BigDecimal absaAttitudeConfidence;
    private String absaSpeedSentiment;
    private java.math.BigDecimal absaSpeedConfidence;
    private String absaAccuracySentiment;
    private java.math.BigDecimal absaAccuracyConfidence;
    private String absaFacilitySentiment;
    private java.math.BigDecimal absaFacilityConfidence;
    private String absaPriceSentiment;
    private java.math.BigDecimal absaPriceConfidence;
    private String absaStatus;
}

