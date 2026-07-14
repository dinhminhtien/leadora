package com.novax.leadora.infrastructure.scheduler;

import com.novax.leadora.application.usecase.identity.InactivateIdleUsersUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily housekeeping: deactivate accounts idle for more than 7 days
 * (see {@link InactivateIdleUsersUseCase}). Runs at 03:00 server time.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserInactivityScheduler {

    private final InactivateIdleUsersUseCase inactivateIdleUsersUseCase;

    @Scheduled(cron = "0 0 3 * * *")
    public void deactivateIdleAccounts() {
        try {
            inactivateIdleUsersUseCase.execute();
        } catch (Exception e) {
            log.warn("Idle-user inactivation job failed: {}", e.getMessage());
        }
    }
}
