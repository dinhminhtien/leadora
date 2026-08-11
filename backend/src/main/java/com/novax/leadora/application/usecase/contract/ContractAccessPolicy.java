package com.novax.leadora.application.usecase.contract;

import com.novax.leadora.common.security.BaseAccessPolicy;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.ContractEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

@Component
public class ContractAccessPolicy extends BaseAccessPolicy<ContractEntity> {

    private static final Set<String> GLOBAL_ROLES = Set.of("MANAGER", "ADMIN", "RESERVATION", "RESERVATION_STAFF");

    public ContractAccessPolicy(CurrentUserProvider currentUserProvider) {
        super(currentUserProvider);
    }

    @Override
    public UUID listScopeOwnerId(UserEntity user) {
        String role = roleName(user);
        if (GLOBAL_ROLES.contains(role)) {
            return null; // unscoped / full access
        }
        return super.listScopeOwnerId(user);
    }

    @Override
    public void assertCanView(UserEntity user, ContractEntity contract) {
        String role = roleName(user);
        if (GLOBAL_ROLES.contains(role)) {
            return;
        }
        super.assertCanView(user, contract);
    }

    @Override
    protected boolean owns(UserEntity user, ContractEntity contract) {
        return contract.getCreatedBy() != null
                && user.getUserId().equals(contract.getCreatedBy().getUserId());
    }
}
