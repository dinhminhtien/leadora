package com.novax.leadora.infrastructure.security.audit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import com.novax.leadora.common.exception.BusinessException;
import java.util.UUID;

public interface SecurityAuditLogger {

    void logInvalidTokenAccess(HttpServletRequest request, AuthenticationException authException);

    void logAccessDenied(HttpServletRequest request, AccessDeniedException accessDeniedException);

    void logAccessDeniedForUser(HttpServletRequest request, AccessDeniedException ex, UUID userId, String userName);

    void logUnprovisionedAccount(HttpServletRequest request, BusinessException ex);

    void logLockedAccount(HttpServletRequest request, BusinessException ex);
}
