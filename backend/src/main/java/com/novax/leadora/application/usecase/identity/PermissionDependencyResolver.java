package com.novax.leadora.application.usecase.identity;

import com.novax.leadora.infrastructure.persistence.repository.PermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Server-side integrity for a requested permission set (UC-6.4).
 *
 * <p>A WRITE/APPROVE permission cannot be held without its prerequisite VIEW — you cannot edit what
 * you cannot see. Any permission whose {@code depends_on} is absent is dropped, repeatedly until the
 * set is stable (a chain of dependencies collapses in one call). The UI applies the same rule while
 * toggling; this is the copy that matters, because it also holds when a client posts an
 * inconsistent set directly to the API.
 */
@Component
@RequiredArgsConstructor
public class PermissionDependencyResolver {

    private final PermissionRepository permissionRepository;

    /** Returns a new set containing only permissions whose full prerequisite chain is present. */
    @Transactional(readOnly = true)
    public Set<Integer> prune(Set<Integer> requested) {
        Set<Integer> resolved = new HashSet<>(requested);

        Map<Integer, Integer> dependsOn = new HashMap<>();
        permissionRepository.findAll()
                .forEach(permission -> dependsOn.put(permission.getPermissionId(), permission.getDependsOnId()));

        boolean changed = true;
        while (changed) {
            changed = resolved.removeIf(id -> {
                Integer prerequisite = dependsOn.get(id);
                return prerequisite != null && !resolved.contains(prerequisite);
            });
        }
        return resolved;
    }
}
