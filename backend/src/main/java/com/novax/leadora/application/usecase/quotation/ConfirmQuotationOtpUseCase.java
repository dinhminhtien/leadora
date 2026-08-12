package com.novax.leadora.application.usecase.quotation;

import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.application.usecase.contract.GenerateContractUseCase;
import com.novax.leadora.infrastructure.persistence.entity.ContractEntity;
import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import com.novax.leadora.infrastructure.persistence.entity.QuotationOtpAuditEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ContractStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus;
import com.novax.leadora.infrastructure.persistence.repository.ContractRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationOtpAuditRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Confirms OTP for quotation acceptance.
 * Delegates actual quotation state transition and logging to CustomerAcceptQuotationUseCase,
 * thereby keeping OTP audit logs and customer acceptance logs aligned.
 *
 * <p><strong>BR-14 compliance</strong>: Auto-conversion to Booking is removed.
 * Transitioning quotation status to {@code RESERVATION_PENDING} is the only outcome.
 * Converting the quotation to a booking must be performed explicitly by Reservation
 * staff once room availability is verified.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConfirmQuotationOtpUseCase {

    private final QuotationRepository quotationRepository;
    private final GetQuotationByTokenUseCase getQuotationByTokenUseCase;
    private final StringRedisTemplate redisTemplate;
    private final QuotationOtpAuditRepository otpAuditRepository;
    private final CustomerAcceptQuotationUseCase customerAcceptQuotationUseCase;
    private final GenerateContractUseCase generateContractUseCase;
    private final ContractRepository contractRepository;

    private static final String OTP_REDIS_PREFIX = "quotation_otp:";
    private static final String FAIL_REDIS_PREFIX = "quotation_otp_fail:";
    private static final int MAX_ATTEMPTS = 5;

    @Transactional
    public QuotationEntity execute(UUID quotationId, String token, String otpInput, String ipAddress, String userAgent) {
        log.info("Confirming OTP for quotation id: {}, IP: {}", quotationId, ipAddress);

        // 1. Validate secure link token (checks usedAt is null)
        getQuotationByTokenUseCase.validateToken(quotationId, token);

        QuotationEntity quotation = quotationRepository.findById(quotationId)
                .orElseThrow(() -> new BusinessException("QUOTATION_NOT_FOUND", "Quotation not found", HttpStatus.NOT_FOUND));

        String previousStatus = quotation.getStatus().name();

        // 2. Guard: must be in PENDING_CUSTOMER_RESPONSE status
        if (quotation.getStatus() != QuotationStatus.PENDING_CUSTOMER_RESPONSE) {
            throw new BusinessException("INVALID_QUOTATION_STATUS",
                    "Quotation cannot be confirmed in its current status: " + quotation.getStatus(), HttpStatus.BAD_REQUEST);
        }

        String otpKey = OTP_REDIS_PREFIX + quotationId.toString();
        String failKey = FAIL_REDIS_PREFIX + quotationId.toString();

        String registeredEmail = quotation.getCustomer() != null ? quotation.getCustomer().getEmail() : null;

        // 3. Retrieve OTP from Redis
        String cachedOtp = redisTemplate.opsForValue().get(otpKey);
        if (cachedOtp == null) {
            // Record failed attempt
            recordAuditLog(quotationId, registeredEmail, ipAddress, false, previousStatus, previousStatus);
            throw new BusinessException("OTP_EXPIRED", "The verification code has expired or was not requested. Please request a new code.", HttpStatus.BAD_REQUEST);
        }

        // 4. Validate OTP and handle failure counts
        if (!cachedOtp.equals(otpInput)) {
            Long currentFailures = redisTemplate.opsForValue().increment(failKey);
            redisTemplate.expire(failKey, 15, TimeUnit.MINUTES);

            // Record failed attempt
            recordAuditLog(quotationId, registeredEmail, ipAddress, false, previousStatus, previousStatus);

            if (currentFailures != null && currentFailures >= MAX_ATTEMPTS) {
                // Invalidate the OTP
                redisTemplate.delete(otpKey);
                redisTemplate.delete(failKey);
                log.warn("OTP locked for quotation {} due to {} failed attempts", quotationId, currentFailures);
                throw new BusinessException("OTP_LOCKED", "Too many incorrect attempts. This code is now invalid. Please request a new verification code.", HttpStatus.BAD_REQUEST);
            }

            int attemptsLeft = MAX_ATTEMPTS - (currentFailures != null ? currentFailures.intValue() : 0);
            throw new BusinessException("INVALID_OTP", "The verification code is incorrect. Attempts remaining: " + attemptsLeft, HttpStatus.BAD_REQUEST);
        }

        // 5. Successful validation -> Accept quotation via delegating use case
        quotation = customerAcceptQuotationUseCase.execute(quotationId, ipAddress, userAgent);

        // Generate Contract in DRAFT (customer accepted quotation via OTP)
        List<ContractEntity> existingContracts = contractRepository.findByQuotation_QuotationId(quotationId);
        if (existingContracts.isEmpty()) {
            generateContractUseCase.execute(quotation, quotation.getCreatedBy());
        }

        // Record successful attempt in OTP audit logs
        recordAuditLog(quotationId, registeredEmail, ipAddress, true, previousStatus, QuotationStatus.RESERVATION_PENDING.name());

        // Clean up Redis
        redisTemplate.delete(otpKey);
        redisTemplate.delete(failKey);

        log.info("Quotation {} successfully transitioned to RESERVATION_PENDING by OTP validation.", quotationId);

        return quotation;
    }

    private void recordAuditLog(UUID quotationId, String email, String ip, boolean verified, String prevStatus, String newStatus) {
        try {
            QuotationOtpAuditEntity audit = QuotationOtpAuditEntity.builder()
                    .quotationId(quotationId)
                    .customerEmail(email)
                    .ipAddress(ip)
                    .otpVerified(verified)
                    .confirmedAt(OffsetDateTime.now())
                    .previousStatus(prevStatus)
                    .newStatus(newStatus)
                    .build();
            otpAuditRepository.save(audit);
        } catch (Exception e) {
            log.error("Failed to record Quotation OTP audit log: {}", e.getMessage(), e);
        }
    }
}
