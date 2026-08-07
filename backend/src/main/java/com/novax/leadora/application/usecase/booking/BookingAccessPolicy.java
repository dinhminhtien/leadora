package com.novax.leadora.application.usecase.booking;

import com.novax.leadora.common.security.BaseAccessPolicy;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.BookingEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

@Component
public class BookingAccessPolicy extends BaseAccessPolicy<BookingEntity> {

    private static final Set<String> GLOBAL_ROLES = Set.of("MANAGER", "ADMIN", "RESERVATION", "RESERVATION_STAFF", "FRONT_OFFICE", "FO");
    private static final Set<String> SCOPED_ROLES = Set.of("SALES", "SALES_STAFF");

    public BookingAccessPolicy(CurrentUserProvider currentUserProvider) {
        super(currentUserProvider);
    }

    @Override
    protected boolean owns(UserEntity user, BookingEntity booking) {
        return booking.getAssignedUser() != null 
                && user.getUserId().equals(booking.getAssignedUser().getUserId());
    }

    @Override
    public UUID listScopeOwnerId(UserEntity user) {
        String role = roleName(user);
        if (GLOBAL_ROLES.contains(role)) {
            return null; // view all bookings
        }
        if (SCOPED_ROLES.contains(role)) {
            return user.getUserId(); // filter by assigned owner
        }
        throw new AccessDeniedException("You do not have permission to access booking data.");
    }

    @Override
    public void assertCanView(UserEntity user, BookingEntity booking) {
        String role = roleName(user);
        if (GLOBAL_ROLES.contains(role)) {
            return;
        }
        if (SCOPED_ROLES.contains(role) && owns(user, booking)) {
            return;
        }
        throw new AccessDeniedException("You do not have permission to access this booking.");
    }
}
