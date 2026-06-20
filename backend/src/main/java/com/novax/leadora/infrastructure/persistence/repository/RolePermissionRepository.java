package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.infrastructure.persistence.entity.RolePermissionEntity;
import com.novax.leadora.infrastructure.persistence.entity.RolePermissionId;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermissionEntity, RolePermissionId> {

    /** Mappings for one role, with the permission eagerly loaded for response mapping. */
    @EntityGraph(attributePaths = {"permission"})
    List<RolePermissionEntity> findByRole_RoleId(Integer roleId);

    /** All mappings, permission eagerly loaded — used to build the role list in one pass. */
    @EntityGraph(attributePaths = {"permission", "role"})
    @Query("SELECT rp FROM RolePermissionEntity rp")
    List<RolePermissionEntity> findAllWithPermission();

    long countByRole_RoleId(Integer roleId);

    /** UC-6.4 — wipe a role's permission set before re-applying the requested one. */
    @Modifying
    @Query("DELETE FROM RolePermissionEntity rp WHERE rp.role.roleId = :roleId")
    void deleteByRoleId(@Param("roleId") Integer roleId);
}
