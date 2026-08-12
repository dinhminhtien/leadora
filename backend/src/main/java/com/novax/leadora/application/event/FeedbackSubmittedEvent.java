package com.novax.leadora.application.event;

import org.springframework.context.ApplicationEvent;
import java.util.UUID;

public class FeedbackSubmittedEvent extends ApplicationEvent {
    private final UUID feedbackId;

    public FeedbackSubmittedEvent(Object source, UUID feedbackId) {
        super(source);
        this.feedbackId = feedbackId;
    }

    public UUID getFeedbackId() {
        return feedbackId;
    }
}
