package com.novax.leadora.common.security;

import com.novax.leadora.application.usecase.identity.EffectivePermissionsService;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.UserStatus;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Builds the authorities for an authenticated request from the application's own tables — the
 * Supabase JWT only carries an email (its {@code role} claim is always "authenticated").
 *
 * <p>Two kinds of authority are granted:
 * <ul>
 *   <li>{@code ROLE_<ROLENAME>} — the coarse job role, used by {@code hasRole(...)}.</li>
 *   <li>the bare permission codes ({@code LEAD_VIEW}, …) that the role currently holds, used by
 *       {@code hasAuthority(...)}. This is what makes UC-6.4 real: revoking a permission stops the
 *       API from serving the data, not just the sidebar from showing the link (BR-02).</li>
 * </ul>
 *
 * <p>Backed by a fast in-memory L1 Caffeine cache (5 min TTL) to avoid hitting PostgreSQL on
 * every single HTTP request. Can be invalidated immediately via {@link #invalidate(String)}
 * or {@link #invalidateAll()}.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthoritiesResolver {

    private static final GrantedAuthority FALLBACK = new SimpleGrantedAuthority("ROLE_authenticated");

    private final UserRepository userRepository;
    private final EffectivePermissionsService effectivePermissionsService;

    private final Cache<String, Collection<GrantedAuthority>> authorityCache = Caffeine.newBuilder()
            .maximumSize(2000)
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();

    public Collection<GrantedAuthority> resolve(String email) {
        if (!StringUtils.hasText(email)) {
            return List.of(FALLBACK);
        }
        String key = email.trim().toLowerCase();
        return authorityCache.get(key, this::loadFromDatabase);
    }

    public void invalidate(String email) {
        if (StringUtils.hasText(email)) {
            authorityCache.invalidate(email.trim().toLowerCase());
        }
    }

    public void invalidateAll() {
        authorityCache.invalidateAll();
    }

    private Collection<GrantedAuthority> loadFromDatabase(String normalizedEmail) {
        return userRepository.findWithRoleByEmailIgnoreCase(normalizedEmail)
                .filter(user -> user.getRole() != null)
                // BR-04 — a locked account gets no authorities at all, so its still-valid token
                // fails every @PreAuthorize. Relying on the use cases to reject it is not enough:
                // an endpoint that never resolves the caller (a plain catalogue read, say) would
                // happily serve a locked user. INACTIVE deliberately passes — it is the reversible
                // "dormant" state set by the idle-account job, not a suspension.
                .filter(user -> user.getStatus() != UserStatus.LOCKED)
                .map(this::authoritiesFor)
                .orElseGet(() -> List.of(FALLBACK));
    }

    private Collection<GrantedAuthority> authoritiesFor(UserEntity user) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().getRoleName().trim().toUpperCase()));
        for (String permissionCode : effectivePermissionsService.forUser(user)) {
            authorities.add(new SimpleGrantedAuthority(permissionCode));
        }
        return authorities;
    }
}
