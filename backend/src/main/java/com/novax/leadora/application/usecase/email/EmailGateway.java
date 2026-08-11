package com.novax.leadora.application.usecase.email;

import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;

@Validated
public interface EmailGateway {
    EmailSendResult send(@Valid EmailRequest request);
}
