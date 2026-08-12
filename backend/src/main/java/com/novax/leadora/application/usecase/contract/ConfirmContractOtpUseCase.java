package com.novax.leadora.application.usecase.contract;

import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.application.event.ContractAcknowledgedEvent;
import com.novax.leadora.application.usecase.activitylog.ActivityLogPublisher;
import com.novax.leadora.infrastructure.persistence.entity.ContractEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ContractStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.EntityType;
import com.novax.leadora.infrastructure.persistence.entity.enums.ActivityLogType;
import com.novax.leadora.infrastructure.persistence.repository.ContractRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConfirmContractOtpUseCase {

    private final ContractRepository contractRepository;
    private final GetContractByTokenUseCase getContractByTokenUseCase;
    private final StringRedisTemplate redisTemplate;
    private final ActivityLogPublisher activityLogPublisher;
    private final ApplicationEventPublisher eventPublisher;
    private final ActivateContractUseCase activateContractUseCase;

    private static final String OTP_REDIS_PREFIX = "contract_otp:";
    private static final String FAIL_REDIS_PREFIX = "contract_otp_fail:";
    private static final int MAX_ATTEMPTS = 5;

    @Transactional
    public ContractEntity execute(UUID contractId, String token, String otpInput) {
        log.info("Confirming OTP for contract id: {}", contractId);

        // 1. Validate the secure link token
        getContractByTokenUseCase.validateToken(contractId, token);

        ContractEntity contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new BusinessException("CONTRACT_NOT_FOUND", "Contract not found", org.springframework.http.HttpStatus.NOT_FOUND));

        // 2. Guard: must be in SENT status
        if (contract.getStatus() != ContractStatus.SENT) {
            throw new BusinessException("INVALID_CONTRACT_STATUS", 
                    "Contract cannot be confirmed in its current status: " + contract.getStatus(), org.springframework.http.HttpStatus.BAD_REQUEST);
        }

        String otpKey = OTP_REDIS_PREFIX + contractId.toString();
        String failKey = FAIL_REDIS_PREFIX + contractId.toString();

        // 3. Retrieve OTP from Redis
        String cachedOtp = redisTemplate.opsForValue().get(otpKey);
        if (cachedOtp == null) {
            throw new BusinessException("OTP_EXPIRED", "The verification code has expired or was not requested. Please request a new code.", org.springframework.http.HttpStatus.BAD_REQUEST);
        }

        // 4. Validate OTP and handle failure counts
        if (!cachedOtp.equals(otpInput)) {
            Long currentFailures = redisTemplate.opsForValue().increment(failKey);
            redisTemplate.expire(failKey, 15, TimeUnit.MINUTES);
            
            if (currentFailures != null && currentFailures >= MAX_ATTEMPTS) {
                // Lock / invalidate the OTP
                redisTemplate.delete(otpKey);
                redisTemplate.delete(failKey);
                log.warn("OTP locked for contract {} due to {} failed attempts", contract.getContractCode(), currentFailures);
                throw new BusinessException("OTP_LOCKED", "Too many incorrect attempts. This code is now invalid. Please request a new verification code.", org.springframework.http.HttpStatus.BAD_REQUEST);
            }
            
            int attemptsLeft = MAX_ATTEMPTS - (currentFailures != null ? currentFailures.intValue() : 0);
            throw new BusinessException("INVALID_OTP", "The verification code is incorrect. Attempts remaining: " + attemptsLeft, org.springframework.http.HttpStatus.BAD_REQUEST);
        }

        // 5. Successful validation -> Acknowledge contract
        contract.setStatus(ContractStatus.ACKNOWLEDGED);
        contract.setAcknowledgedAt(OffsetDateTime.now());
        contract = contractRepository.save(contract);

        // Activate the contract immediately
        activateContractUseCase.execute(contract.getId());

        // Clean up Redis
        redisTemplate.delete(otpKey);
        redisTemplate.delete(failKey);

        log.info("Contract {} successfully ACKNOWLEDGED by OTP validation.", contract.getContractCode());

        // Publish activity logs
        activityLogPublisher.publish(
                ActivityLogType.CONTRACT_ACKNOWLEDGED,
                EntityType.CONTRACT,
                contract.getId(),
                "Contract " + contract.getContractCode() + " acknowledged by customer",
                null
        );

        // Publish event
        eventPublisher.publishEvent(new ContractAcknowledgedEvent(contract));

        return contract;
    }
}
