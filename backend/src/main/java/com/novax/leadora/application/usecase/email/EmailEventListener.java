package com.novax.leadora.application.usecase.email;

import com.novax.leadora.application.usecase.email.event.BookingConfirmedEvent;
import com.novax.leadora.application.usecase.email.event.FeedbackInvitationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailEventListener {

    private final EmailService emailService;

    @Async("taskExecutor")
    @EventListener
    public void handleBookingConfirmed(BookingConfirmedEvent event) {
        log.info("Asynchronously processing booking confirmation email for booking: {}", event.booking().getBookingCode());
        try {
            emailService.sendBookingConfirmationEmail(event.booking(), event.details());
        } catch (Exception e) {
            log.error("Failed to send booking confirmation email asynchronously for booking: {}", event.booking().getBookingCode(), e);
        }
    }

    @Async("taskExecutor")
    @EventListener
    public void handleFeedbackInvitation(FeedbackInvitationEvent event) {
        log.info("Asynchronously processing feedback invitation email for email: {}", event.email());
        try {
            emailService.sendFeedbackInvitationEmail(event.email(), event.customerName(), event.feedbackLink());
        } catch (Exception e) {
            log.error("Failed to send feedback invitation email asynchronously for email: {}", event.email(), e);
        }
    }
}
