package com.novax.leadora.application.usecase.feedback;

import com.novax.leadora.api.dto.request.UpdateReviewStatusRequest;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.common.exception.BusinessRuleException;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.SalesFeedbackEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ReviewStatus;
import com.novax.leadora.infrastructure.persistence.repository.SalesFeedbackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UpdateFeedbackReviewStatusUseCase {

    private final SalesFeedbackRepository salesFeedbackRepository;
    private final CurrentUserProvider currentUserProvider;

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

        salesFeedbackRepository.save(entity);
    }
}
