package com.novax.leadora.application.usecase.email.exception;

public class EmailDeliveryException extends EmailException {
    public EmailDeliveryException(String message) {
        super(message);
    }

    public EmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
