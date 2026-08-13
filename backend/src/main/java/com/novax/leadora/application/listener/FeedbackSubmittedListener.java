package com.novax.leadora.application.listener;

import com.novax.leadora.application.event.FeedbackSubmittedEvent;
import com.novax.leadora.infrastructure.integration.ai.AbsaEngineClient;
import com.novax.leadora.api.dto.response.AbsaResponseDto;
import com.novax.leadora.infrastructure.persistence.entity.SalesFeedbackEntity;
import com.novax.leadora.infrastructure.persistence.repository.SalesFeedbackRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class FeedbackSubmittedListener {

    private final SalesFeedbackRepository salesFeedbackRepository;
    private final AbsaEngineClient absaEngineClient;

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleFeedbackSubmitted(FeedbackSubmittedEvent event) {
        log.info("Starting asynchronous ABSA sentiment analysis for feedback ID: {}", event.getFeedbackId());
        
        int maxLockRetries = 3;
        int lockAttempt = 0;
        AbsaResponseDto results = null;

        SalesFeedbackEntity initialFeedback = salesFeedbackRepository.findById(event.getFeedbackId()).orElse(null);
        if (initialFeedback == null) {
            log.error("Feedback with ID {} not found for ABSA analysis", event.getFeedbackId());
            return;
        }

        String currentStatus = initialFeedback.getAbsaStatus();
        if ("SUCCESS".equalsIgnoreCase(currentStatus) || 
            "PROCESSING".equalsIgnoreCase(currentStatus) || 
            "SKIPPED".equalsIgnoreCase(currentStatus)) {
            log.info("ABSA analysis for feedback ID {} is already processed or in progress (status: {}). Skipping.", 
                     event.getFeedbackId(), currentStatus);
            return;
        }

        String rawComment = initialFeedback.getComment();
        if (rawComment == null || rawComment.trim().isEmpty()) {
            log.info("Feedback comment is empty, skipping ABSA analysis for feedback ID: {}", event.getFeedbackId());
            updateFeedbackStatusWithLock(event.getFeedbackId(), "SKIPPED");
            return;
        }
        String comment = org.springframework.web.util.HtmlUtils.htmlUnescape(rawComment);

        // 2. Call AI ABSA Engine Client
        try {
            updateFeedbackStatusWithLock(event.getFeedbackId(), "PROCESSING");
            results = absaEngineClient.analyze(comment);
        } catch (Exception e) {
            log.error("Error analyzing feedback comment for ID {}: {}", event.getFeedbackId(), e.getMessage(), e);
            updateFeedbackStatusWithLock(event.getFeedbackId(), "FAILED");
            return;
        }

        // 3. Save ABSA results with optimistic locking retry logic
        while (lockAttempt < maxLockRetries) {
            try {
                SalesFeedbackEntity feedback = salesFeedbackRepository.findById(event.getFeedbackId())
                        .orElseThrow(() -> new IllegalStateException("Feedback not found: " + event.getFeedbackId()));

                feedback.setAbsaAttitudeSentiment(results.attitude() != null ? results.attitude().sentiment() : null);
                feedback.setAbsaAttitudeConfidence(results.attitude() != null ? results.attitude().confidence() : null);

                feedback.setAbsaSpeedSentiment(results.speed() != null ? results.speed().sentiment() : null);
                feedback.setAbsaSpeedConfidence(results.speed() != null ? results.speed().confidence() : null);

                feedback.setAbsaAccuracySentiment(results.accuracy() != null ? results.accuracy().sentiment() : null);
                feedback.setAbsaAccuracyConfidence(results.accuracy() != null ? results.accuracy().confidence() : null);

                feedback.setAbsaFacilitySentiment(results.facility() != null ? results.facility().sentiment() : null);
                feedback.setAbsaFacilityConfidence(results.facility() != null ? results.facility().confidence() : null);

                feedback.setAbsaPriceSentiment(results.price() != null ? results.price().sentiment() : null);
                feedback.setAbsaPriceConfidence(results.price() != null ? results.price().confidence() : null);

                feedback.setComment(comment);
                feedback.setAbsaStatus("SUCCESS");

                salesFeedbackRepository.save(feedback);
                log.info("Successfully completed and saved ABSA sentiment analysis for feedback ID: {}", event.getFeedbackId());
                break;
            } catch (ObjectOptimisticLockingFailureException e) {
                lockAttempt++;
                log.warn("Optimistic lock failure during ABSA saving for feedback ID: {}. Retry {}/{}", 
                         event.getFeedbackId(), lockAttempt, maxLockRetries);
                if (lockAttempt >= maxLockRetries) {
                    log.error("Failed to save ABSA results due to persistent optimistic lock failure for ID: {}", event.getFeedbackId());
                    updateFeedbackStatusWithLock(event.getFeedbackId(), "FAILED");
                }
                try { Thread.sleep(200); } catch (InterruptedException ignored) {}
            }
        }
    }

    private void updateFeedbackStatusWithLock(UUID feedbackId, String status) {
        int attempt = 0;
        int maxAttempts = 3;
        while (attempt < maxAttempts) {
            try {
                SalesFeedbackEntity feedback = salesFeedbackRepository.findById(feedbackId)
                        .orElseThrow(() -> new IllegalStateException("Feedback not found"));
                feedback.setAbsaStatus(status);
                salesFeedbackRepository.saveAndFlush(feedback);
                break;
            } catch (ObjectOptimisticLockingFailureException ex) {
                attempt++;
                if (attempt >= maxAttempts) {
                    log.error("Failed to update status to {} due to persistent locking failure", status);
                }
                try { Thread.sleep(200); } catch (InterruptedException ignored) {}
            }
        }
    }
}
