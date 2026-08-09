package com.novax.leadora.application.usecase.quotation.event;

import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import org.springframework.context.ApplicationEvent;

public class QuotationRejectedEvent extends ApplicationEvent {
    private final QuotationEntity quotation;
    private final String reason;

    public QuotationRejectedEvent(Object source, QuotationEntity quotation, String reason) {
        super(source);
        this.quotation = quotation;
        this.reason = reason;
    }

    public QuotationEntity getQuotation() {
        return quotation;
    }

    public String getReason() {
        return reason;
    }
}
