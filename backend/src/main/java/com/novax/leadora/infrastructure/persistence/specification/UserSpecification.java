package com.novax.leadora.infrastructure.persistence.specification;

import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.UserStatus;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

public final class UserSpecification {

    private UserSpecification() {
    }

    public static Specification<UserEntity> search(String keyword) {
        if (!StringUtils.hasText(keyword))
            return null;
        String pattern = "%" + keyword.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("fullName")), pattern),
                cb.like(cb.lower(root.get("email")), pattern)
        );
    }

    public static Specification<UserEntity> hasRoleId(Integer roleId) {
        if (roleId == null)
            return null;
        return (root, query, cb) -> cb.equal(root.get("role").get("roleId"), roleId);
    }

    public static Specification<UserEntity> hasStatus(UserStatus status) {
        if (status == null)
            return null;
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }
}
