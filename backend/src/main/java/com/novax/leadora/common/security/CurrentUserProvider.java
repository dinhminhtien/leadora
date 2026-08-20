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
import org.springframework.web.context.annotation.RequestScope;

import java.util.Arrays;
import java.util.UUID;

/**
 * Resolves the acting user for requests.
 *
 * <p>
 * Resolution order:
 * <ol>
 * <li>JWT subject (UUID) from Spring Security context</li>
 * <li>Email claim from the same verified JWT — if the JWT resolves to no account,
 * the request is rejected (no fall-through to spoofable fallbacks)</li>
 * <li>X-User-Id request header — <b>only in the "dev" Spring profile</b></li>
 * <li>AI_CHAT_DEV_USER_ID env — <b>only in the "dev" Spring profile</b></li>
 * </ol>
 *
 * <p>
 * If none of the above resolves a user, an {@link AccessDeniedException} is
 * thrown
 * (→ HTTP 403). The old "first user in DB" fallback has been removed as it was
 * a
 * critical security hole allowing unauthenticated access.
 *
 * <p>
 * Request-scoped: a single HTTP request commonly calls {@link #resolve} more than
 * once (e.g. a controller resolves the actor, then a use case resolves it again for
 * activity-log purposes), each time re-querying the DB for the same JWT principal.
 * This bean is instantiated fresh per request (Spring's request scope), so the
 * memoized result never survives past the request it was resolved in — unlike a
 * cross-request cache, it can't leak one request's entity into another's, which
 * matters because {@code UpdateMyProfileUseCase}/{@code ChangePasswordUseCase}
 * mutate the returned entity in place before saving it.
 */
@Component
@RequestScope
@RequiredArgsConstructor
public class CurrentUserProvider {

    private final UserRepository userRepository;

    @Value("${AI_CHAT_DEV_USER_ID:}")
    private String devUserId;

    @Value("${spring.profiles.active:}")
    private String activeProfiles;

    private UserEntity cachedUser;

    public UserEntity resolve(String headerUserId) {
        if (cachedUser != null) {
            return cachedUser;
        }
        UserEntity user = doResolve(headerUserId);
        cachedUser = user;
        return user;
    }

    private UserEntity doResolve(String headerUserId) {
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

            // A verified JWT that maps to no account must stop HERE. Falling through to the
            // client-supplied X-User-Id header would let any token holder impersonate an
            // arbitrary user simply by omitting the email claim.
            throw new BusinessException(
                    "ACCOUNT_NOT_PROVISIONED",
                    "You do not have access to this system. Please contact your administrator.",
                    HttpStatus.FORBIDDEN);
        }

        // Dev-only fallbacks (no JWT on the request — never reachable on protected routes in
        // production, where the security filter chain already requires a Bearer token):
        boolean isDevProfile = Arrays.stream(activeProfiles.split(","))
                .map(s -> s.trim())
                .anyMatch("dev"::equalsIgnoreCase);

        if (isDevProfile) {
            // 2. X-User-Id request header (local tooling / manual API testing).
            if (StringUtils.hasText(headerUserId)) {
                UserEntity user = tryLoad(headerUserId);
                if (user != null) {
                    return requireActiveUser(user);
                }
            }

            // 3. AI_CHAT_DEV_USER_ID env variable.
            if (StringUtils.hasText(devUserId)) {
                UserEntity user = tryLoad(devUserId);
                if (user != null) {
                    return requireActiveUser(user);
                }
            }
        }

        // No authenticated user could be resolved — reject the request.
        throw new AccessDeniedException(
                "Could not resolve an authenticated user. Please provide a valid Bearer token.");
    }

    /**
     * Same as {@link #resolve(String)} but returns {@code null} instead of throwing.
     *
     * <p>For audit-trail writers only: failing to name the actor must never turn a successful
     * business operation into an error response. The audit row is still written, with a null actor.
     */
    public UserEntity resolveQuietly() {
        try {
            return resolve(null);
        } catch (RuntimeException ignored) {
            return null;
        }
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
