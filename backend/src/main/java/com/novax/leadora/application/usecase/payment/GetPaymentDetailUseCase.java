package com.novax.leadora.application.usecase.payment;

import com.novax.leadora.api.dto.response.PaymentResponse;
import com.novax.leadora.infrastructure.persistence.entity.PaymentEntity;
import com.novax.leadora.infrastructure.persistence.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * UC-21.3 — View Payment Detail Use Case.
 */
@Service
@RequiredArgsConstructor
public class GetPaymentDetailUseCase {

    private final PaymentRepository paymentRepository;
    private final PaymentAccessPolicy paymentAccessPolicy;

    @Transactional(readOnly = true)
    public PaymentResponse execute(UUID paymentId) {
        PaymentEntity payment = paymentRepository.findByIdWithRelations(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment record not found."));

        // Assert access control policies
        paymentAccessPolicy.assertCanView(paymentAccessPolicy.currentUser(), payment);

        return PaymentResponse.from(payment);
    }
}
