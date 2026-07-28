package com.novax.leadora.application.event;

import com.novax.leadora.application.usecase.activitylog.ActivityLogCommand;

public record SecurityAuditEvent(ActivityLogCommand command) {
}
