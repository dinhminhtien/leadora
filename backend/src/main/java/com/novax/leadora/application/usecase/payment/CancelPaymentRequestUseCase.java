package com.novax.leadora.application.usecase.payment;

import com.novax.leadora.api.dto.response.PaymentResponse;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.PaymentEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.PaymentStatus;
import com.novax.leadora.infrastructure.persistence.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * UC-21.5 — Cancel Payment Request Use Case.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CancelPaymentRequestUseCase {

    private final PaymentRepository paymentRepository;

    @Transactional
    public PaymentResponse execute(UUID paymentId, UserEntity actor) {
        PaymentEntity payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment record not found", paymentId));

        // Exception E3.1: Payment Already Processed
        if (payment.getStatus() == PaymentStatus.PAID) {
            throw new IllegalStateException("Payment has already been processed");
        }

        // Cancel payment request and invalidate QR Code/Link
        payment.setStatus(PaymentStatus.CANCELLED);
        payment.setQrCodeUrl(null); // Invalidate the associated QR code

        PaymentEntity saved = paymentRepository.save(payment);

        log.info("[AUDIT] Action: CANCEL_PAYMENT_REQUEST, PaymentId: {}, Status: {}, UpdatedBy: {}",
                saved.getPaymentId(), saved.getStatus(), actor != null ? actor.getUserId() : null);

        return PaymentResponse.from(saved);
    }
}
