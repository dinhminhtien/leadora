package com.novax.leadora.application.usecase.quotation;

import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.infrastructure.persistence.entity.QuotationConfirmationTokenEntity;
import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import com.novax.leadora.infrastructure.persistence.repository.QuotationConfirmationTokenRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GetQuotationByTokenUseCase {

    private final QuotationRepository quotationRepository;
    private final QuotationConfirmationTokenRepository tokenRepository;

    @Transactional
    public QuotationEntity execute(UUID quotationId, String token) {
        log.info("Public quotation access request for quotation id: {}", quotationId);

        QuotationEntity quotation = quotationRepository.findById(quotationId)
                .orElseThrow(() -> new BusinessException("QUOTATION_NOT_FOUND", "Quotation not found", HttpStatus.NOT_FOUND));

        QuotationConfirmationTokenEntity tokenEntity = validateTokenAndGet(quotationId, token);

        if (tokenEntity.getOpenedAt() == null) {
            tokenEntity.setOpenedAt(OffsetDateTime.now());
            tokenRepository.save(tokenEntity);
        }

        return quotation;
    }

    public void validateToken(UUID quotationId, String token) {
        validateTokenAndGet(quotationId, token);
    }

    public QuotationConfirmationTokenEntity validateTokenAndGet(UUID quotationId, String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException("INVALID_TOKEN", "Secure token is missing.", HttpStatus.BAD_REQUEST);
        }

        QuotationConfirmationTokenEntity tokenEntity = tokenRepository.findByQuotationId(quotationId)
                .orElseThrow(() -> new BusinessException("INVALID_TOKEN", "No active verification session found for this quotation. Please request a new link from your sales representative.", HttpStatus.BAD_REQUEST));

        if (tokenEntity.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new BusinessException("TOKEN_EXPIRED", "The secure link has expired. Please contact your sales representative.", HttpStatus.BAD_REQUEST);
        }

        if (tokenEntity.getUsedAt() != null) {
            throw new BusinessException("TOKEN_ALREADY_USED", "The secure link token has already been used. Please request a new link.", HttpStatus.BAD_REQUEST);
        }

        String inputHash = hashSha256(token);
        if (!inputHash.equals(tokenEntity.getTokenHash())) {
            throw new BusinessException("INVALID_TOKEN", "The secure link token is invalid.", HttpStatus.BAD_REQUEST);
        }

        return tokenEntity;
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
