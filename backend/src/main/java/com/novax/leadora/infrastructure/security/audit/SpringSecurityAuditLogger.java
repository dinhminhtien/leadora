package com.novax.leadora.infrastructure.security.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.novax.leadora.application.usecase.activitylog.ActivityLogCommand;
import com.novax.leadora.application.usecase.activitylog.ActivityLogPublisher;
import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.infrastructure.persistence.entity.enums.ActivityLogType;
import com.novax.leadora.infrastructure.persistence.entity.enums.ActorType;
import com.novax.leadora.infrastructure.persistence.entity.enums.EntityType;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SpringSecurityAuditLogger implements SecurityAuditLogger {

    private final ActivityLogPublisher activityLogPublisher;
    private final ObjectMapper objectMapper;

    private static final UUID SYSTEM_UUID = new UUID(0L, 0L);

    private JsonNode buildSecurityPayload(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        try {
            String ipAddress = request.getHeader("X-Forwarded-For");
            if (ipAddress == null || ipAddress.isBlank()) {
                ipAddress = request.getRemoteAddr();
            }
            String userAgent = request.getHeader("User-Agent");

            ObjectNode node = objectMapper.createObjectNode();
            node.put("ipAddress", ipAddress != null ? ipAddress.trim() : "unknown");
            node.put("userAgent", userAgent != null ? userAgent.trim() : "unknown");
            return node;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void logInvalidTokenAccess(HttpServletRequest request, AuthenticationException authException) {
        String uri = request != null ? request.getRequestURI() : "unknown";
        String authHeader = request != null ? request.getHeader("Authorization") : null;
        String summary;
        if (authHeader == null || authHeader.isBlank()) {
            summary = "Access Denied: Missing Bearer token for protected URI: " + uri;
        } else {
            summary = "Access Denied: Invalid/expired token for protected URI: " + uri + ". Error: " + authException.getMessage();
        }

        activityLogPublisher.publish(ActivityLogCommand.builder()
                .actorType(ActorType.SYSTEM)
                .activityType(ActivityLogType.INVALID_TOKEN_ACCESS)
                .entityType(EntityType.USER)
                .entityId(SYSTEM_UUID)
                .summary(summary)
                .payload(buildSecurityPayload(request))
                .build());
    }

    @Override
    public void logAccessDenied(HttpServletRequest request, AccessDeniedException accessDeniedException) {
        String uri = request != null ? request.getRequestURI() : "unknown";
        UUID userId = SYSTEM_UUID;
        String userName = "Unauthenticated User";
        try {
            org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof org.springframework.security.oauth2.jwt.Jwt jwt) {
                String subject = jwt.getSubject();
                if (subject != null) {
                    userId = UUID.fromString(subject.trim());
                }
                String email = jwt.getClaimAsString("email");
                if (email != null) {
                    userName = email;
                }
            }
        } catch (Exception ignored) {}

        activityLogPublisher.publish(ActivityLogCommand.builder()
                .actorType(ActorType.SYSTEM)
                .activityType(ActivityLogType.ACCESS_DENIED_EVENT)
                .entityType(EntityType.USER)
                .entityId(userId)
                .summary("Access Denied: User " + userName + " has insufficient permission to access protected URI: " + uri)
                .payload(buildSecurityPayload(request))
                .build());
    }

    @Override
    public void logAccessDeniedForUser(HttpServletRequest request, AccessDeniedException ex, UUID userId, String userName) {
        String uri = request != null ? request.getRequestURI() : "unknown";
        activityLogPublisher.publish(ActivityLogCommand.builder()
                .actorType(ActorType.SYSTEM)
                .activityType(ActivityLogType.ACCESS_DENIED_EVENT)
                .entityType(EntityType.USER)
                .entityId(userId != null ? userId : SYSTEM_UUID)
                .summary("Access Denied for user " + userName + " attempting to access URI: " + uri + ". Reason: " + ex.getMessage())
                .payload(buildSecurityPayload(request))
                .build());
    }

    @Override
    public void logUnprovisionedAccount(HttpServletRequest request, BusinessException ex) {
        String uri = request != null ? request.getRequestURI() : "unknown";
        activityLogPublisher.publish(ActivityLogCommand.builder()
                .actorType(ActorType.SYSTEM)
                .activityType(ActivityLogType.INVALID_TOKEN_ACCESS)
                .entityType(EntityType.USER)
                .entityId(SYSTEM_UUID)
                .summary("Access Denied: Unprovisioned account attempt at URI: " + uri + ". Reason: " + ex.getMessage())
                .payload(buildSecurityPayload(request))
                .build());
    }

    @Override
    public void logLockedAccount(HttpServletRequest request, BusinessException ex) {
        String uri = request != null ? request.getRequestURI() : "unknown";
        activityLogPublisher.publish(ActivityLogCommand.builder()
                .actorType(ActorType.SYSTEM)
                .activityType(ActivityLogType.ACCESS_DENIED_EVENT)
                .entityType(EntityType.USER)
                .entityId(SYSTEM_UUID)
                .summary("Access Denied: Locked account login attempt at URI: " + uri + ". Reason: " + ex.getMessage())
                .payload(buildSecurityPayload(request))
                .build());
    }
}
