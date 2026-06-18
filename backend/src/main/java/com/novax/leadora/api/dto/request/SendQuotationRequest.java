package com.novax.leadora.api.dto.request;

import lombok.Data;

@Data
public class SendQuotationRequest {

    private String sendMethod; // EMAIL | WHATSAPP | PDF

    private String recipientName;
    private String recipientEmail;
    private String recipientPhone;

    private String sentByName;
    private String sentByRole;

    private String personalMessage;
}
