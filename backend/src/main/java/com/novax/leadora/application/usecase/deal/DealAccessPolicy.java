package com.novax.leadora.application.usecase.deal;

import com.novax.leadora.common.security.BaseAccessPolicy;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.DealEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Access policy for deals (UC-12.x).
 * Extends BaseAccessPolicy to inherit global role-scoping and visibility checks.
 */
@Component
public class DealAccessPolicy extends BaseAccessPolicy<DealEntity> {

    public DealAccessPolicy(CurrentUserProvider currentUserProvider) {
        super(currentUserProvider);
    }

    @Override
    protected boolean owns(UserEntity user, DealEntity deal) {
        UUID uid = user.getUserId();
        boolean assignedToMe = deal.getAssignedUser() != null
                && uid.equals(deal.getAssignedUser().getUserId());
        boolean createdByMe = deal.getCreatedBy() != null
                && uid.equals(deal.getCreatedBy().getUserId());
        return assignedToMe || createdByMe;
    }
}
