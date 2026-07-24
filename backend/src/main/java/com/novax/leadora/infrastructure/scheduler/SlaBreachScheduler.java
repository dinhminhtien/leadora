package com.novax.leadora.infrastructure.scheduler;

import com.novax.leadora.application.usecase.sla.ProcessSlaBreachUseCase;
import com.novax.leadora.application.usecase.sla.ProcessSlaEscalationUseCase;
import com.novax.leadora.application.usecase.sla.ProcessSlaWarningUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SlaBreachScheduler {

    private final ProcessSlaBreachUseCase processSlaBreachUseCase;
    private final ProcessSlaWarningUseCase processSlaWarningUseCase;
    private final ProcessSlaEscalationUseCase processSlaEscalationUseCase;

    /**
     * UC-17.2 step 5-6: Scan every 30 seconds for ACTIVE records whose warningAt has
     * passed. Send warning notifications to assigned staff + managers.
     */
    @Scheduled(fixedDelay = 30_000)
    public void detectWarnings() {
        try {
            int count = processSlaWarningUseCase.execute();
            if (count > 0) {
                log.info("SLA warning scan: {} warning notification(s) sent", count);
            }
        } catch (Exception e) {
            log.error("SLA warning scheduler error: {}", e.getMessage(), e);
        }
    }

    /**
     * UC-17.4 step 1: Scan every 30 seconds for ACTIVE SLA records that have passed
     * their deadline. Each detected breach is updated to BREACHED and notifications are sent.
     */
    @Scheduled(fixedDelay = 30_000)
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

    /**
     * UC-17.4: escalate still-unresolved BREACHED records past escalationAt to Admin —
     * a higher tier than the initial breach notification (Manager + assignee).
     */
    @Scheduled(fixedDelay = 30_000)
    public void detectEscalations() {
        try {
            int count = processSlaEscalationUseCase.execute();
            if (count > 0) {
                log.info("SLA escalation scan: {} record(s) escalated", count);
            }
        } catch (Exception e) {
            log.error("SLA escalation scheduler error: {}", e.getMessage(), e);
        }
    }
}
