package com.novax.leadora.api.dto.response;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PipelineDealCardResponse {
    private DealResponse deal;
    private boolean hasActiveQuotation;
    private String activeQuotationStatus;
    private boolean hasActiveBooking;
    private String activeBookingStatus;
    private String paymentStatus;
}
