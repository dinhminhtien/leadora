package com.novax.leadora.application.usecase.notification;

import com.novax.leadora.infrastructure.persistence.entity.NotificationEntity;
import org.springframework.context.ApplicationEvent;

public class NotificationCreatedEvent extends ApplicationEvent {
    
    private final NotificationEntity notification;

    public NotificationCreatedEvent(Object source, NotificationEntity notification) {
        super(source);
        this.notification = notification;
    }

    public NotificationEntity getNotification() {
        return notification;
    }
}
