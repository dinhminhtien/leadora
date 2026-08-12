package com.novax.leadora.application.usecase.contract;

import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.infrastructure.persistence.entity.ContractConfirmationTokenEntity;
import com.novax.leadora.infrastructure.persistence.entity.ContractEntity;
import com.novax.leadora.infrastructure.persistence.repository.ContractConfirmationTokenRepository;
import com.novax.leadora.infrastructure.persistence.repository.ContractRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GetContractByTokenUseCase {

    private final ContractRepository contractRepository;
    private final ContractConfirmationTokenRepository tokenRepository;

    /**
     * Validates the provided token against the database hash and retrieves the ContractEntity.
     * This endpoint is public and is used by the customer portal.
     */
    @Transactional(readOnly = true)
    public ContractEntity execute(UUID contractId, String token) {
        log.info("Public contract access request for contract id: {}", contractId);

        ContractEntity contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new BusinessException("CONTRACT_NOT_FOUND", "Contract not found", org.springframework.http.HttpStatus.NOT_FOUND));

        // Validate the secure token
        validateToken(contractId, token);

        return contract;
    }

    public void validateToken(UUID contractId, String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException("INVALID_TOKEN", "Secure token is missing.", org.springframework.http.HttpStatus.BAD_REQUEST);
        }

        ContractConfirmationTokenEntity tokenEntity = tokenRepository.findByContractId(contractId)
                .orElseThrow(() -> new BusinessException("INVALID_TOKEN", "No active verification session found for this contract. Please request a new link from your sales representative.", org.springframework.http.HttpStatus.BAD_REQUEST));

        if (tokenEntity.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new BusinessException("TOKEN_EXPIRED", "The secure link has expired. Please contact your sales representative.", org.springframework.http.HttpStatus.BAD_REQUEST);
        }

        String inputHash = hashSha256(token);
        if (!inputHash.equals(tokenEntity.getTokenHash())) {
            throw new BusinessException("INVALID_TOKEN", "The secure link token is invalid.", org.springframework.http.HttpStatus.BAD_REQUEST);
        }
    }

    private String hashSha256(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 hashing not available", e);
        }
    }
}
