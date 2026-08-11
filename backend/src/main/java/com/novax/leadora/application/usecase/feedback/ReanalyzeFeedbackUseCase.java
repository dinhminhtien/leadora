package com.novax.leadora.application.usecase.feedback;

import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.common.exception.BusinessRuleException;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.integration.ai.AbsaEngineClient;
import com.novax.leadora.infrastructure.integration.ai.AbsaEngineClient.SentimentResult;
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

import java.util.Map;
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

        String comment = feedback.getComment();
        if (comment == null || comment.trim().isEmpty()) {
            throw new BusinessRuleException("Cannot analyze feedback with an empty comment");
        }

        try {
            feedback.setAbsaStatus("PROCESSING");
            salesFeedbackRepository.saveAndFlush(feedback);

            Map<String, SentimentResult> results = absaEngineClient.analyze(comment);

            feedback.setAbsaAttitudeSentiment(results.get("attitude").getSentiment());
            feedback.setAbsaAttitudeConfidence(results.get("attitude").getConfidence());

            feedback.setAbsaSpeedSentiment(results.get("speed").getSentiment());
            feedback.setAbsaSpeedConfidence(results.get("speed").getConfidence());

            feedback.setAbsaAccuracySentiment(results.get("accuracy").getSentiment());
            feedback.setAbsaAccuracyConfidence(results.get("accuracy").getConfidence());

            feedback.setAbsaFacilitySentiment(results.get("facility").getSentiment());
            feedback.setAbsaFacilityConfidence(results.get("facility").getConfidence());

            feedback.setAbsaPriceSentiment(results.get("price").getSentiment());
            feedback.setAbsaPriceConfidence(results.get("price").getConfidence());

            feedback.setAbsaStatus("SUCCESS");
            
            SalesFeedbackEntity saved = salesFeedbackRepository.save(feedback);

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

        } catch (Exception e) {
            log.error("Manual ABSA re-analysis failed for feedback {}: {}", feedbackId, e.getMessage(), e);
            feedback.setAbsaStatus("FAILED");
            salesFeedbackRepository.save(feedback);
            throw new BusinessRuleException("AI analysis failed: " + e.getMessage());
        }
    }
}
