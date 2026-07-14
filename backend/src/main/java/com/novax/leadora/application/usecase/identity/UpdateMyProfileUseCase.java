package com.novax.leadora.application.usecase.identity;

import com.novax.leadora.api.dto.request.UpdateProfileRequest;
import com.novax.leadora.api.dto.response.ProfileResponse;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * UC-5.2 — Update Profile.
 * Allows the authenticated user to modify their own editable fields (fullName, phone,
 * avatarUrl). Restricted fields (email, role, status) are intentionally excluded.
 * Passing null for phone or avatarUrl clears the field; omitting them (empty string) has
 * the same effect.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateMyProfileUseCase {

    private final CurrentUserProvider currentUserProvider;
    private final UserRepository userRepository;

    @Transactional
    public ProfileResponse execute(String headerUserId, UpdateProfileRequest request) {
        UserEntity user = currentUserProvider.resolve(headerUserId);

        // fullName is @NotBlank — always present and non-empty after bean validation
        user.setFullName(request.getFullName().trim());

        // null → preserve existing; blank string → clear the field
        if (request.getPhone() != null) {
            user.setPhone(StringUtils.hasText(request.getPhone()) ? request.getPhone().trim() : null);
        }
        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(StringUtils.hasText(request.getAvatarUrl()) ? request.getAvatarUrl().trim() : null);
        }

        UserEntity saved = userRepository.save(user);
        log.info("Profile updated for user: {}", saved.getEmail());
        return ProfileResponse.from(saved);
    }
}
