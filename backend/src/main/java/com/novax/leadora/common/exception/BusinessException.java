package com.novax.leadora.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * A refusal the caller can act on: the request was understood, and the business says no.
 *
 * <p>Always carries the status it should surface as, so a known failure never reaches
 * {@code handleAllExceptions} and gets reported as an internal error. {@code field} names the
 * input the user has to fix, when there is one — the client uses it to point at the offending
 * control instead of showing a banner and leaving the user to guess.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus httpStatus;
    private final String details;
    /** Dotted path of the offending input, e.g. {@code customer.email}. Null when none applies. */
    private final String field;

    public BusinessException(String errorCode, String message, HttpStatus httpStatus) {
        this(errorCode, message, null, httpStatus, null);
    }

    public BusinessException(String errorCode, String message, String details, HttpStatus httpStatus) {
        this(errorCode, message, details, httpStatus, null);
    }

    public BusinessException(String errorCode, String message, String details, HttpStatus httpStatus, String field) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.details = details;
        this.field = field;
    }

    /** The same refusal, tagged with the input the user must correct. */
    public static BusinessException forField(String errorCode, String message, HttpStatus httpStatus, String field) {
        return new BusinessException(errorCode, message, null, httpStatus, field);
    }
}
