package com.novax.leadora.infrastructure.scheduler;

import com.novax.leadora.application.usecase.contract.ExpireContractUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExpireOverdueContractsScheduler {

    private final ExpireContractUseCase expireContractUseCase;

    /** Runs daily at 00:10 AM to expire overdue contracts */
    @Scheduled(cron = "0 10 0 * * *")
    public void expireOverdueContracts() {
        log.info("Starting daily scheduled scan for expired contracts...");
        try {
            expireContractUseCase.execute();
            log.info("Finished daily scheduled scan for expired contracts.");
        } catch (Exception e) {
            log.error("Error occurred during contract expiration scheduler execution: {}", e.getMessage(), e);
        }
    }
}
