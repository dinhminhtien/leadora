package com.novax.leadora.application.usecase.contract;

import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.application.event.ContractCancelledEvent;
import com.novax.leadora.application.usecase.activitylog.ActivityLogPublisher;
import com.novax.leadora.infrastructure.persistence.entity.ContractEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ContractStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.EntityType;
import com.novax.leadora.infrastructure.persistence.entity.enums.ActivityLogType;
import com.novax.leadora.infrastructure.persistence.repository.ContractRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CancelContractUseCase {

    private final ContractRepository contractRepository;
    private final ActivityLogPublisher activityLogPublisher;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ContractEntity execute(UUID contractId) {
        log.info("Cancelling contract id: {}", contractId);

        ContractEntity contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new BusinessException("CONTRACT_NOT_FOUND", "Contract not found", org.springframework.http.HttpStatus.NOT_FOUND));

        if (contract.getStatus() == ContractStatus.ACTIVE ||
                contract.getStatus() == ContractStatus.CANCELLED ||
                contract.getStatus() == ContractStatus.SUPERSEDED ||
                contract.getStatus() == ContractStatus.EXPIRED) {
            throw new BusinessException("INVALID_CONTRACT_STATUS", 
                    "Contract cannot be cancelled in its current status: " + contract.getStatus(), org.springframework.http.HttpStatus.BAD_REQUEST);
        }

        contract.setStatus(ContractStatus.CANCELLED);
        contract = contractRepository.save(contract);

        log.info("Contract {} has been cancelled.", contract.getContractCode());

        // Publish activity
        activityLogPublisher.publish(
                ActivityLogType.CONTRACT_CANCELLED,
                EntityType.CONTRACT,
                contract.getId(),
                "Contract " + contract.getContractCode() + " cancelled",
                null
        );

        // Publish event
        eventPublisher.publishEvent(new ContractCancelledEvent(contract));

        return contract;
    }
}
