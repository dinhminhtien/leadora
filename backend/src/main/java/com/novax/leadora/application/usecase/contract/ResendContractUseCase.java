package com.novax.leadora.application.usecase.contract;

import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.config.ContractProperties;
import com.novax.leadora.application.event.ContractSentEvent;
import com.novax.leadora.application.usecase.activitylog.ActivityLogPublisher;
import com.novax.leadora.infrastructure.persistence.entity.ContractConfirmationTokenEntity;
import com.novax.leadora.infrastructure.persistence.entity.ContractEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ContractStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.EntityType;
import com.novax.leadora.infrastructure.persistence.entity.enums.ActivityLogType;
import com.novax.leadora.infrastructure.persistence.repository.ContractConfirmationTokenRepository;
import com.novax.leadora.infrastructure.persistence.repository.ContractRepository;
import com.novax.leadora.infrastructure.integration.supabase.SupabaseStorageAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResendContractUseCase {

    private final ContractRepository contractRepository;
    private final ContractConfirmationTokenRepository tokenRepository;
    private final ContractProperties contractProperties;
    private final ContractPdfGenerator pdfGenerator;
    private final SupabaseStorageAdapter storageAdapter;
    private final ActivityLogPublisher activityLogPublisher;
    private final ApplicationEventPublisher eventPublisher;

    private static final SecureRandom secureRandom = new SecureRandom();

    /**
     * Resends the contract email to the customer with a freshly generated secure token.
     * Allowed for contracts in SENT or ACKNOWLEDGED status.
     * A new PDF is regenerated and re-uploaded, and the old token is replaced.
     */
    @Transactional
    public ContractEntity execute(UUID contractId) {
        log.info("Executing ResendContractUseCase for contract id: {}", contractId);

        ContractEntity contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new BusinessException("CONTRACT_NOT_FOUND", "Contract not found with id: " + contractId, org.springframework.http.HttpStatus.NOT_FOUND));

        if (contract.getStatus() != ContractStatus.SENT && contract.getStatus() != ContractStatus.ACKNOWLEDGED) {
            throw new BusinessException("INVALID_CONTRACT_STATUS",
                    "Only SENT or ACKNOWLEDGED contracts can be resent. Current status: " + contract.getStatus(),
                    org.springframework.http.HttpStatus.BAD_REQUEST);
        }

        String recipientEmail = contract.getCustomer().getEmail();
        if (recipientEmail == null || recipientEmail.isBlank()) {
            throw new BusinessException("CUSTOMER_EMAIL_MISSING", "Cannot resend contract because customer has no email address configured.", org.springframework.http.HttpStatus.BAD_REQUEST);
        }

        // 1. Regenerate PDF
        byte[] pdfBytes;
        try {
            pdfBytes = pdfGenerator.generate(contract);
        } catch (Exception e) {
            throw new BusinessException("PDF_GENERATION_FAILED", "Failed to regenerate contract PDF: " + e.getMessage(), org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
        }

        // 2. Re-upload to Supabase Storage (overwrite existing)
        String pdfPath = contract.getId().toString() + "/" + contract.getContractCode() + ".pdf";
        String pdfUrl;
        try {
            pdfUrl = storageAdapter.uploadPdf(pdfPath, pdfBytes);
            contract.setPdfUrl(pdfUrl);
        } catch (Exception e) {
            throw new BusinessException("PDF_UPLOAD_FAILED", "Failed to re-upload contract PDF to storage: " + e.getMessage(), org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
        }

        // 3. Rotate the secure token (invalidate old one, issue new one)
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String tokenHex = bytesToHex(tokenBytes);
        String tokenHash = hashSha256(tokenHex);

        tokenRepository.deleteByContractId(contract.getId());

        ContractConfirmationTokenEntity tokenEntity = ContractConfirmationTokenEntity.builder()
                .contractId(contract.getId())
                .tokenHash(tokenHash)
                .expiresAt(OffsetDateTime.now().plusHours(24))
                .build();
        tokenRepository.save(tokenEntity);

        // 4. Update sentAt timestamp
        contract.setSentAt(OffsetDateTime.now());
        contract.setSentTo(recipientEmail);
        contract = contractRepository.save(contract);

        // 5. Build portal link and fire email event
        String portalBase = contractProperties.getPortalBaseUrl();
        if (portalBase != null && portalBase.endsWith("/")) {
            portalBase = portalBase.substring(0, portalBase.length() - 1);
        }
        String secureLink = portalBase + "/portal/contracts/" + contract.getId() + "?token=" + tokenHex;

        activityLogPublisher.publish(
                ActivityLogType.CONTRACT_SENT,
                EntityType.CONTRACT,
                contract.getId(),
                "Resent contract " + contract.getContractCode() + " to " + recipientEmail,
                null
        );

        eventPublisher.publishEvent(new ContractSentEvent(contract, secureLink, pdfBytes));

        log.info("Contract {} resent successfully to {}.", contract.getContractCode(), recipientEmail);
        return contract;
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String hashSha256(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 hashing not available", e);
        }
    }
}
