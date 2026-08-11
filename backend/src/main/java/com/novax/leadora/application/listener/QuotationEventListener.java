package com.novax.leadora.application.listener;

import com.novax.leadora.application.event.QuotationOtpRequestedEvent;
import com.novax.leadora.application.usecase.quotation.QuotationOtpEmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class QuotationEventListener {

    private final QuotationOtpEmailService quotationOtpEmailService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleQuotationOtpRequested(QuotationOtpRequestedEvent event) {
        log.info("QuotationOtpRequestedEvent received. Sending OTP email to {} for quotation {}", 
                event.recipientEmail(), event.quotation().getQuotationId());
        try {
            quotationOtpEmailService.sendOtpEmail(event.quotation(), event.otpCode(), event.recipientEmail());
        } catch (Exception e) {
            log.error("Failed to send quotation OTP email inside listener: {}", e.getMessage(), e);
        }
    }
}
