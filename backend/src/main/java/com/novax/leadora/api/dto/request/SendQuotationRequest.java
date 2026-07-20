package com.novax.leadora.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class SendQuotationRequest {

    @NotBlank(message = "Send method is required")
    @Pattern(regexp = "^(EMAIL|WHATSAPP|PDF)$", message = "Send method must be EMAIL, WHATSAPP, or PDF")
    private String sendMethod; // EMAIL | WHATSAPP | PDF

    private String recipientName;
    private String recipientEmail;
    private String recipientPhone;

    private String sentByName;
    private String sentByRole;

    private String personalMessage;
}
