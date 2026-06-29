package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.infrastructure.persistence.entity.PermissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PermissionRepository extends JpaRepository<PermissionEntity, Integer> {
    Optional<PermissionEntity> findByPermissionCode(String permissionCode);

    List<PermissionEntity> findAllByOrderByPermissionIdAsc();

    /** All permission codes — the effective set for ADMIN (full access). */
    @Query("SELECT p.permissionCode FROM PermissionEntity p")
    List<String> findAllCodes();
}
