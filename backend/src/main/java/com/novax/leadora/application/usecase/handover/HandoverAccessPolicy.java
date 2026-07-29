package com.novax.leadora.application.usecase.handover;

import com.novax.leadora.common.security.BaseAccessPolicy;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.OpHandoverEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

/**
 * Access policy for operational handovers (UC-20.x — the Sales/Reservation side).
 *
 * <p>Overrides the inherited scoping because {@link BaseAccessPolicy#SCOPED_ROLES} covers Sales
 * only, while a handover can legitimately be owned by <b>Reservation</b> too: both roles are on
 * the create/update endpoints. Inheriting the base sets verbatim would have denied Reservation
 * its own records.
 *
 * <p>Front Office is deliberately <b>not</b> here. It reads arrivals through
 * {@code /api/v1/arrival-handovers}, which applies its own filter (submitted only, live booking
 * only). Letting it in through the operational endpoints is exactly the bypass that exposed
 * DRAFT handovers the arrival screens take care to hide.
 */
@Component
public class HandoverAccessPolicy extends BaseAccessPolicy<OpHandoverEntity> {

    /** Roles that see every handover. */
    private static final Set<String> FULL_ACCESS = Set.of("MANAGER", "ADMIN");

    /** Roles that own handovers and therefore see only their own. */
    private static final Set<String> OWN_RECORDS_ONLY =
            Set.of("SALES", "SALES_STAFF", "RESERVATION", "RESERVATION_STAFF");

    public HandoverAccessPolicy(CurrentUserProvider currentUserProvider) {
        super(currentUserProvider);
    }

    /**
     * A handover belongs to whoever wrote it, and to the rep the booking is assigned to — a
     * colleague drafting the handover must not hide the arrival from the account's owner.
     */
    @Override
    protected boolean owns(UserEntity user, OpHandoverEntity handover) {
        UUID uid = user.getUserId();
        boolean createdByMe = handover.getCreatedBy() != null
                && uid.equals(handover.getCreatedBy().getUserId());
        boolean bookingAssignedToMe = handover.getBooking() != null
                && handover.getBooking().getAssignedUser() != null
                && uid.equals(handover.getBooking().getAssignedUser().getUserId());
        return createdByMe || bookingAssignedToMe;
    }

    @Override
    public UUID listScopeOwnerId(UserEntity user) {
        String role = roleName(user);
        if (FULL_ACCESS.contains(role)) {
            return null; // unscoped
        }
        if (OWN_RECORDS_ONLY.contains(role)) {
            return user.getUserId();
        }
        throw new AccessDeniedException("You do not have permission to view this resource.");
    }

    @Override
    public void assertCanView(UserEntity user, OpHandoverEntity handover) {
        String role = roleName(user);
        if (FULL_ACCESS.contains(role)) {
            return;
        }
        if (OWN_RECORDS_ONLY.contains(role) && owns(user, handover)) {
            return;
        }
        throw new AccessDeniedException("You do not have permission to access this resource.");
    }
}
