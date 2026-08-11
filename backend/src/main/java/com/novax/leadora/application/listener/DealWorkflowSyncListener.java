package com.novax.leadora.application.listener;

import com.novax.leadora.application.event.DealWorkflowSyncEvent;
import com.novax.leadora.application.usecase.deal.DealWorkflowSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class DealWorkflowSyncListener {

    private final DealWorkflowSyncService dealWorkflowSyncService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleSync(DealWorkflowSyncEvent event) {
        log.info("Received DealWorkflowSyncEvent for deal: {}. Syncing pipeline stage.", event.dealId());
        try {
            dealWorkflowSyncService.syncPipelineStage(event.dealId());
        } catch (Exception e) {
            log.error("Failed to sync deal workflow for deal {}: {}", event.dealId(), e.getMessage(), e);
        }
    }
}
