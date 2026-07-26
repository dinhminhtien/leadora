package com.novax.leadora.api.dto.response;

import lombok.*;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DealWorkflowSummaryResponse {
    private UUID dealId;
    private String dealStatus;
    private String pipelineStage;
    private UUID activeQuotationId;
    private String activeQuotationStatus;
    private UUID activeBookingId;
    private String activeBookingStatus;
    private String currentPaymentStatus;
    private boolean hasPaidPayment;
}
