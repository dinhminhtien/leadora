package com.novax.leadora.application.event;

import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;

public record QuotationAcceptedEvent(QuotationEntity quotation) {
}
