package com.novax.leadora.infrastructure.activitylog;

import com.novax.leadora.application.event.BusinessActivityEvent;
import com.novax.leadora.application.usecase.activitylog.ActivityLogCommand;
import com.novax.leadora.application.usecase.activitylog.ActivityLogPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SpringActivityLogPublisher implements ActivityLogPublisher {

    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void publish(ActivityLogCommand command) {
        eventPublisher.publishEvent(new BusinessActivityEvent(command));
    }
}
