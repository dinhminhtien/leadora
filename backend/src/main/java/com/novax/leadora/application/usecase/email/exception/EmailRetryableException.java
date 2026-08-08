package com.novax.leadora.application.usecase.email.exception;

public class EmailRetryableException extends EmailDeliveryException {
    public EmailRetryableException(String message) {
        super(message);
    }

    public EmailRetryableException(String message, Throwable cause) {
        super(message, cause);
    }
}
