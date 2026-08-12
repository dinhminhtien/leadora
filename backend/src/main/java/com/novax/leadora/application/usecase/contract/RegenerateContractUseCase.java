package com.novax.leadora.application.usecase.contract;

import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.ContractEntity;
import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ContractStatus;
import com.novax.leadora.infrastructure.persistence.repository.ContractRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RegenerateContractUseCase {

    private static final Set<ContractStatus> TERMINAL_STATUSES = EnumSet.of(
            ContractStatus.CANCELLED, ContractStatus.EXPIRED, ContractStatus.SUPERSEDED
    );

    private static final Set<ContractStatus> BLOCKING_STATUSES = EnumSet.of(
            ContractStatus.DRAFT, ContractStatus.SENT, ContractStatus.ACKNOWLEDGED, ContractStatus.ACTIVE
    );

    private final ContractRepository contractRepository;
    private final QuotationRepository quotationRepository;
    private final GenerateContractUseCase generateContractUseCase;
    private final CurrentUserProvider currentUserProvider;

    /**
     * Creates a new DRAFT contract (next version) for the quotation linked to the given
     * cancelled/expired contract. Fails if there is already a live contract (DRAFT/SENT/
     * ACKNOWLEDGED/ACTIVE) for the same quotation — the caller must cancel it first.
     */
    @Transactional
    public ContractEntity execute(UUID cancelledContractId) {
        log.info("Regenerating contract from cancelled contract id: {}", cancelledContractId);

        ContractEntity cancelled = contractRepository.findById(cancelledContractId)
                .orElseThrow(() -> new BusinessException("CONTRACT_NOT_FOUND",
                        "Contract not found with id: " + cancelledContractId,
                        org.springframework.http.HttpStatus.NOT_FOUND));

        if (!TERMINAL_STATUSES.contains(cancelled.getStatus())) {
            throw new BusinessException("INVALID_CONTRACT_STATUS",
                    "Only CANCELLED, EXPIRED, or SUPERSEDED contracts can be regenerated. Current status: " + cancelled.getStatus(),
                    org.springframework.http.HttpStatus.BAD_REQUEST);
        }

        UUID quotationId = cancelled.getQuotation().getQuotationId();

        // Guard: block if a live contract already exists for this quotation
        List<ContractEntity> existingContracts = contractRepository.findByQuotation_QuotationId(quotationId);
        boolean hasLiveContract = existingContracts.stream()
                .anyMatch(c -> BLOCKING_STATUSES.contains(c.getStatus()));

        if (hasLiveContract) {
            throw new BusinessException("CONTRACT_ALREADY_ACTIVE",
                    "A live contract (DRAFT/SENT/ACKNOWLEDGED/ACTIVE) already exists for this quotation. Cancel it before regenerating.",
                    org.springframework.http.HttpStatus.CONFLICT);
        }

        QuotationEntity quotation = quotationRepository.findById(quotationId)
                .orElseThrow(() -> new BusinessException("QUOTATION_NOT_FOUND",
                        "Quotation not found for contract: " + cancelledContractId,
                        org.springframework.http.HttpStatus.NOT_FOUND));

        // Resolve current user from JWT — returns a managed (persisted) UserEntity
        UserEntity actor = currentUserProvider.resolve(null);

        ContractEntity newContract = generateContractUseCase.execute(quotation, actor);
        log.info("Regenerated contract {} (v{}) for quotation {}", newContract.getContractCode(), newContract.getVersion(), quotationId);
        return newContract;
    }
}
