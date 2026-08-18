package com.novax.leadora.application.usecase.feedback;

import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.common.exception.BusinessRuleException;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.integration.ai.AbsaEngineClient;
import com.novax.leadora.api.dto.response.AbsaResponseDto;
import com.novax.leadora.infrastructure.persistence.entity.SalesFeedbackEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.repository.SalesFeedbackRepository;
import com.novax.leadora.infrastructure.persistence.entity.enums.ActivityLogType;
import com.novax.leadora.infrastructure.persistence.entity.enums.EntityType;
import com.novax.leadora.application.usecase.activitylog.ActivityLogPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReanalyzeFeedbackUseCase {

    private final SalesFeedbackRepository salesFeedbackRepository;
    private final CurrentUserProvider currentUserProvider;
    private final AbsaEngineClient absaEngineClient;
    private final ActivityLogPublisher activityLogPublisher;
    private final ObjectMapper objectMapper;

    @Transactional
    public void execute(UUID feedbackId, String headerUserId) {
        UserEntity actor = currentUserProvider.resolve(headerUserId);
        String roleName = actor.getRole() != null ? actor.getRole().getRoleName().trim().toUpperCase() : "";

        // Enforce role authorization: only MANAGER or ADMIN can trigger manual ABSA re-analysis
        if (!"MANAGER".equalsIgnoreCase(roleName) && !"ADMIN".equalsIgnoreCase(roleName)) {
            throw new AccessDeniedException("Only Managers or Administrators can trigger feedback sentiment re-analysis");
        }

        SalesFeedbackEntity feedback = salesFeedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new ResourceNotFoundException("Feedback", feedbackId));

        String rawComment = feedback.getComment();
        if (rawComment == null || rawComment.trim().isEmpty()) {
            throw new BusinessRuleException("Cannot analyze feedback with an empty comment");
        }
        String comment = org.springframework.web.util.HtmlUtils.htmlUnescape(rawComment);

        try {
            feedback.setAbsaStatus("PROCESSING");
            salesFeedbackRepository.saveAndFlush(feedback);

            AbsaResponseDto results = absaEngineClient.analyze(comment);

            int lockAttempt = 0;
            int maxLockRetries = 3;
            while (lockAttempt < maxLockRetries) {
                try {
                    SalesFeedbackEntity currentFeedback = salesFeedbackRepository.findById(feedbackId)
                            .orElseThrow(() -> new ResourceNotFoundException("Feedback", feedbackId));

                    currentFeedback.setAbsaAttitudeSentiment(results.attitude() != null ? results.attitude().sentiment() : null);
                    currentFeedback.setAbsaAttitudeConfidence(results.attitude() != null ? results.attitude().confidence() : null);

                    currentFeedback.setAbsaSpeedSentiment(results.speed() != null ? results.speed().sentiment() : null);
                    currentFeedback.setAbsaSpeedConfidence(results.speed() != null ? results.speed().confidence() : null);

                    currentFeedback.setAbsaAccuracySentiment(results.accuracy() != null ? results.accuracy().sentiment() : null);
                    currentFeedback.setAbsaAccuracyConfidence(results.accuracy() != null ? results.accuracy().confidence() : null);

                    currentFeedback.setAbsaFacilitySentiment(results.facility() != null ? results.facility().sentiment() : null);
                    currentFeedback.setAbsaFacilityConfidence(results.facility() != null ? results.facility().confidence() : null);

                    currentFeedback.setAbsaPriceSentiment(results.price() != null ? results.price().sentiment() : null);
                    currentFeedback.setAbsaPriceConfidence(results.price() != null ? results.price().confidence() : null);

                    currentFeedback.setComment(comment);
                    currentFeedback.setAbsaStatus("SUCCESS");

                    SalesFeedbackEntity saved = salesFeedbackRepository.save(currentFeedback);

                    // Log activity history for BR-37 audit trail
                    ObjectNode payload = objectMapper.createObjectNode()
                            .put("action", "RE_ANALYZE")
                            .put("triggeredBy", actor.getUserId().toString());
                    
                    activityLogPublisher.publish(
                            ActivityLogType.FEEDBACK_REVIEW_STATUS_UPDATED,
                            EntityType.FEEDBACK,
                            saved.getFeedbackId(),
                            "Manual ABSA analysis re-triggered by " + actor.getFullName(),
                            payload
                    );
                    break;
                } catch (org.springframework.orm.ObjectOptimisticLockingFailureException ex) {
                    lockAttempt++;
                    if (lockAttempt >= maxLockRetries) {
                        throw ex;
                    }
                    try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                }
            }

        } catch (Exception e) {
            log.error("Manual ABSA re-analysis failed for feedback {}: {}", feedbackId, e.getMessage(), e);
            // Fallback status FAILED with safety lock
            int attempt = 0;
            while (attempt < 3) {
                try {
                    SalesFeedbackEntity failFeedback = salesFeedbackRepository.findById(feedbackId)
                            .orElseThrow(() -> new ResourceNotFoundException("Feedback", feedbackId));
                    failFeedback.setAbsaStatus("FAILED");
                    salesFeedbackRepository.save(failFeedback);
                    break;
                } catch (org.springframework.orm.ObjectOptimisticLockingFailureException ex) {
                    attempt++;
                    try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                }
            }
            throw new BusinessRuleException("AI analysis failed: " + e.getMessage());
        }
    }
}
