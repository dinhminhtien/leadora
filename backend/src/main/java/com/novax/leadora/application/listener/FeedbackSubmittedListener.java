package com.novax.leadora.application.listener;

import com.novax.leadora.application.event.FeedbackSubmittedEvent;
import com.novax.leadora.infrastructure.integration.ai.AbsaEngineClient;
import com.novax.leadora.infrastructure.integration.ai.AbsaEngineClient.SentimentResult;
import com.novax.leadora.infrastructure.persistence.entity.SalesFeedbackEntity;
import com.novax.leadora.infrastructure.persistence.repository.SalesFeedbackRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

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
        
        SalesFeedbackEntity feedback = salesFeedbackRepository.findById(event.getFeedbackId())
                .orElse(null);
                
        if (feedback == null) {
            log.error("Feedback with ID {} not found for ABSA analysis", event.getFeedbackId());
            return;
        }
        
        String comment = feedback.getComment();
        if (comment == null || comment.trim().isEmpty()) {
            log.info("Feedback comment is empty, skipping ABSA analysis for feedback ID: {}", event.getFeedbackId());
            feedback.setAbsaStatus("SKIPPED");
            salesFeedbackRepository.save(feedback);
            return;
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
            log.info("Successfully completed ABSA sentiment analysis for feedback ID: {}", event.getFeedbackId());
            
        } catch (Exception e) {
            log.error("Error analyzing feedback comment for ID {}: {}", event.getFeedbackId(), e.getMessage(), e);
            feedback.setAbsaStatus("FAILED");
        }
        
        salesFeedbackRepository.save(feedback);
    }
}
