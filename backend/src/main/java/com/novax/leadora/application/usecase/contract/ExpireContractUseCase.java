package com.novax.leadora.application.usecase.contract;

import com.novax.leadora.application.event.ContractExpiredEvent;
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

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExpireContractUseCase {

    private final ContractRepository contractRepository;
    private final ActivityLogPublisher activityLogPublisher;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Finds and expires contracts whose validity date has passed.
     */
    @Transactional
    public void execute() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        log.info("Running ExpireContractUseCase for date: {}", today);

        List<ContractStatus> expirableStatuses = List.of(ContractStatus.DRAFT, ContractStatus.SENT);
        List<ContractEntity> expiredContracts = contractRepository.findByStatusInAndValidUntilBefore(expirableStatuses, today);

        log.info("Found {} contracts to expire", expiredContracts.size());

        for (ContractEntity contract : expiredContracts) {
            log.info("Expiring contract: {}", contract.getContractCode());
            contract.setStatus(ContractStatus.EXPIRED);
            contractRepository.save(contract);

            activityLogPublisher.publish(
                    ActivityLogType.CONTRACT_EXPIRED,
                    EntityType.CONTRACT,
                    contract.getId(),
                    "Contract " + contract.getContractCode() + " expired automatically",
                    null
            );

            eventPublisher.publishEvent(new ContractExpiredEvent(contract));
        }
    }
}
