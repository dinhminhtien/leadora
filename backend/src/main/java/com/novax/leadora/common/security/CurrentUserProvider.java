package com.novax.leadora.common.security;

import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * Resolves the acting user for requests.
 *
 * <p>Server-side JWT auth is not wired yet (the login feature is still in progress), so the
 * AI chat assistant identifies the actor with a best-effort strategy:
 * <ol>
 *   <li>the {@code X-User-Id} request header, if it is a valid existing user;</li>
 *   <li>otherwise the {@code AI_CHAT_DEV_USER_ID} configured in {@code .env};</li>
 *   <li>otherwise the first user in the database (single-tenant dev convenience).</li>
 * </ol>
 *
 * <p>When real authentication lands, replace this with the authenticated principal — the
 * use cases only depend on {@link #resolve(String)} returning a {@link UserEntity}.
 */
@Component
@RequiredArgsConstructor
public class CurrentUserProvider {

    private final UserRepository userRepository;

    @Value("${AI_CHAT_DEV_USER_ID:}")
    private String devUserId;

    public UserEntity resolve(String headerUserId) {
        if (StringUtils.hasText(headerUserId)) {
            UserEntity user = tryLoad(headerUserId);
            if (user != null) {
                return user;
            }
        }

        if (StringUtils.hasText(devUserId)) {
            UserEntity user = tryLoad(devUserId);
            if (user != null) {
                return user;
            }
        }

        return userRepository.findAll(PageRequest.of(0, 1)).stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
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
