package com.novax.leadora.application.usecase.email.exception;

public class InvalidEmailException extends EmailException {
    public InvalidEmailException(String message) {
        super(message);
    }

    public InvalidEmailException(String message, Throwable cause) {
        super(message, cause);
    }
}
