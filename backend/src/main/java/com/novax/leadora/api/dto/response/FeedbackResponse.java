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
    private String customerName;
    private String bookingCode;
    private String salesStaffName;
    private Short rating;
    private String comment;
    private ReviewStatus reviewStatus;
    private OffsetDateTime submittedAt;
    private String reviewedByName;
    private OffsetDateTime reviewedAt;
    private OffsetDateTime createdAt;
}
