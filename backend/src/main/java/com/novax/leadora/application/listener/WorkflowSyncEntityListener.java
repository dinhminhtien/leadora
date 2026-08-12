package com.novax.leadora.application.listener;

import com.novax.leadora.application.event.DealWorkflowSyncEvent;
import com.novax.leadora.infrastructure.persistence.entity.BookingEntity;
import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostUpdate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class WorkflowSyncEntityListener {

    private static ApplicationEventPublisher eventPublisher;

    @Autowired
    public void init(ApplicationEventPublisher eventPublisher) {
        WorkflowSyncEntityListener.eventPublisher = eventPublisher;
    }

    @PostPersist
    @PostUpdate
    public void onSave(Object entity) {
        if (eventPublisher == null) {
            return;
        }
        try {
            if (entity instanceof QuotationEntity q) {
                if (q.getDeal() != null) {
                    log.debug("Publishing DealWorkflowSyncEvent for quotation: {}", q.getQuotationId());
                    eventPublisher.publishEvent(new DealWorkflowSyncEvent(q.getDeal().getDealId()));
                }
            } else if (entity instanceof BookingEntity b) {
                if (b.getQuotation() != null && b.getQuotation().getDeal() != null) {
                    log.debug("Publishing DealWorkflowSyncEvent for booking: {}", b.getBookingId());
                    eventPublisher.publishEvent(new DealWorkflowSyncEvent(b.getQuotation().getDeal().getDealId()));
                }
            }
        } catch (Exception e) {
            log.error("Failed to publish DealWorkflowSyncEvent for entity: {}", entity, e);
        }
    }
}
