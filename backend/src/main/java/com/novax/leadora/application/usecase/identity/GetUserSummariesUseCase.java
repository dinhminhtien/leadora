package com.novax.leadora.application.usecase.identity;

import com.novax.leadora.api.dto.response.UserSummaryResponse;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * Lightweight, high-performance cached roster of active users for dropdowns across the CRM.
 */
@Service
@RequiredArgsConstructor
public class GetUserSummariesUseCase {

    private final UserRepository userRepository;

    @Cacheable(value = "user-summaries", key = "#role != null ? #role.trim().toUpperCase() : 'ALL'", unless = "#result == null")
    @Transactional(readOnly = true)
    public List<UserSummaryResponse> execute(String role) {
        String wanted = role != null ? role.trim().toUpperCase() : null;
        return userRepository.findAllWithRole()
                .stream()
                .filter(u -> wanted == null || wanted.isEmpty() || matchesRole(u, wanted))
                .map(UserSummaryResponse::from)
                .toList();
    }

    /** {@code FO} and {@code FRONT_OFFICE} are the same desk under two spellings. */
    private boolean matchesRole(UserEntity user, String wanted) {
        if (user.getRole() == null || user.getRole().getRoleName() == null) {
            return false;
        }
        String actual = user.getRole().getRoleName().trim().toUpperCase();
        if (actual.equals(wanted)) {
            return true;
        }
        return Set.of("FO", "FRONT_OFFICE").containsAll(Set.of(actual, wanted));
    }
}
