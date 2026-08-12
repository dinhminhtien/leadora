package com.novax.leadora.application.usecase.quotation;

import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.config.QuotationProperties;
import com.novax.leadora.application.event.QuotationOtpRequestedEvent;
import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus;
import com.novax.leadora.infrastructure.persistence.repository.QuotationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class RequestQuotationOtpUseCase {

    private final QuotationRepository quotationRepository;
    private final GetQuotationByTokenUseCase getQuotationByTokenUseCase;
    private final StringRedisTemplate redisTemplate;
    private final QuotationProperties quotationProperties;
    private final ApplicationEventPublisher eventPublisher;

    private static final SecureRandom secureRandom = new SecureRandom();
    private static final String OTP_REDIS_PREFIX = "quotation_otp:";

    @Transactional(readOnly = true)
    public void execute(UUID quotationId, String token) {
        log.info("Requesting OTP for quotation id: {}", quotationId);

        // 1. Validate secure link token
        getQuotationByTokenUseCase.validateToken(quotationId, token);

        QuotationEntity quotation = quotationRepository.findById(quotationId)
                .orElseThrow(() -> new BusinessException("QUOTATION_NOT_FOUND", "Quotation not found", HttpStatus.NOT_FOUND));

        // 2. Guard: must be in PENDING_CUSTOMER_RESPONSE status
        if (quotation.getStatus() != QuotationStatus.PENDING_CUSTOMER_RESPONSE) {
            throw new BusinessException("INVALID_QUOTATION_STATUS",
                    "Quotation cannot request OTP in its current status: " + quotation.getStatus(), HttpStatus.BAD_REQUEST);
        }

        String recipientEmail = quotation.getCustomer() != null ? quotation.getCustomer().getEmail() : null;
        if (recipientEmail == null || recipientEmail.isBlank()) {
            throw new BusinessException("CUSTOMER_EMAIL_MISSING",
                    "Cannot request OTP because customer registered email is missing.", HttpStatus.BAD_REQUEST);
        }

        // 3. Generate 6-digit OTP
        int code = 100000 + secureRandom.nextInt(900000);
        String otpCode = String.valueOf(code);

        // 4. Save to Redis
        String key = OTP_REDIS_PREFIX + quotationId.toString();
        int expirySeconds = quotationProperties.getOtpExpirySeconds();
        try {
            redisTemplate.opsForValue().set(key, otpCode, expirySeconds, TimeUnit.SECONDS);
            log.info("OTP generated and saved in Redis for quotation {}. TTL: {} seconds", quotationId, expirySeconds);
        } catch (Exception e) {
            log.error("Failed to connect to Redis for quotation OTP generation: {}", e.getMessage(), e);
            throw new BusinessException("REDIS_CONNECTION_FAILED",
                    "Unable to issue OTP. Please try again later.", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        // 5. Publish event to send OTP email
        eventPublisher.publishEvent(new QuotationOtpRequestedEvent(quotation, otpCode, recipientEmail));
    }
}
