package com.novax.leadora.common.security;

import com.novax.leadora.application.usecase.identity.EffectivePermissionsService;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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
 * <p>Resolved per request from the database, so an Admin's permission change takes effect on the
 * caller's very next call — no re-login, no token refresh, no cache to invalidate (UC-6.4 POST-2).
 */
@Component
@RequiredArgsConstructor
public class JwtAuthoritiesResolver {

    private static final GrantedAuthority FALLBACK = new SimpleGrantedAuthority("ROLE_authenticated");

    private final UserRepository userRepository;
    private final EffectivePermissionsService effectivePermissionsService;

    public Collection<GrantedAuthority> resolve(String email) {
        if (!StringUtils.hasText(email)) {
            return List.of(FALLBACK);
        }
        return userRepository.findWithRoleByEmailIgnoreCase(email)
                .filter(user -> user.getRole() != null)
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
