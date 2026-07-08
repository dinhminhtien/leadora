package com.novax.leadora.application.usecase.payment;

import com.novax.leadora.api.dto.response.PaymentResponse;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.PaymentEntity;
import com.novax.leadora.infrastructure.persistence.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * UC-21.3 — View Payment Detail Use Case.
 */
@Service
@RequiredArgsConstructor
public class GetPaymentDetailUseCase {

    private final PaymentRepository paymentRepository;

    @Transactional(readOnly = true)
    public PaymentResponse execute(UUID paymentId) {
        PaymentEntity payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment record not found", paymentId));

        return PaymentResponse.from(payment);
    }
}
