package com.novax.leadora.infrastructure.activitylog;

import com.novax.leadora.application.event.AuditCorrectionEvent;
import com.novax.leadora.application.event.BusinessActivityEvent;
import com.novax.leadora.application.event.SecurityAuditEvent;
import com.novax.leadora.application.usecase.activitylog.ActivityLogCommand;
import com.novax.leadora.application.usecase.activitylog.ActivityLogPublisher;
import com.novax.leadora.infrastructure.persistence.entity.enums.ActivityLogType;
import com.novax.leadora.infrastructure.persistence.entity.enums.RecordOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SpringActivityLogPublisher implements ActivityLogPublisher {

    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void publish(ActivityLogCommand command) {
        if (command == null) {
            return;
        }
        if (command.getActivityType() != null && isSecurityEvent(command.getActivityType())) {
            eventPublisher.publishEvent(new SecurityAuditEvent(command));
        } else if (command.getRecordOperation() == RecordOperation.CORRECTED
                || command.getRecordOperation() == RecordOperation.VOIDED) {
            eventPublisher.publishEvent(new AuditCorrectionEvent(command));
        } else {
            eventPublisher.publishEvent(new BusinessActivityEvent(command));
        }
    }

    private boolean isSecurityEvent(ActivityLogType type) {
        return type == ActivityLogType.USER_LOGGED_IN
                || type == ActivityLogType.USER_LOGGED_OUT
                || type == ActivityLogType.PASSWORD_CHANGED
                || type == ActivityLogType.PASSWORD_RESET_REQUESTED
                || type == ActivityLogType.PASSWORD_RESET_COMPLETED
                || type == ActivityLogType.USER_ACCOUNT_CREATED
                || type == ActivityLogType.USER_ACCOUNT_UPDATED
                || type == ActivityLogType.LOGIN_FAILED
                || type == ActivityLogType.ACCESS_DENIED_EVENT
                || type == ActivityLogType.INVALID_TOKEN_ACCESS
                || type == ActivityLogType.FEEDBACK_LINK_EXPIRED;
    }
}
