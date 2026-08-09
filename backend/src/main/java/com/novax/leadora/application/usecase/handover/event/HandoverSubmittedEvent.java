package com.novax.leadora.application.usecase.handover.event;

import com.novax.leadora.infrastructure.persistence.entity.OpHandoverEntity;
import org.springframework.context.ApplicationEvent;

public class HandoverSubmittedEvent extends ApplicationEvent {
    private final OpHandoverEntity handover;

    public HandoverSubmittedEvent(Object source, OpHandoverEntity handover) {
        super(source);
        this.handover = handover;
    }

    public OpHandoverEntity getHandover() {
        return handover;
    }
}
