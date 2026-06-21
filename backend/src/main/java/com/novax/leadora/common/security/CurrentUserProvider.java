package com.novax.leadora.common.security;

import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * Resolves the acting user for requests.
 *
 * <p>Updated to support full server-side JWT authentication, falling back to the dev credentials
 * and header bypass for local environment compatibility.
 */
@Component
@RequiredArgsConstructor
public class CurrentUserProvider {

    private final UserRepository userRepository;

    @Value("${AI_CHAT_DEV_USER_ID:}")
    private String devUserId;

    public UserEntity resolve(String headerUserId) {
        // 1. Try to load from Security Context (Spring Security authenticated principal from JWT)
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof Jwt jwt) {
            String subject = jwt.getSubject();
            if (StringUtils.hasText(subject)) {
                UserEntity user = tryLoad(subject);
                if (user != null) {
                    return user;
                }
            }
        }

        // 2. Fall back to X-User-Id request header
        if (StringUtils.hasText(headerUserId)) {
            UserEntity user = tryLoad(headerUserId);
            if (user != null) {
                return user;
            }
        }

        // 3. Fall back to the devUserId env
        if (StringUtils.hasText(devUserId)) {
            UserEntity user = tryLoad(devUserId);
            if (user != null) {
                return user;
            }
        }

        // 4. Fall back to the first user in the database (single-tenant convenience)
        return userRepository.findAll(PageRequest.of(0, 1)).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No user available to act as the chat assistant actor. "
                                + "Seed a user or set AI_CHAT_DEV_USER_ID."));
    }

    private UserEntity tryLoad(String rawId) {
        try {
            return userRepository.findById(UUID.fromString(rawId.trim())).orElse(null);
        } catch (IllegalArgumentException ex) {
            return null; // not a UUID — ignore and fall through
        }
    }
}
