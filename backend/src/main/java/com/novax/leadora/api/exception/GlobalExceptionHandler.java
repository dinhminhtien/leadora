package com.novax.leadora.api.exception;

import com.novax.leadora.application.usecase.email.exception.EmailConfigurationException;
import com.novax.leadora.application.usecase.email.exception.EmailException;
import com.novax.leadora.application.usecase.email.exception.InvalidEmailException;
import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.common.exception.BusinessRuleException;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.util.stream.Collectors;

import com.novax.leadora.infrastructure.security.audit.SecurityAuditLogger;
import com.novax.leadora.common.security.CurrentUserProvider;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final SecurityAuditLogger securityAuditLogger;
    private final CurrentUserProvider currentUserProvider;

    public GlobalExceptionHandler(SecurityAuditLogger securityAuditLogger, CurrentUserProvider currentUserProvider) {
        this.securityAuditLogger = securityAuditLogger;
        this.currentUserProvider = currentUserProvider;
    }

    private static final UUID SYSTEM_UUID = new UUID(0L, 0L);

    /** MSG-05, verbatim from the SRS application-messages catalogue (§5.3). */
    private static final String MSG_05_ACCESS_DENIED = "You do not have permission to access this function.";

    private ResponseEntity<ProblemDetail> createProblemResponse(HttpStatus status, String errorCode, String message,
            Object details) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, message);
        problemDetail.setTitle(status.getReasonPhrase());

        // Add custom properties for compatibility with client-side ApiResponse parser
        problemDetail.setProperty("success", false);
        problemDetail.setProperty("message", message);
        if (errorCode != null) {
            problemDetail.setProperty("errorCode", errorCode);
        }
        if (details != null) {
            if (details instanceof String) {
                problemDetail.setProperty("details", details);
            } else {
                problemDetail.setProperty("errors", details);
            }
        }
        String correlationId = org.slf4j.MDC.get("correlationId");
        if (correlationId != null) {
            problemDetail.setProperty("correlationId", correlationId);
        }
        problemDetail.setProperty("timestamp", java.time.OffsetDateTime.now().toString());

        return ResponseEntity.status(status)
                .contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON)
                .body(problemDetail);
    }

    private ResponseEntity<ProblemDetail> createProblemResponse(HttpStatus status, String errorCode, String message,
            Object details, String field) {
        ResponseEntity<ProblemDetail> response = createProblemResponse(status, errorCode, message, details);
        if (field != null && response.getBody() != null) {
            response.getBody().setProperty("field", field);
        }
        return response;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationException(MethodArgumentNotValidException ex) {
        String errorMessage = "Validation failed for request.";
        java.util.Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        e -> e.getField(),
                        e -> e.getDefaultMessage() != null ? e.getDefaultMessage() : "Invalid value",
                        (existing, replacement) -> existing));
        log.warn("Validation error: {}", errors);
        return createProblemResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", errorMessage, errors);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return createProblemResponse(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", ex.getMessage(), null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        log.warn("Method not allowed: {}", ex.getMessage());
        return createProblemResponse(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                "This action is not available on the requested resource.", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Malformed path/parameter '{}': {}", ex.getName(), ex.getMessage());
        return createProblemResponse(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND",
                "The requested resource does not exist.", null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex,
            jakarta.servlet.http.HttpServletRequest request) {
        log.warn("Access denied: {}", ex.getMessage());

        UUID userId = SYSTEM_UUID;
        String userName = "Unauthenticated User";
        try {
            com.novax.leadora.infrastructure.persistence.entity.UserEntity user = currentUserProvider.resolve(null);
            if (user != null) {
                userId = user.getUserId();
                userName = user.getFullName() + " (" + user.getEmail() + ")";
            }
        } catch (Exception ignored) {
        }

        securityAuditLogger.logAccessDeniedForUser(request, ex, userId, userName);

        return createProblemResponse(HttpStatus.FORBIDDEN, "ACCESS_DENIED", MSG_05_ACCESS_DENIED, ex.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLock(OptimisticLockingFailureException ex) {
        log.warn("Optimistic lock conflict: {}", ex.getMessage());
        return createProblemResponse(HttpStatus.CONFLICT, "ALREADY_PROCESSED",
                "This record was just modified by someone else. Please refresh and try again.", null);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> handleBusinessException(BusinessException ex,
            jakarta.servlet.http.HttpServletRequest request) {
        log.warn("Business rule violation [{}]: {}", ex.getErrorCode(), ex.getMessage());

        if ("ACCOUNT_NOT_PROVISIONED".equals(ex.getErrorCode())) {
            securityAuditLogger.logUnprovisionedAccount(request, ex);
        } else if ("ACCOUNT_LOCKED".equals(ex.getErrorCode())) {
            securityAuditLogger.logLockedAccount(request, ex);
        }

        return createProblemResponse(HttpStatus.valueOf(ex.getHttpStatus().value()), ex.getErrorCode(), ex.getMessage(),
                ex.getDetails(), ex.getField());
    }

    /**
     * Email failures are known outcomes, not surprises, so none of them may surface as an
     * internal error.
     *
     * <p>The split matters to the caller: a malformed address is theirs to fix and comes back as
     * 422; a provider that rejected or could not be reached is ours, and comes back as 502 so the
     * client can offer "try again" rather than "check the address". Missing credentials are a
     * deployment fault — 503, because retrying the same request will keep failing until someone
     * configures the service.
     *
     * <p>Before this handler existed these all fell through to {@link #handleAllExceptions}, and a
     * rep whose customer had no usable email address was told "An unexpected error occurred".
     */
    @ExceptionHandler(EmailException.class)
    public ResponseEntity<ProblemDetail> handleEmailFailure(EmailException ex) {
        if (ex instanceof InvalidEmailException) {
            log.warn("Invalid email recipient: {}", ex.getMessage());
            return createProblemResponse(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_EMAIL_FORMAT", ex.getMessage(), null);
        }
        if (ex instanceof EmailConfigurationException) {
            String reference = newReference();
            log.error("Email service is not configured [ref={}]: {}", reference, ex.getMessage(), ex);
            return createProblemResponse(HttpStatus.SERVICE_UNAVAILABLE, "EMAIL_NOT_CONFIGURED",
                    "Email delivery is not configured on this environment, so the message could not be sent. "
                            + "Nothing has been changed — contact an administrator and try again afterwards.",
                    reference);
        }
        String reference = newReference();
        log.error("Email delivery failed [ref={}]: {}", reference, ex.getMessage(), ex);
        return createProblemResponse(HttpStatus.BAD_GATEWAY, "EMAIL_DELIVERY_FAILED",
                "The email provider could not deliver this message. Verify the recipient address, "
                        + "then try again — nothing else has been changed.",
                reference);
    }

    /**
     * Constraint violations raised outside a controller — most often by a {@code @Validated}
     * service boundary such as {@code EmailGateway}. Without this they reached
     * {@link #handleAllExceptions} as a 500, which is how an unusable recipient address ended up
     * reported as an internal error.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex) {
        java.util.Map<String, String> errors = ex.getConstraintViolations().stream()
                .collect(Collectors.toMap(
                        v -> String.valueOf(v.getPropertyPath()),
                        v -> v.getMessage() != null ? v.getMessage() : "Invalid value",
                        (existing, replacement) -> existing));
        log.warn("Constraint violation: {}", errors);
        return createProblemResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "Some of the values sent are not valid.", errors);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> handleResponseStatusException(ResponseStatusException ex) {
        log.warn("Request error [{}]: {}", ex.getStatusCode(), ex.getReason());
        return createProblemResponse(HttpStatus.valueOf(ex.getStatusCode().value()), "REQUEST_ERROR", ex.getReason(),
                null);
    }

    @ExceptionHandler({ IllegalStateException.class, BusinessRuleException.class })
    public ResponseEntity<ProblemDetail> handleBusinessRuleViolation(RuntimeException ex) {
        log.warn("Business rule violation: {}", ex.getMessage());
        return createProblemResponse(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_VIOLATION", ex.getMessage(), null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ProblemDetail> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        log.warn("Upload too large: {}", ex.getMessage());
        return createProblemResponse(HttpStatus.PAYLOAD_TOO_LARGE, "UPLOAD_TOO_LARGE",
                "The file exceeds the maximum allowed size (5MB).", null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.warn("Malformed request body: {}", ex.getMessage());
        return createProblemResponse(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "The request body is invalid (malformed or wrongly encoded JSON).", null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Invalid argument: {}", ex.getMessage());
        return createProblemResponse(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", ex.getMessage(), null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrity(DataIntegrityViolationException ex) {
        String reference = newReference();
        log.error("Data integrity violation [ref={}]: {}", reference,
                ex.getMostSpecificCause().getMessage(), ex);
        return createProblemResponse(HttpStatus.CONFLICT, "DATA_CONSTRAINT_VIOLATION",
                "The data could not be saved because it breaks a database constraint — "
                        + "a value may be too long, required, or already in use. "
                        + "Please review the form and try again.",
                reference);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleAllExceptions(Exception ex) {
        String reference = newReference();
        log.error("Unexpected system error [ref={}]: {}", reference, ex.getMessage(), ex);
        return createProblemResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred. Please try again later.", reference);
    }

    private static String newReference() {
        return UUID.randomUUID().toString().substring(0, 6);
    }
}
