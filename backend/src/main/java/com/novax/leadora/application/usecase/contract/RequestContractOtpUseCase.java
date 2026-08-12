package com.novax.leadora.application.usecase.contract;

import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.common.security.OtpStore;
import com.novax.leadora.config.ContractProperties;
import com.novax.leadora.application.event.ContractOtpRequestedEvent;
import com.novax.leadora.infrastructure.persistence.entity.ContractEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ContractStatus;
import com.novax.leadora.infrastructure.persistence.repository.ContractRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RequestContractOtpUseCase {

    private final ContractRepository contractRepository;
    private final GetContractByTokenUseCase getContractByTokenUseCase;
    private final OtpStore otpStore;
    private final ContractProperties contractProperties;
    private final ApplicationEventPublisher eventPublisher;

    private static final SecureRandom secureRandom = new SecureRandom();
    private static final String OTP_KEY_PREFIX = "contract_otp:";

    @Transactional(readOnly = true)
    public void execute(UUID contractId, String token) {
        log.info("Requesting OTP for contract id: {}", contractId);

        // 1. Validate the secure link token
        getContractByTokenUseCase.validateToken(contractId, token);

        ContractEntity contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new BusinessException("CONTRACT_NOT_FOUND", "Contract not found", org.springframework.http.HttpStatus.NOT_FOUND));

        // 2. Guard: must be in SENT status
        if (contract.getStatus() != ContractStatus.SENT) {
            throw new BusinessException("INVALID_CONTRACT_STATUS", 
                    "Contract cannot request OTP in its current status: " + contract.getStatus(), org.springframework.http.HttpStatus.BAD_REQUEST);
        }

        // 3. Generate 6-digit OTP
        int code = 100000 + secureRandom.nextInt(900000);
        String otpCode = String.valueOf(code);

        // 4. Store the code until it expires (Redis when reachable, this process otherwise)
        String key = OTP_KEY_PREFIX + contractId.toString();
        int expirySeconds = contractProperties.getOtpExpirySeconds();
        otpStore.put(key, otpCode, Duration.ofSeconds(expirySeconds));
        log.info("OTP issued for contract {}. TTL: {} seconds", contract.getContractCode(), expirySeconds);

        // 5. Publish event to send OTP email
        eventPublisher.publishEvent(new ContractOtpRequestedEvent(contract, otpCode));
    }
}
