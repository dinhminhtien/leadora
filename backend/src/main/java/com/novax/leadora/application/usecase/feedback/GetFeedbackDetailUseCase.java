package com.novax.leadora.application.usecase.feedback;

import com.novax.leadora.api.dto.response.FeedbackResponse;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.SalesFeedbackEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.repository.SalesFeedbackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetFeedbackDetailUseCase {

    private final SalesFeedbackRepository salesFeedbackRepository;
    private final CurrentUserProvider currentUserProvider;

    @Transactional(readOnly = true)
    public FeedbackResponse execute(UUID feedbackId, String headerUserId) {
        UserEntity actor = currentUserProvider.resolve(headerUserId);
        String roleName = actor.getRole() != null ? actor.getRole().getRoleName().trim().toUpperCase() : "";

        SalesFeedbackEntity entity = salesFeedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new ResourceNotFoundException("Feedback", feedbackId));

        // Enforce data boundary: SALES can only view their own assigned feedback
        if (("SALES".equalsIgnoreCase(roleName) || "SALES_STAFF".equalsIgnoreCase(roleName)) &&
                (entity.getSalesStaff() == null || !entity.getSalesStaff().getUserId().equals(actor.getUserId()))) {
            throw new AccessDeniedException("You do not have permission to access this feedback");
        }

        String customerName = entity.getCustomer() != null ? entity.getCustomer().getFullName() : "N/A";
        String bookingCode = entity.getBooking() != null ? entity.getBooking().getBookingCode() : "N/A";
        String salesStaffName = entity.getSalesStaff() != null ? entity.getSalesStaff().getFullName() : "N/A";
        String reviewedByName = entity.getReviewedBy() != null ? entity.getReviewedBy().getFullName() : null;

        return FeedbackResponse.builder()
                .feedbackId(entity.getFeedbackId())
                .customerName(customerName)
                .bookingCode(bookingCode)
                .salesStaffName(salesStaffName)
                .rating(entity.getRating())
                .comment(entity.getComment())
                .reviewStatus(entity.getReviewStatus())
                .submittedAt(entity.getSubmittedAt())
                .reviewedByName(reviewedByName)
                .reviewedAt(entity.getReviewedAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
