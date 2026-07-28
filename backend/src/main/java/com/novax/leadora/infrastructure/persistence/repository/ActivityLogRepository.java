package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.infrastructure.persistence.entity.ActivityLogEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ActivityLogType;
import com.novax.leadora.infrastructure.persistence.entity.enums.RecordOperation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ActivityLogRepository
        extends JpaRepository<ActivityLogEntity, UUID>, JpaSpecificationExecutor<ActivityLogEntity> {
    Optional<ActivityLogEntity> findFirstByEntityIdAndActivityTypeInAndRecordOperationInOrderByCreatedAtDesc(
            UUID entityId,
            Collection<ActivityLogType> activityTypes,
            Collection<RecordOperation> recordOperations);
}
