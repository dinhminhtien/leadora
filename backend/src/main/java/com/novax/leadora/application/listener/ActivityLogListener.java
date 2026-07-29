package com.novax.leadora.application.listener;

import com.novax.leadora.application.event.AuditCorrectionEvent;
import com.novax.leadora.application.event.BusinessActivityEvent;
import com.novax.leadora.application.event.SecurityAuditEvent;
import com.novax.leadora.application.usecase.activitylog.AppendActivityLogSameTransactionUseCase;
import com.novax.leadora.application.usecase.activitylog.AppendActivityLogUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ActivityLogListener {

    private final AppendActivityLogUseCase appendActivityLogUseCase;
    private final AppendActivityLogSameTransactionUseCase appendActivityLogSameTransactionUseCase;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleBusinessActivity(BusinessActivityEvent event) {
        appendActivityLogUseCase.execute(event.command());
    }

    @EventListener
    public void handleSecurityActivity(SecurityAuditEvent event) {
        appendActivityLogUseCase.execute(event.command());
    }

    @EventListener
    public void handleCorrectionActivity(AuditCorrectionEvent event) {
        appendActivityLogSameTransactionUseCase.execute(event.command());
    }
}
