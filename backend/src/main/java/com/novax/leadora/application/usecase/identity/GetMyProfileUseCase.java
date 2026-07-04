package com.novax.leadora.application.usecase.identity;

import com.novax.leadora.api.dto.response.ProfileResponse;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-5.1 — View Profile.
 * Resolves the currently authenticated user from the security context and returns
 * their full profile. Read-only; no state mutation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GetMyProfileUseCase {

    private final CurrentUserProvider currentUserProvider;

    @Transactional(readOnly = true)
    public ProfileResponse execute(String headerUserId) {
        UserEntity user = currentUserProvider.resolve(headerUserId);
        log.debug("Profile requested for user: {}", user.getEmail());
        return ProfileResponse.from(user);
    }
}
