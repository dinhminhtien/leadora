package com.novax.leadora.application.usecase.quotation.event;

import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import org.springframework.context.ApplicationEvent;

public class QuotationAcceptedEvent extends ApplicationEvent {
    private final QuotationEntity quotation;

    public QuotationAcceptedEvent(Object source, QuotationEntity quotation) {
        super(source);
        this.quotation = quotation;
    }

    public QuotationEntity getQuotation() {
        return quotation;
    }
}
