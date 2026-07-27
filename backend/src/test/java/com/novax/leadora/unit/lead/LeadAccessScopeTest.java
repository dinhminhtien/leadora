package com.novax.leadora.unit.lead;

import com.novax.leadora.application.usecase.lead.LeadAccessPolicy;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.LeadEntity;
import com.novax.leadora.infrastructure.persistence.entity.RoleEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * BR-02 — who may see which lead.
 *
 * <p>This is the highest-consequence rule in the module and it had no test at all: nothing would
 * have caught a change that let one sales rep read another's leads, and the failure is silent from
 * the outside — the list simply returns more rows than it should.
 *
 * <p>The policy is exercised directly rather than through the use case. It is the single place both
 * the list query and every single-record read go through, so pinning it here covers view, search,
 * filter, update and convert at once, without a Spring context.
 */
class LeadAccessScopeTest {

    private final LeadAccessPolicy policy = new LeadAccessPolicy(mock(CurrentUserProvider.class));

    private static UserEntity user(String roleName) {
        return UserEntity.builder()
                .userId(UUID.randomUUID())
                .fullName(roleName + " user")
                .role(RoleEntity.builder().roleName(roleName).build())
                .build();
    }

    private static LeadEntity leadOf(UserEntity assignee, UserEntity creator) {
        return LeadEntity.builder()
                .leadId(UUID.randomUUID())
                .fullName("Some Lead")
                .assignedUser(assignee)
                .createdBy(creator)
                .build();
    }

    // ── List scope ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("a sales rep's list query is pinned to their own user id")
    void salesListIsScopedToSelf() {
        UserEntity sales = user("SALES");

        assertThat(policy.listScopeOwnerId(sales)).isEqualTo(sales.getUserId());
    }

    @Test
    @DisplayName("manager and admin query unscoped")
    void managerAndAdminAreUnscoped() {
        assertThat(policy.listScopeOwnerId(user("MANAGER"))).isNull();
        assertThat(policy.listScopeOwnerId(user("ADMIN"))).isNull();
    }

    @Test
    @DisplayName("a role with no lead access is refused outright, not silently given an empty list")
    void unrelatedRolesAreRejected() {
        // An empty result would read as "you have no leads" and hide the missing permission.
        assertThatThrownBy(() -> policy.listScopeOwnerId(user("FRONT_OFFICE")))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> policy.listScopeOwnerId(user("RESERVATION_STAFF")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("role names are matched case- and whitespace-insensitively")
    void roleNameIsNormalised() {
        UserEntity sales = UserEntity.builder()
                .userId(UUID.randomUUID())
                .role(RoleEntity.builder().roleName("  sales  ").build())
                .build();

        assertThat(policy.listScopeOwnerId(sales)).isEqualTo(sales.getUserId());
    }

    // ── Single-record access ─────────────────────────────────────────────────

    @Test
    @DisplayName("a sales rep may open a lead assigned to them")
    void salesCanViewOwnAssignedLead() {
        UserEntity sales = user("SALES");

        policy.assertCanView(sales, leadOf(sales, user("MANAGER")));
    }

    @Test
    @DisplayName("a sales rep may open a lead they created but do not own")
    void salesCanViewLeadTheyCreated() {
        UserEntity sales = user("SALES");

        policy.assertCanView(sales, leadOf(null, sales));
    }

    @Test
    @DisplayName("a sales rep may NOT open a colleague's lead")
    void salesCannotViewAnotherRepsLead() {
        UserEntity mine = user("SALES");
        UserEntity theirs = user("SALES");

        assertThatThrownBy(() -> policy.assertCanView(mine, leadOf(theirs, theirs)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("an unassigned draft belongs to nobody but its creator")
    void salesCannotViewUnassignedLeadCreatedByAnother() {
        UserEntity mine = user("SALES");
        UserEntity manager = user("MANAGER");

        assertThatThrownBy(() -> policy.assertCanView(mine, leadOf(null, manager)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("a manager may open any lead")
    void managerCanViewAnyLead() {
        UserEntity other = user("SALES");

        policy.assertCanView(user("MANAGER"), leadOf(other, other));
    }

    @Test
    @DisplayName("a front-office user may not open a lead even when it names them")
    void wrongRoleIsRefusedEvenWhenItOwnsTheRecord() {
        // Ownership is only consulted for roles that are scoped in the first place — otherwise a
        // stray assignment would hand lead access to a role that should never have it.
        UserEntity frontOffice = user("FRONT_OFFICE");

        assertThatThrownBy(() -> policy.assertCanView(frontOffice, leadOf(frontOffice, frontOffice)))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ── Privileged actions ───────────────────────────────────────────────────

    @Test
    @DisplayName("only manager and admin hold full access")
    void fullAccessIsManagerAndAdminOnly() {
        policy.assertFullAccess(user("MANAGER"));
        policy.assertFullAccess(user("ADMIN"));

        assertThatThrownBy(() -> policy.assertFullAccess(user("SALES")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("a user with no role at all is refused")
    void missingRoleIsRefused() {
        UserEntity roleless = UserEntity.builder().userId(UUID.randomUUID()).build();

        assertThatThrownBy(() -> policy.listScopeOwnerId(roleless))
                .isInstanceOf(AccessDeniedException.class);
    }
}
