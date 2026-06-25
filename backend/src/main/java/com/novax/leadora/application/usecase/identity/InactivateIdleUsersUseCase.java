package com.novax.leadora.application.usecase.identity;

import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.UserStatus;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Marks ACTIVE accounts that have not signed in for more than {@value #IDLE_DAYS} days as INACTIVE.
 * Admin accounts are never auto-deactivated. INACTIVE is a reversible "dormant" state — the user is
 * reactivated automatically on their next successful login (see {@link LoginActivityService}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InactivateIdleUsersUseCase {

    private static final int IDLE_DAYS = 7;
    private static final String ADMIN_ROLE = "ADMIN";

    private final UserRepository userRepository;

    @Transactional
    public int execute() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(IDLE_DAYS);
        List<UserEntity> activeUsers = userRepository.findByStatus(UserStatus.ACTIVE);

        int inactivated = 0;
        for (UserEntity u : activeUsers) {
            if (u.getRole() != null && ADMIN_ROLE.equalsIgnoreCase(u.getRole().getRoleName())) {
                continue; // never auto-deactivate Admins
            }
            // Fall back to createdAt for accounts that have never logged in.
            OffsetDateTime lastSeen = u.getLastLoginAt() != null ? u.getLastLoginAt() : u.getCreatedAt();
            if (lastSeen != null && lastSeen.isBefore(cutoff)) {
                u.setStatus(UserStatus.INACTIVE);
                inactivated++;
            }
        }
        if (inactivated > 0) {
            log.info("Idle-user job: {} account(s) moved ACTIVE → INACTIVE (idle > {} days).", inactivated, IDLE_DAYS);
        }
        return inactivated;
    }
}
