package com.novax.leadora.application.usecase.payment;

import com.novax.leadora.common.security.BaseAccessPolicy;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.PaymentEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

@Component
public class PaymentAccessPolicy extends BaseAccessPolicy<PaymentEntity> {

    private static final Set<String> GLOBAL_ROLES = Set.of("MANAGER", "ADMIN", "RESERVATION", "RESERVATION_STAFF", "FRONT_OFFICE", "FO");
    private static final Set<String> SCOPED_ROLES = Set.of("SALES", "SALES_STAFF");

    public PaymentAccessPolicy(CurrentUserProvider currentUserProvider) {
        super(currentUserProvider);
    }

    @Override
    protected boolean owns(UserEntity user, PaymentEntity payment) {
        return payment.getBooking() != null 
                && payment.getBooking().getAssignedUser() != null 
                && user.getUserId().equals(payment.getBooking().getAssignedUser().getUserId());
    }

    @Override
    public UUID listScopeOwnerId(UserEntity user) {
        String role = roleName(user);
        if (GLOBAL_ROLES.contains(role)) {
            return null; // view all
        }
        if (SCOPED_ROLES.contains(role)) {
            return user.getUserId();
        }
        throw new AccessDeniedException("You do not have permission to access payment data.");
    }

    @Override
    public void assertCanView(UserEntity user, PaymentEntity payment) {
        String role = roleName(user);
        if (GLOBAL_ROLES.contains(role)) {
            return;
        }
        if (SCOPED_ROLES.contains(role) && owns(user, payment)) {
            return;
        }
        throw new AccessDeniedException("You do not have permission to access this payment.");
    }
}
