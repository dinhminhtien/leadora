package com.novax.leadora.common.security;

import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.UserStatus;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.UUID;

/**
 * Resolves the acting user for requests.
 *
 * <p>
 * Resolution order:
 * <ol>
 * <li>JWT subject (UUID) from Spring Security context</li>
 * <li>Email claim from the same verified JWT</li>
 * <li>X-User-Id request header</li>
 * <li>AI_CHAT_DEV_USER_ID env — <b>only in the "dev" Spring profile</b></li>
 * </ol>
 *
 * <p>
 * If none of the above resolves a user, an {@link AccessDeniedException} is
 * thrown
 * (→ HTTP 403). The old "first user in DB" fallback has been removed as it was
 * a
 * critical security hole allowing unauthenticated access.
 */
@Component
@RequiredArgsConstructor
public class CurrentUserProvider {

    private final UserRepository userRepository;

    @Value("${AI_CHAT_DEV_USER_ID:}")
    private String devUserId;

    @Value("${spring.profiles.active:}")
    private String activeProfiles;

    public UserEntity resolve(String headerUserId) {
        // 1. Try to load from Security Context (Spring Security authenticated principal
        // from JWT)
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof Jwt jwt) {
            String subject = jwt.getSubject();
            if (StringUtils.hasText(subject)) {
                UserEntity user = tryLoad(subject);
                if (user != null) {
                    return requireActiveUser(user);
                }
            }

            // OAuth / SSO: resolve by verified email — account must be pre-provisioned by Admin.
            String email = jwt.getClaimAsString("email");
            if (StringUtils.hasText(email)) {
                return userRepository.findWithRoleByEmailIgnoreCase(email.trim())
                        .map(this::requireActiveUser)
                        .orElseThrow(() -> new BusinessException(
                                "ACCOUNT_NOT_PROVISIONED",
                                "You do not have access to this system. Please contact your administrator.",
                                HttpStatus.FORBIDDEN));
            }
        }

        // 2. Fall back to X-User-Id request header
        if (StringUtils.hasText(headerUserId)) {
            UserEntity user = tryLoad(headerUserId);
            if (user != null) {
                return requireActiveUser(user);
            }
        }

        // 3. Dev-only fallback: AI_CHAT_DEV_USER_ID env variable.
        // Intentionally disabled in production to prevent unauthorized access.
        boolean isDevProfile = Arrays.stream(activeProfiles.split(","))
                .map(String::trim)
                .anyMatch("dev"::equalsIgnoreCase);

        if (isDevProfile && StringUtils.hasText(devUserId)) {
            UserEntity user = tryLoad(devUserId);
            if (user != null) {
                return requireActiveUser(user);
            }
        }

        // No authenticated user could be resolved — reject the request.
        throw new AccessDeniedException(
                "Could not resolve an authenticated user. Please provide a valid Bearer token.");
    }

    private UserEntity tryLoad(String rawId) {
        try {
            return userRepository.findWithRoleByUserId(UUID.fromString(rawId.trim())).orElse(null);
        } catch (IllegalArgumentException ex) {
            return null; // not a UUID — ignore and fall through
        }
    }

    private UserEntity requireActiveUser(UserEntity user) {
        // Only LOCKED accounts are blocked. INACTIVE is a reversible "dormant" state (from the
        // 7-day idle job) — it is allowed through and is reactivated to ACTIVE on explicit login
        // (see LoginActivityService). This keeps a dormant user from being bounced out of the app.
        if (user.getStatus() == UserStatus.LOCKED) {
            throw new BusinessException(
                    "ACCOUNT_LOCKED",
                    "Your account has been locked. Please contact the Admin for assistance.",
                    HttpStatus.FORBIDDEN);
        }
        return user;
    }
}
