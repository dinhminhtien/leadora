package com.novax.leadora.infrastructure.scheduler;

import com.novax.leadora.application.usecase.sla.ProcessSlaBreachUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SlaBreachScheduler {

    private final ProcessSlaBreachUseCase processSlaBreachUseCase;

    /**
     * UC-17.4 step 1: Scan every minute for ACTIVE SLA records that have passed their deadline.
     * Each detected breach is updated to BREACHED and notifications are sent.
     */
    @Scheduled(fixedDelay = 60_000)
    public void detectBreaches() {
        try {
            int count = processSlaBreachUseCase.execute();
            if (count > 0) {
                log.info("SLA breach scan: {} new breach(es) detected and notified", count);
            }
        } catch (Exception e) {
            log.error("SLA breach scheduler error: {}", e.getMessage(), e);
        }
    }
}
