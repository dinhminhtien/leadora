package com.novax.leadora.unit.feedback;

import com.novax.leadora.api.dto.request.SubmitFeedbackRequest;
import com.novax.leadora.api.dto.response.SubmitFeedbackResponse;
import com.novax.leadora.application.usecase.feedback.SubmitFeedbackUseCase;
import com.novax.leadora.common.exception.BusinessRuleException;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.SalesFeedbackEntity;
import com.novax.leadora.infrastructure.persistence.repository.SalesFeedbackRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubmitFeedbackUseCaseTest {

    @Mock
    private SalesFeedbackRepository salesFeedbackRepository;

    @InjectMocks
    private SubmitFeedbackUseCase submitFeedbackUseCase;

    @Test
    @DisplayName("UT-FEEDBACK-01: Submit feedback successfully with detailed ratings")
    void testSubmitFeedbackSuccess() {
        String token = "valid-token";
        SalesFeedbackEntity feedback = SalesFeedbackEntity.builder()
                .feedbackId(UUID.randomUUID())
                .feedbackToken(token)
                .tokenExpiresAt(OffsetDateTime.now().plusDays(1))
                .build();

        SubmitFeedbackRequest request = new SubmitFeedbackRequest();
        request.setRating((short) 4);
        request.setRatingAttitude((short) 5);
        request.setRatingSpeed((short) 3);
        request.setRatingAccuracy((short) 4);
        request.setComment("Good support!");

        when(salesFeedbackRepository.findByFeedbackToken(token)).thenReturn(Optional.of(feedback));
        when(salesFeedbackRepository.save(any(SalesFeedbackEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SubmitFeedbackResponse response = submitFeedbackUseCase.execute(token, request);

        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals("Thank you for submitting your feedback.", response.getMessage());

        assertEquals((short) 4, feedback.getRating());
        assertEquals((short) 5, feedback.getRatingAttitude());
        assertEquals((short) 3, feedback.getRatingSpeed());
        assertEquals((short) 4, feedback.getRatingAccuracy());
        assertEquals("Good support!", feedback.getComment());
        assertNotNull(feedback.getSubmittedAt());

        verify(salesFeedbackRepository, times(1)).save(feedback);
    }

    @Test
    @DisplayName("UT-FEEDBACK-02: Submit feedback with non-existent token throws ResourceNotFoundException")
    void testSubmitFeedbackNonExistentToken() {
        String token = "non-existent";
        SubmitFeedbackRequest request = new SubmitFeedbackRequest();

        when(salesFeedbackRepository.findByFeedbackToken(token)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> submitFeedbackUseCase.execute(token, request));
        verify(salesFeedbackRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT-FEEDBACK-03: Submit feedback with expired token throws BusinessRuleException")
    void testSubmitFeedbackExpiredToken() {
        String token = "expired-token";
        SalesFeedbackEntity feedback = SalesFeedbackEntity.builder()
                .feedbackId(UUID.randomUUID())
                .feedbackToken(token)
                .tokenExpiresAt(OffsetDateTime.now().minusDays(1))
                .build();

        SubmitFeedbackRequest request = new SubmitFeedbackRequest();

        when(salesFeedbackRepository.findByFeedbackToken(token)).thenReturn(Optional.of(feedback));

        assertThrows(BusinessRuleException.class, () -> submitFeedbackUseCase.execute(token, request));
        verify(salesFeedbackRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT-FEEDBACK-04: Submit feedback that is already submitted throws BusinessRuleException")
    void testSubmitFeedbackAlreadySubmitted() {
        String token = "already-submitted";
        SalesFeedbackEntity feedback = SalesFeedbackEntity.builder()
                .feedbackId(UUID.randomUUID())
                .feedbackToken(token)
                .tokenExpiresAt(OffsetDateTime.now().plusDays(1))
                .submittedAt(OffsetDateTime.now().minusHours(1))
                .build();

        SubmitFeedbackRequest request = new SubmitFeedbackRequest();

        when(salesFeedbackRepository.findByFeedbackToken(token)).thenReturn(Optional.of(feedback));

        assertThrows(BusinessRuleException.class, () -> submitFeedbackUseCase.execute(token, request));
        verify(salesFeedbackRepository, never()).save(any());
    }
}
