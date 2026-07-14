package com.novax.leadora.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised when a new lead would duplicate an existing one by a unique-ish contact
 * field (email or phone). Carries the id of the matching lead in {@code details}
 * so the UI can offer a "view existing lead" link instead of a generic error.
 *
 * <p>Maps to HTTP 409 Conflict via {@link com.novax.leadora.api.exception.GlobalExceptionHandler}
 * (handled by the {@code BusinessException} branch).
 */
public class DuplicateLeadException extends BusinessException {

    public DuplicateLeadException(String field, String value, Object existingLeadId) {
        super(
                "DUPLICATE_LEAD",
                "A lead with this " + field + " (\"" + value + "\") already exists.",
                existingLeadId != null ? existingLeadId.toString() : null,
                HttpStatus.CONFLICT
        );
    }
}
