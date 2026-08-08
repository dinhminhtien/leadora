package com.novax.leadora.application.usecase.email.event;

public record FeedbackInvitationEvent(
    String email,
    String customerName,
    String feedbackLink
) {}
