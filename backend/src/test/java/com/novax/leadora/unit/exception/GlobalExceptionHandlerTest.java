package com.novax.leadora.unit.exception;

import com.novax.leadora.api.exception.GlobalExceptionHandler;
import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.security.audit.SecurityAuditLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;
    private SecurityAuditLogger securityAuditLogger;
    private CurrentUserProvider currentUserProvider;

    @BeforeEach
    void setUp() {
        securityAuditLogger = mock(SecurityAuditLogger.class);
        currentUserProvider = mock(CurrentUserProvider.class);
        exceptionHandler = new GlobalExceptionHandler(securityAuditLogger, currentUserProvider);
    }

    @Test
    void testHandleNotFound() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Lead", 123L);
        ResponseEntity<ProblemDetail> response = exceptionHandler.handleNotFound(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        ProblemDetail body = response.getBody();
        assertNotNull(body);
        assertEquals("Not Found", body.getTitle());
        assertEquals("Lead not found with id: 123", body.getDetail());
        assertEquals("RESOURCE_NOT_FOUND", body.getProperties().get("errorCode"));
        assertEquals(false, body.getProperties().get("success"));
    }

    @Test
    void testHandleValidationException() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("Lead", "email", "must be a valid email");

        when(bindingResult.getFieldErrors()).thenReturn(Collections.singletonList(fieldError));
        when(ex.getBindingResult()).thenReturn(bindingResult);

        ResponseEntity<ProblemDetail> response = exceptionHandler.handleValidationException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ProblemDetail body = response.getBody();
        assertNotNull(body);
        assertEquals("Bad Request", body.getTitle());
        assertEquals("VALIDATION_ERROR", body.getProperties().get("errorCode"));

        java.util.Map<?, ?> errors = (java.util.Map<?, ?>) body.getProperties().get("errors");
        assertNotNull(errors);
        assertEquals("must be a valid email", errors.get("email"));
    }

    @Test
    void testHandleAccessDenied() {
        AccessDeniedException ex = new AccessDeniedException("Access is denied");
        HttpServletRequest request = mock(HttpServletRequest.class);

        ResponseEntity<ProblemDetail> response = exceptionHandler.handleAccessDenied(ex, request);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        ProblemDetail body = response.getBody();
        assertNotNull(body);
        assertEquals("Forbidden", body.getTitle());
        assertEquals("You do not have permission to access this function.", body.getDetail());
        assertEquals("ACCESS_DENIED", body.getProperties().get("errorCode"));

        verify(securityAuditLogger).logAccessDeniedForUser(eq(request), eq(ex), any(), any());
    }

    @Test
    void testHandleBusinessException() {
        BusinessException ex = new BusinessException("LEAD_ALREADY_CONVERTED", "This lead is already converted",
                "detail info", HttpStatus.CONFLICT);
        HttpServletRequest request = mock(HttpServletRequest.class);

        ResponseEntity<ProblemDetail> response = exceptionHandler.handleBusinessException(ex, request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        ProblemDetail body = response.getBody();
        assertNotNull(body);
        assertEquals("This lead is already converted", body.getDetail());
        assertEquals("LEAD_ALREADY_CONVERTED", body.getProperties().get("errorCode"));
        assertEquals("detail info", body.getProperties().get("details"));
    }
}
