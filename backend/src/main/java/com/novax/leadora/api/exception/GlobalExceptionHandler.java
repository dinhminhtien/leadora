package com.novax.leadora.api.exception;

import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.common.exception.BusinessRuleException;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
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

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        String errorMessage = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("Validation error: {}", errorMessage);
        return ResponseEntity.badRequest()
                .body(ApiResponse.businessError("VALIDATION_ERROR", errorMessage, null));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.businessError("RESOURCE_NOT_FOUND", ex.getMessage(), null));
    }

    /**
     * A path variable that fails to bind to its target type — most commonly a
     * malformed UUID such as {@code /leads/999}. Without this handler such requests
     * fall through to {@link #handleAllExceptions} and return a misleading HTTP 500.
     * Treating it as 404 lets the UI render a proper "not found" state instead of
     * hanging or showing a server-crash banner.
     */
    /**
     * A verb the route does not offer (e.g. POST to a read-only collection). Without this it fell
     * into the catch-all below and surfaced as a 500 with a support reference, which reads as
     * "the server broke" when the request was simply wrong.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        log.warn("Method not allowed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.businessError("METHOD_NOT_ALLOWED",
                        "This action is not available on the requested resource.", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Malformed path/parameter '{}': {}", ex.getName(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.businessError("RESOURCE_NOT_FOUND",
                        "The requested resource does not exist.", null));
    }

    /**
     * Authorization failures — both a {@code @PreAuthorize} denial (role or permission code, which
     * arrives as Spring Security's own "Access Denied") and one thrown by a use case's access
     * policy (e.g. a Sales Staff opening a lead that is not theirs).
     *
     * <p>The user-facing message is always MSG-05 verbatim, so every denial reads the same
     * regardless of which rule failed; the specific reason goes to the log and to {@code details}
     * for support, not into the headline message.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex, jakarta.servlet.http.HttpServletRequest request) {
        log.warn("Access denied: {}", ex.getMessage());

        UUID userId = SYSTEM_UUID;
        String userName = "Unauthenticated User";
        try {
            com.novax.leadora.infrastructure.persistence.entity.UserEntity user = currentUserProvider.resolve(null);
            if (user != null) {
                userId = user.getUserId();
                userName = user.getFullName() + " (" + user.getEmail() + ")";
            }
        } catch (Exception ignored) {}

        securityAuditLogger.logAccessDeniedForUser(request, ex, userId, userName);

        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.businessError("ACCESS_DENIED", MSG_05_ACCESS_DENIED, ex.getMessage()));
    }

    /**
     * Two managers approving the same quotation concurrently (E3, UC-14.3) — JPA's
     * @Version check fails the second writer instead of silently overwriting the first.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(OptimisticLockingFailureException ex) {
        log.warn("Optimistic lock conflict: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.businessError("ALREADY_PROCESSED",
                        "This record was just modified by someone else. Please refresh and try again.", null));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex, jakarta.servlet.http.HttpServletRequest request) {
        log.warn("Business rule violation [{}]: {}", ex.getErrorCode(), ex.getMessage());

        if ("ACCOUNT_NOT_PROVISIONED".equals(ex.getErrorCode())) {
            securityAuditLogger.logUnprovisionedAccount(request, ex);
        } else if ("ACCOUNT_LOCKED".equals(ex.getErrorCode())) {
            securityAuditLogger.logLockedAccount(request, ex);
        }

        return ResponseEntity.status(ex.getHttpStatus())
                .body(ApiResponse.businessError(ex.getErrorCode(), ex.getMessage(), ex.getDetails()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleResponseStatusException(ResponseStatusException ex) {
        log.warn("Request error [{}]: {}", ex.getStatusCode(), ex.getReason());
        return ResponseEntity.status(ex.getStatusCode())
                .body(ApiResponse.businessError("REQUEST_ERROR", ex.getReason(), null));
    }

    @ExceptionHandler({IllegalStateException.class, BusinessRuleException.class})
    public ResponseEntity<ApiResponse<Void>> handleBusinessRuleViolation(RuntimeException ex) {
        log.warn("Business rule violation: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.businessError("BUSINESS_RULE_VIOLATION", ex.getMessage(), null));
    }

    /**
     * A multipart upload larger than {@code spring.servlet.multipart.max-file-size}. The chat
     * document upload also enforces a 5 MB app-level cap with a friendlier message; this catches
     * anything that trips the servlet's hard ceiling first so the client gets 413 + a clear reason
     * instead of a generic 500.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        log.warn("Upload too large: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.businessError("UPLOAD_TOO_LARGE",
                        "The file exceeds the maximum allowed size (5MB).", null));
    }

    /**
     * A malformed / unreadable request body (invalid JSON, wrong charset, empty body where one is
     * required). This is a client mistake, not a server fault — return 400 instead of letting it
     * fall through to the generic 500 handler.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.warn("Malformed request body: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.businessError("MALFORMED_REQUEST",
                        "The request body is invalid (malformed or wrongly encoded JSON).", null));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Invalid argument: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.businessError("INVALID_ARGUMENT", ex.getMessage(), null));
    }

    /**
     * A write the database refused: a value longer than its column, a NOT NULL left empty, a broken
     * foreign key, a unique index already taken.
     *
     * <p>Without this handler such failures fell through to {@link #handleAllExceptions} and came
     * back as HTTP 500 "An unexpected error occurred" — which is wrong twice over. It is not a
     * server fault (the input was invalid), and the message tells the user nothing they can act on:
     * every lead create/edit/convert failure looked identical and pointed at the Admin.
     *
     * <p><b>The cause is deliberately not echoed back.</b> {@code getMostSpecificCause()} carries
     * the table, column and constraint names plus the offending value — a free description of the
     * schema for anyone probing the API. It goes to the log, where support can find it by the
     * reference below; the client gets a generic sentence and that reference.
     *
     * <p>This is the safety net, not the fix. Anything reaching here should have been rejected
     * earlier by bean validation with a message naming the actual field.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
        String reference = newReference();
        log.error("Data integrity violation [ref={}]: {}", reference,
                ex.getMostSpecificCause().getMessage(), ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.businessError("DATA_CONSTRAINT_VIOLATION",
                        "The data could not be saved because it breaks a database constraint — "
                                + "a value may be too long, required, or already in use. "
                                + "Please review the form and try again.",
                        reference));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleAllExceptions(Exception ex) {
        String reference = newReference();
        log.error("Unexpected system error [ref={}]: {}", reference, ex.getMessage(), ex);
        return ResponseEntity.internalServerError()
                .body(ApiResponse.systemError(reference));
    }

    /**
     * Short correlation id tying the response the user sees to the stack trace in the log.
     *
     * <p>"Please contact your Admin" is a dead end for both sides otherwise: the user has nothing
     * to report and the Admin has nothing to search for. Six hex characters are enough to find one
     * entry in a day's log while staying short enough to read over the phone. Deliberately not a
     * full UUID — this is meant to be retyped by a human.
     */
    private static String newReference() {
        return UUID.randomUUID().toString().substring(0, 6);
    }
}
