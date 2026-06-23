package com.novax.leadora.application.usecase.identity;

import com.novax.leadora.api.dto.response.UserAccountResponse;
import com.novax.leadora.infrastructure.persistence.entity.enums.UserStatus;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.specification.UserSpecification;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import org.springframework.data.jpa.domain.Specification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Set;

/**
 * UC-6.1 — View User Accounts.
 * Paged list with free-text search (name/email), role + status filters, and whitelisted sorting.
 */
@Service
@RequiredArgsConstructor
public class GetUserListUseCase {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("createdAt", "fullName", "email", "status");

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Page<UserAccountResponse> execute(String search, Integer roleId, String status,
                                             String sortBy, String sortDir, int page, int size) {
        Specification<UserEntity> spec = (root, query, cb) -> cb.conjunction();

        if (StringUtils.hasText(search)) {
            spec = spec.and(UserSpecification.search(search));
        }

        if (roleId != null) {
            spec = spec.and(UserSpecification.hasRoleId(roleId));
        }

        if (StringUtils.hasText(status)) {
            try {
                UserStatus statusParam = UserStatus.valueOf(status.trim().toUpperCase());
                spec = spec.and(UserSpecification.hasStatus(statusParam));
            } catch (IllegalArgumentException ignored) {
                // unknown status → treat as no filter
            }
        }

        String sortField = ALLOWED_SORT_FIELDS.contains(sortBy) ? sortBy : "createdAt";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortField));

        return userRepository.findAll(spec, pageable)
                .map(UserAccountResponse::from);
    }
}
