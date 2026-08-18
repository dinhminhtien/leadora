package com.novax.leadora.application.usecase.feedback;

import com.novax.leadora.api.dto.response.FeedbackTokenValidationResponse;
import com.novax.leadora.common.exception.BusinessRuleException;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.SalesFeedbackEntity;
import com.novax.leadora.infrastructure.persistence.repository.SalesFeedbackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.novax.leadora.application.usecase.activitylog.ActivityLogCommand;
import com.novax.leadora.application.usecase.activitylog.ActivityLogPublisher;
import com.novax.leadora.infrastructure.persistence.entity.enums.ActorType;
import com.novax.leadora.infrastructure.persistence.entity.enums.ActivityLogType;
import com.novax.leadora.infrastructure.persistence.entity.enums.EntityType;
import java.util.UUID;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class ValidateFeedbackTokenUseCase {

    private final SalesFeedbackRepository salesFeedbackRepository;
    private final ActivityLogPublisher activityLogPublisher;

    private static final UUID SYSTEM_UUID = new UUID(0L, 0L);

    @Transactional(readOnly = true)
    public FeedbackTokenValidationResponse execute(String token) {
        SalesFeedbackEntity feedback = salesFeedbackRepository.findByFeedbackToken(token)
                .orElse(null);
        if (feedback == null) {
            activityLogPublisher.publish(ActivityLogCommand.builder()
                    .actorType(ActorType.SYSTEM)
                    .activityType(ActivityLogType.INVALID_TOKEN_ACCESS)
                    .entityType(EntityType.USER)
                    .entityId(SYSTEM_UUID)
                    .summary("Access denied: Invalid feedback token attempted: " + token)
                    .build());
            throw new ResourceNotFoundException("Feedback invitation", token);
        }

        // Use constant-time comparison to prevent timing attacks
        byte[] tokenBytes = token.getBytes(StandardCharsets.UTF_8);
        byte[] dbTokenBytes = feedback.getFeedbackToken().getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(tokenBytes, dbTokenBytes)) {
            activityLogPublisher.publish(ActivityLogCommand.builder()
                    .actorType(ActorType.SYSTEM)
                    .activityType(ActivityLogType.INVALID_TOKEN_ACCESS)
                    .entityType(EntityType.USER)
                    .entityId(SYSTEM_UUID)
                    .summary("Access denied: Feedback token comparison failed.")
                    .build());
            throw new BusinessRuleException("Invalid feedback token");
        }

        // Check if token has expired
        if (feedback.getTokenExpiresAt() != null && feedback.getTokenExpiresAt().isBefore(OffsetDateTime.now())) {
            String bookingCode = feedback.getBooking() != null ? feedback.getBooking().getBookingCode() : "N/A";
            activityLogPublisher.publish(ActivityLogCommand.builder()
                    .actorType(ActorType.SYSTEM)
                    .activityType(ActivityLogType.FEEDBACK_LINK_EXPIRED)
                    .entityType(EntityType.USER)
                    .entityId(SYSTEM_UUID)
                    .summary("Access denied: Feedback link has expired for booking code: " + bookingCode + ". Expired at: " + feedback.getTokenExpiresAt())
                    .build());
            throw new BusinessRuleException("Feedback link has expired (only valid for 30 days)");
        }

        // Check if feedback is already submitted
        if (feedback.getSubmittedAt() != null) {
            throw new BusinessRuleException("Feedback for this request has already been submitted");
        }

        String bookingCode = feedback.getBooking() != null ? feedback.getBooking().getBookingCode() : "N/A";
        String customerName = feedback.getCustomer() != null ? feedback.getCustomer().getFullName() : "Guest";
        String staffName = feedback.getSalesStaff() != null ? feedback.getSalesStaff().getFullName() : "Staff";
        String staffAvatar = feedback.getSalesStaff() != null ? feedback.getSalesStaff().getAvatarUrl() : null;
        java.time.LocalDate checkOut = feedback.getBooking() != null ? feedback.getBooking().getCheckOutDate() : null;

        return FeedbackTokenValidationResponse.builder()
                .valid(true)
                .bookingCode(bookingCode)
                .customerName(customerName)
                .hotelName("Leadora Hotel & Resort")
                .checkOutDate(checkOut)
                .salesStaffName(staffName)
                .salesStaffAvatar(staffAvatar)
                .expiresAt(feedback.getTokenExpiresAt())
                .build();
    }
}
