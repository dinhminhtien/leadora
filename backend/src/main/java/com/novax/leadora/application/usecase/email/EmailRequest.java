package com.novax.leadora.application.usecase.email;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record EmailRequest(
    String from,
    @NotEmpty(message = "Recipients list (to) cannot be empty")
    List<@NotBlank @Email String> to,
    List<@NotBlank @Email String> cc,
    List<@NotBlank @Email String> bcc,
    @NotBlank(message = "Subject cannot be blank")
    String subject,
    @NotBlank(message = "HTML content cannot be blank")
    String html,
    List<EmailAttachment> attachments,
    String idempotencyKey
) {
    public EmailRequest {
        if (cc == null) cc = List.of();
        if (bcc == null) bcc = List.of();
        if (attachments == null) attachments = List.of();
    }
}
