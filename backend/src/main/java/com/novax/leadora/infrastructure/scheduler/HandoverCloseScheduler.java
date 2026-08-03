package com.novax.leadora.infrastructure.scheduler;

import com.novax.leadora.application.usecase.handover.CloseFinishedHandoversUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Nightly housekeeping: close handovers whose arrival is over — the guest checked out, cancelled, or
 * never came (see {@link CloseFinishedHandoversUseCase}). Runs at 03:30 server time, after the idle
 * user sweep.
 *
 * <p>A sweep rather than a reaction to the booking status change, because there is no event bus to
 * hang one on. It also means a record missed for any reason gets closed on the next run.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HandoverCloseScheduler {

    private final CloseFinishedHandoversUseCase closeFinishedHandoversUseCase;

    @Scheduled(cron = "0 30 3 * * *")
    public void closeFinishedHandovers() {
        try {
            closeFinishedHandoversUseCase.execute();
        } catch (Exception e) {
            log.warn("Handover close job failed: {}", e.getMessage());
        }
    }
}
