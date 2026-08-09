package com.novax.leadora.application.usecase.email;

import com.novax.leadora.application.usecase.email.event.BookingConfirmedEvent;
import com.novax.leadora.application.usecase.email.event.FeedbackInvitationEvent;
import com.novax.leadora.application.usecase.quotation.event.QuotationAcceptedEvent;
import com.novax.leadora.application.usecase.quotation.event.QuotationRejectedEvent;
import com.novax.leadora.application.usecase.handover.event.HandoverSubmittedEvent;
import com.novax.leadora.infrastructure.persistence.entity.BookingEntity;
import com.novax.leadora.infrastructure.persistence.entity.OpHandoverEntity;
import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.repository.BookingRepository;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailEventListener {

    private final EmailService emailService;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;

    @Value("${app.frontend-url}")
    private String frontendUrl;

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

    @Async("taskExecutor")
    @EventListener
    public void handleQuotationAccepted(QuotationAcceptedEvent event) {
        QuotationEntity quotation = event.getQuotation();
        log.info("Asynchronously processing quotation accepted email for quotation: {}", quotation.getQuotationId());
        try {
            List<BookingEntity> bookings = bookingRepository.findByQuotation_QuotationId(quotation.getQuotationId());
            String bookingCode = bookings.isEmpty() ? "PENDING" : bookings.get(0).getBookingCode();
            String quoteNo = "QT-" + quotation.getQuotationId().toString().substring(0, 8).toUpperCase();

            // 1. Notify Customer
            if (quotation.getCustomer() != null && quotation.getCustomer().getEmail() != null) {
                emailService.sendQuotationAcceptedEmailToCustomer(
                        quotation.getCustomer().getEmail(),
                        quotation.getCustomer().getFullName(),
                        quoteNo,
                        bookingCode
                );
            }

            // 2. Notify Sales Rep
            if (quotation.getCreatedBy() != null && quotation.getCreatedBy().getEmail() != null) {
                emailService.sendQuotationAcceptedEmailToSalesRep(
                        quotation.getCreatedBy().getEmail(),
                        quotation.getCreatedBy().getFullName(),
                        quoteNo,
                        bookingCode
                );
            }
        } catch (Exception e) {
            log.error("Failed to process quotation accepted emails for quotation: {}", quotation.getQuotationId(), e);
        }
    }

    @Async("taskExecutor")
    @EventListener
    public void handleQuotationRejected(QuotationRejectedEvent event) {
        QuotationEntity quotation = event.getQuotation();
        log.info("Asynchronously processing quotation rejected email for quotation: {}", quotation.getQuotationId());
        try {
            String quoteNo = "QT-" + quotation.getQuotationId().toString().substring(0, 8).toUpperCase();

            // Notify Sales Rep
            if (quotation.getCreatedBy() != null && quotation.getCreatedBy().getEmail() != null) {
                emailService.sendQuotationRejectedEmailToSalesRep(
                        quotation.getCreatedBy().getEmail(),
                        quotation.getCreatedBy().getFullName(),
                        quoteNo,
                        event.getReason()
                );
            }
        } catch (Exception e) {
            log.error("Failed to process quotation rejected email for quotation: {}", quotation.getQuotationId(), e);
        }
    }

    @Async("taskExecutor")
    @EventListener
    public void handleHandoverSubmitted(HandoverSubmittedEvent event) {
        OpHandoverEntity handover = event.getHandover();
        log.info("Asynchronously processing operational handover email for handover: {}", handover.getHandoverId());
        try {
            if (handover.getAssignedFoUserId() != null) {
                UserEntity foUser = userRepository.findById(handover.getAssignedFoUserId()).orElse(null);
                if (foUser != null && foUser.getEmail() != null) {
                    String handoverCode = "HO-" + handover.getHandoverId().toString().substring(0, 8).toUpperCase();
                    String bookingCode = handover.getBooking() != null ? handover.getBooking().getBookingCode() : "—";
                    String customerName = (handover.getBooking() != null && handover.getBooking().getCustomer() != null) 
                            ? handover.getBooking().getCustomer().getFullName() : "—";
                    String salesRep = (handover.getBooking() != null && handover.getBooking().getAssignedUser() != null)
                            ? handover.getBooking().getAssignedUser().getFullName() : "—";
                    String handoverLink = frontendUrl + "/operations/handovers/" + handover.getHandoverId();

                    emailService.sendHandoverNotificationEmail(
                            foUser.getEmail(),
                            handoverCode,
                            bookingCode,
                            customerName,
                            salesRep,
                            handoverLink
                    );
                }
            }
        } catch (Exception e) {
            log.error("Failed to send handover notification email for handover: {}", handover.getHandoverId(), e);
        }
    }
}
