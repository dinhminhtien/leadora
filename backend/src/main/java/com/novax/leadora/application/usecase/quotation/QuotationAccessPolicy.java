package com.novax.leadora.application.usecase.quotation;

import com.novax.leadora.common.security.BaseAccessPolicy;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import org.springframework.stereotype.Component;

/**
 * Access policy for quotations (UC-14.x).
 * Extends BaseAccessPolicy to inherit global role-scoping and visibility checks.
 */
@Component
public class QuotationAccessPolicy extends BaseAccessPolicy<QuotationEntity> {

    public QuotationAccessPolicy(CurrentUserProvider currentUserProvider) {
        super(currentUserProvider);
    }

    @Override
    protected boolean owns(UserEntity user, QuotationEntity quotation) {
        // Quotation has no assignedUser field — ownership is the creator only.
        return quotation.getCreatedBy() != null
                && user.getUserId().equals(quotation.getCreatedBy().getUserId());
    }
}
