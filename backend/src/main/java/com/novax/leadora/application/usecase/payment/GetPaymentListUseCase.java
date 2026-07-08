package com.novax.leadora.application.usecase.payment;

import com.novax.leadora.api.dto.response.PaymentResponse;
import com.novax.leadora.infrastructure.persistence.entity.PaymentEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.PaymentStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.PaymentType;
import com.novax.leadora.infrastructure.persistence.repository.PaymentRepository;
import com.novax.leadora.infrastructure.persistence.specification.PaymentSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * UC-21.2 — View Payment List Use Case.
 */
@Service
@RequiredArgsConstructor
public class GetPaymentListUseCase {

    private final PaymentRepository paymentRepository;

    @Transactional(readOnly = true)
    public Page<PaymentResponse> execute(String search, String status, String paymentType,
                                         String sortBy, String sortDir, int page, int size) {
        String sortField = StringUtils.hasText(sortBy) ? sortBy : "createdAt";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortField));

        PaymentStatus statusFilter = parseStatus(status);
        PaymentType typeFilter = parseType(paymentType);

        Specification<PaymentEntity> spec = PaymentSpecification.filterPayments(search, statusFilter, typeFilter);
        Page<PaymentEntity> payments = paymentRepository.findAll(spec, pageable);

        return payments.map(PaymentResponse::from);
    }

    private PaymentStatus parseStatus(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return PaymentStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private PaymentType parseType(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return PaymentType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
