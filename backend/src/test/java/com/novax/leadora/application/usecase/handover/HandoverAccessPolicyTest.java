package com.novax.leadora.application.usecase.handover;

import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.BookingEntity;
import com.novax.leadora.infrastructure.persistence.entity.OpHandoverEntity;
import com.novax.leadora.infrastructure.persistence.entity.RoleEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * BR-01 / BR-02 — who may read which operational handover.
 *
 * <p>The read endpoints used to apply no scoping at all: every Sales rep could list and open every
 * other rep's handovers, and Front Office could reach DRAFT records through them.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HandoverAccessPolicyTest {

    @Mock
    private CurrentUserProvider currentUserProvider;

    private HandoverAccessPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new HandoverAccessPolicy(currentUserProvider);
    }

    private UserEntity user(String role) {
        return UserEntity.builder()
                .userId(UUID.randomUUID())
                .role(RoleEntity.builder().roleName(role).build())
                .build();
    }

    /** A handover written by {@code creator}, on a booking assigned to {@code assignee}. */
    private OpHandoverEntity handover(UserEntity creator, UserEntity assignee) {
        return OpHandoverEntity.builder()
                .handoverId(UUID.randomUUID())
                .createdBy(creator)
                .booking(BookingEntity.builder()
                        .bookingId(UUID.randomUUID())
                        .assignedUser(assignee)
                        .build())
                .build();
    }

    // ------------------------------------------------------------------ list scoping

    @ParameterizedTest(name = "{0} lists every handover")
    @ValueSource(strings = {"MANAGER", "ADMIN", "manager", " admin "})
    void fullAccessRolesAreUnscoped(String role) {
        assertThat(policy.listScopeOwnerId(user(role))).isNull();
    }

    @ParameterizedTest(name = "{0} is scoped to its own handovers")
    @ValueSource(strings = {"SALES", "SALES_STAFF", "RESERVATION", "RESERVATION_STAFF"})
    void owningRolesAreScopedToThemselves(String role) {
        UserEntity u = user(role);
        assertThat(policy.listScopeOwnerId(u)).isEqualTo(u.getUserId());
    }

    @ParameterizedTest(name = "{0} cannot list operational handovers at all")
    @ValueSource(strings = {"FO", "FRONT_OFFICE", "SOMETHING_NEW", ""})
    @DisplayName("Front Office has no business on the operational endpoints — it has its own")
    void otherRolesAreDenied(String role) {
        assertThatThrownBy(() -> policy.listScopeOwnerId(user(role)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void aUserWithNoRoleIsDenied() {
        UserEntity roleless = UserEntity.builder().userId(UUID.randomUUID()).build();
        assertThatThrownBy(() -> policy.listScopeOwnerId(roleless))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ------------------------------------------------------------------ detail scoping

    @Test
    @DisplayName("A rep may open the handover they wrote")
    void ownerMayViewOwnRecord() {
        UserEntity rep = user("SALES");
        assertThatCode(() -> policy.assertCanView(rep, handover(rep, null)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("A rep may open a handover a colleague drafted on their own booking")
    void bookingAssigneeMayViewRecordCreatedBySomeoneElse() {
        UserEntity rep = user("SALES");
        UserEntity colleague = user("SALES");
        assertThatCode(() -> policy.assertCanView(rep, handover(colleague, rep)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("A rep may NOT open somebody else's handover — this was the leak")
    void repMayNotViewAnotherRepsRecord() {
        UserEntity rep = user("SALES");
        UserEntity other = user("SALES");
        assertThatThrownBy(() -> policy.assertCanView(rep, handover(other, other)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Reservation owns handovers too — BaseAccessPolicy's Sales-only set would deny it")
    void reservationMayViewOwnRecord() {
        UserEntity reservation = user("RESERVATION");
        assertThatCode(() -> policy.assertCanView(reservation, handover(reservation, null)))
                .doesNotThrowAnyException();
    }

    @Test
    void managerMayViewAnybodysRecord() {
        UserEntity manager = user("MANAGER");
        UserEntity rep = user("SALES");
        assertThatCode(() -> policy.assertCanView(manager, handover(rep, rep)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Front Office cannot open an operational handover even if it is assigned the booking")
    void frontOfficeIsDeniedOnDetail() {
        UserEntity fo = user("FO");
        assertThatThrownBy(() -> policy.assertCanView(fo, handover(fo, fo)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("A handover with neither creator nor assignee belongs to nobody but Manager/Admin")
    void orphanRecordIsNotOwnedByAnyRep() {
        UserEntity rep = user("SALES");
        assertThatThrownBy(() -> policy.assertCanView(rep, handover(null, null)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatCode(() -> policy.assertCanView(user("ADMIN"), handover(null, null)))
                .doesNotThrowAnyException();
    }
}
