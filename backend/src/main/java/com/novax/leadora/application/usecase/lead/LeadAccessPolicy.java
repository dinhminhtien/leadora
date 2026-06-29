package com.novax.leadora.application.usecase.lead;

import com.novax.leadora.common.security.BaseAccessPolicy;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.LeadEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Access policy for leads (UC-8.x).
 * Extends BaseAccessPolicy to inherit global role-scoping and visibility checks.
 */
@Component
public class LeadAccessPolicy extends BaseAccessPolicy<LeadEntity> {

    public LeadAccessPolicy(CurrentUserProvider currentUserProvider) {
        super(currentUserProvider);
    }

    @Override
    protected boolean owns(UserEntity user, LeadEntity lead) {
        UUID uid = user.getUserId();
        boolean assignedToMe = lead.getAssignedUser() != null
                && uid.equals(lead.getAssignedUser().getUserId());
        boolean createdByMe = lead.getCreatedBy() != null
                && uid.equals(lead.getCreatedBy().getUserId());
        return assignedToMe || createdByMe;
    }
}
