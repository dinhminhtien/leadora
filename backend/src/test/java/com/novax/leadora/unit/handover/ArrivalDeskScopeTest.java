package com.novax.leadora.unit.handover;
import com.novax.leadora.application.usecase.handover.*;

import com.novax.leadora.infrastructure.persistence.entity.RoleEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.access.AccessDeniedException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UC-22.1 step 3 / step 5 — what a Front Office Staff sees on the arrival desk, and who may
 * narrow the list to a named colleague.
 */
class ArrivalDeskScopeTest {

    private ArrivalDeskScope scope;

    @BeforeEach
    void setUp() {
        scope = new ArrivalDeskScope();
    }

    private UserEntity user(String role) {
        return UserEntity.builder()
                .userId(UUID.randomUUID())
                .role(RoleEntity.builder().roleName(role).build())
                .build();
    }

    @ParameterizedTest(name = "{0} is never scoped")
    @ValueSource(strings = {"MANAGER", "ADMIN", "manager", " admin "})
    void supervisorsSeeEveryArrival(String role) {
        assertThat(scope.scopeFor(user(role), false)).isNull();
        assertThat(scope.scopeFor(user(role), true)).isNull();
    }

    @ParameterizedTest(name = "{0} defaults to its own queue")
    @ValueSource(strings = {"FO", "FRONT_OFFICE", "fo"})
    void frontOfficeDefaultsToOwnQueue(String role) {
        UserEntity fo = user(role);
        assertThat(scope.scopeFor(fo, false)).isEqualTo(fo.getUserId());
    }

    @ParameterizedTest(name = "{0} can take the whole desk on request")
    @ValueSource(strings = {"FO", "FRONT_OFFICE"})
    @DisplayName("A shift rota needs desk-wide: the assignee may simply be off duty")
    void frontOfficeCanRequestTheWholeDesk(String role) {
        assertThat(scope.scopeFor(user(role), true)).isNull();
    }

    @Test
    @DisplayName("Only supervisors may filter by an arbitrary assignee (step 5)")
    void onlySupervisorsMayFilterByAssignee() {
        assertThat(scope.canFilterByAssignee(user("MANAGER"))).isTrue();
        assertThat(scope.canFilterByAssignee(user("ADMIN"))).isTrue();
        assertThat(scope.canFilterByAssignee(user("FO"))).isFalse();
        assertThat(scope.canFilterByAssignee(user("FRONT_OFFICE"))).isFalse();
        assertThat(scope.canFilterByAssignee(user("SALES"))).isFalse();
    }

    @Test
    @DisplayName("A user with no role is scoped, not waved through")
    void rolelessUserIsScoped() {
        UserEntity roleless = UserEntity.builder().userId(UUID.randomUUID()).build();

        assertThat(scope.scopeFor(roleless, false)).isEqualTo(roleless.getUserId());
        assertThat(scope.canFilterByAssignee(roleless)).isFalse();
    }

    @Test
    @DisplayName("An unknown caller is refused, not silently unscoped")
    void nullUserIsRefused() {
        // null is this method's value for "see everything", so returning it for an unidentified
        // caller would hand out the whole desk. It must fail closed.
        assertThatThrownBy(() -> scope.scopeFor(null, false))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> scope.scopeFor(null, true))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(scope.canFilterByAssignee(null)).isFalse();
    }
}
