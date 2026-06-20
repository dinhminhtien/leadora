package com.novax.leadora.application.usecase.identity;

import com.novax.leadora.api.dto.response.UserAccountResponse;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** UC-6.1 — View one user account (Account Detail). */
@Service
@RequiredArgsConstructor
public class GetUserDetailUseCase {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserAccountResponse execute(UUID userId) {
        return userRepository.findWithRoleByUserId(userId)
                .map(UserAccountResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }
}
