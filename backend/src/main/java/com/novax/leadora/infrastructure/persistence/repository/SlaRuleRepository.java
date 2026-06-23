package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.infrastructure.persistence.entity.SlaRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SlaRuleRepository extends JpaRepository<SlaRuleEntity, UUID> {
    List<SlaRuleEntity> findAllByOrderByActivityTypeAsc();
    Optional<SlaRuleEntity> findByActivityTypeAndActiveTrue(String activityType);
    boolean existsByActivityType(String activityType);
}
