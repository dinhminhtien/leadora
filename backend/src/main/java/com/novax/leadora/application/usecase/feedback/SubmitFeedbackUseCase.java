package com.novax.leadora.application.usecase.feedback;

import com.novax.leadora.api.dto.request.SubmitFeedbackRequest;
import com.novax.leadora.api.dto.response.SubmitFeedbackResponse;
import com.novax.leadora.common.exception.BusinessRuleException;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.SalesFeedbackEntity;
import com.novax.leadora.infrastructure.persistence.repository.SalesFeedbackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class SubmitFeedbackUseCase {

    private final SalesFeedbackRepository salesFeedbackRepository;

    @Transactional
    public SubmitFeedbackResponse execute(String token, SubmitFeedbackRequest request) {
        SalesFeedbackEntity feedback = salesFeedbackRepository.findByFeedbackToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Feedback invitation", token));

        // Use constant-time comparison to prevent timing attacks
        byte[] tokenBytes = token.getBytes(StandardCharsets.UTF_8);
        byte[] dbTokenBytes = feedback.getFeedbackToken().getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(tokenBytes, dbTokenBytes)) {
            throw new BusinessRuleException("Invalid feedback token");
        }

        // Check expiration
        if (feedback.getTokenExpiresAt() != null && feedback.getTokenExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new BusinessRuleException("Feedback link has expired");
        }

        // Check if already used
        if (feedback.getSubmittedAt() != null) {
            throw new BusinessRuleException("Feedback has already been submitted");
        }

        // XSS sanitization
        String sanitizedComment = request.getComment() != null ? HtmlUtils.htmlEscape(request.getComment().trim()) : null;

        feedback.setRating(request.getRating());
        feedback.setComment(sanitizedComment);
        feedback.setSubmittedAt(OffsetDateTime.now());

        // Token is consumed, optionally nullify token to release DB space / fully deactivate token
        // Keep the token in DB for tracking, but marked as used by submittedAt != null.
        salesFeedbackRepository.save(feedback);

        return SubmitFeedbackResponse.builder()
                .success(true)
                .message("Thank you for submitting your feedback.")
                .build();
    }
}
