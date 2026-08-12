package com.novax.leadora.application.event;

import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;

public record QuotationOtpRequestedEvent(QuotationEntity quotation, String otpCode, String recipientEmail) {
}
