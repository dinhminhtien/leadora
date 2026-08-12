package com.novax.leadora.application.usecase.feedback;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novax.leadora.api.dto.request.UpdateReviewStatusRequest;
import com.novax.leadora.application.usecase.activitylog.ActivityLogPublisher;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.common.exception.BusinessRuleException;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.SalesFeedbackEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ActivityLogType;
import com.novax.leadora.infrastructure.persistence.entity.enums.EntityType;
import com.novax.leadora.infrastructure.persistence.entity.enums.ReviewStatus;
import com.novax.leadora.infrastructure.persistence.repository.SalesFeedbackRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateFeedbackReviewStatusUseCase {

    private final SalesFeedbackRepository salesFeedbackRepository;
    private final CurrentUserProvider currentUserProvider;
    private final ActivityLogPublisher activityLogPublisher;
    private final ObjectMapper objectMapper;

    @Transactional
    public void execute(UUID feedbackId, UpdateReviewStatusRequest request, String headerUserId) {
        UserEntity actor = currentUserProvider.resolve(headerUserId);
        String roleName = actor.getRole() != null ? actor.getRole().getRoleName().trim().toUpperCase() : "";

        // Enforce role authorization: only MANAGER or ADMIN can review feedback
        if (!"MANAGER".equalsIgnoreCase(roleName) && !"ADMIN".equalsIgnoreCase(roleName)) {
            throw new AccessDeniedException("Only Managers or Administrators can update feedback review status");
        }

        SalesFeedbackEntity entity = salesFeedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new ResourceNotFoundException("Feedback", feedbackId));

        ReviewStatus currentStatus = entity.getReviewStatus();
        ReviewStatus targetStatus = request.getReviewStatus();

        if (currentStatus == null) {
            currentStatus = ReviewStatus.PENDING;
        }

        // State Machine Transition Rule: can only transition FROM PENDING
        if (currentStatus != ReviewStatus.PENDING) {
            throw new BusinessRuleException(
                String.format("Cannot modify feedback that is already in a final state '%s'", currentStatus)
            );
        }

        if (targetStatus == ReviewStatus.PENDING) {
            throw new BusinessRuleException("Cannot reset review status back to PENDING");
        }

        entity.setReviewStatus(targetStatus);
        entity.setReviewedBy(actor);
        entity.setReviewedAt(OffsetDateTime.now());

        SalesFeedbackEntity saved = salesFeedbackRepository.save(entity);

        // BR-37 Compliance: Publish to activity log
        try {
            ObjectNode payload = objectMapper.createObjectNode()
                    .put("previousStatus", currentStatus.name())
                    .put("newStatus", targetStatus.name())
                    .put("reviewedByUserId", actor.getUserId().toString());
            
            activityLogPublisher.publish(
                    ActivityLogType.FEEDBACK_REVIEW_STATUS_UPDATED,
                    EntityType.FEEDBACK,
                    saved.getFeedbackId(),
                    "Feedback review status updated from " + currentStatus + " to " + targetStatus,
                    payload
            );
        } catch (Exception e) {
            log.warn("Failed to publish feedback review activity: {}", e.getMessage());
        }
    }
}

