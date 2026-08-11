package com.novax.leadora.application.usecase.email.exception;

public class EmailConfigurationException extends EmailException {
    public EmailConfigurationException(String message) {
        super(message);
    }

    public EmailConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
