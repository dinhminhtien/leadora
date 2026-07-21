package com.novax.leadora.api.exception;

import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.common.exception.BusinessRuleException;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

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
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Malformed path/parameter '{}': {}", ex.getName(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.businessError("RESOURCE_NOT_FOUND",
                        "The requested resource does not exist.", null));
    }

    /**
     * Authorization failures raised in the service layer (e.g. a Sales Staff trying
     * to open a lead that is not theirs). Method-security denials are handled by the
     * security filter chain, but a manually thrown {@link AccessDeniedException} from
     * a use case reaches here — map it to 403 so the UI can show "Access Denied".
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.businessError("ACCESS_DENIED",
                        ex.getMessage() != null ? ex.getMessage()
                                : "You do not have permission to access this resource.", null));
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
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        log.warn("Business rule violation [{}]: {}", ex.getErrorCode(), ex.getMessage());
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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleAllExceptions(Exception ex) {
        log.error("Unexpected system error: {}", ex.getMessage(), ex);
        return ResponseEntity.internalServerError()
                .body(ApiResponse.systemError());
    }
}
