package com.novax.leadora.application.usecase.email;

import java.time.Instant;

public record EmailSendResult(
    String messageId,
    Instant sentAt,
    boolean success
) {}
