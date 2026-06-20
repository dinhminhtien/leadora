package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, UUID> {
    Optional<UserEntity> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<UserEntity> findFirstByFullNameIgnoreCase(String fullName);

    /** Email uniqueness on update — true if any OTHER user already owns this email. */
    boolean existsByEmailIgnoreCaseAndUserIdNot(String email, UUID userId);

    boolean existsByEmailIgnoreCase(String email);

    /** Load one account together with its role (avoids LazyInitialization in the response mapper). */
    @EntityGraph(attributePaths = {"role"})
    Optional<UserEntity> findWithRoleByUserId(UUID userId);

    /** Used by the "last active Admin" guard (BR-03 safety). */
    long countByRole_RoleNameAndStatus(String roleName, UserStatus status);

    long countByRole_RoleId(Integer roleId);

    @EntityGraph(attributePaths = {"role"})
    @Query("SELECT u FROM UserEntity u ORDER BY u.fullName ASC")
    List<UserEntity> findAllWithRole();

    /**
     * UC-6.1 management list — paged search/filter.
     * Pass {@code ""} (never {@code null}) for {@code search}: Hibernate 6 binds a null String
     * as {@code bytea}, which breaks {@code LOWER(...)} (see backend/CLAUDE.md). {@code roleId}
     * and {@code status} are nullable to mean "no filter".
     */
    @EntityGraph(attributePaths = {"role"})
    @Query("""
            SELECT u FROM UserEntity u
            WHERE (:search = '' OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
                                OR LOWER(u.email)    LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:roleId IS NULL OR u.role.roleId = :roleId)
              AND (:status IS NULL OR u.status = :status)
            """)
    Page<UserEntity> searchUsers(@Param("search") String search,
                                 @Param("roleId") Integer roleId,
                                 @Param("status") UserStatus status,
                                 Pageable pageable);
}
