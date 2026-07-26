package com.novax.leadora.api.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class SendQuotationRequest {

    @NotBlank(message = "Send method is required")
    @Pattern(regexp = "^(EMAIL|WHATSAPP|PDF)$", message = "Send method must be EMAIL, WHATSAPP, or PDF")
    private String sendMethod; // EMAIL | WHATSAPP | PDF

    private String recipientName;

    @Email(message = "Recipient email must be a valid email address")
    private String recipientEmail;

    private String recipientPhone;

    // sentByName/sentByRole intentionally removed — actor is resolved server-side
    // from the authenticated session (BR-37), never trusted from the client.

    private String personalMessage;
}
