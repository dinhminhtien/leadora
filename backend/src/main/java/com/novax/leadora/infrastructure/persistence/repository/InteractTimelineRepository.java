package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.infrastructure.persistence.entity.InteractTimelineEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InteractTimelineRepository extends JpaRepository<InteractTimelineEntity, UUID> {
    List<InteractTimelineEntity> findByCustomer_CustomerIdOrderByOccurredAtDesc(UUID customerId);
    List<InteractTimelineEntity> findByLead_LeadIdOrderByOccurredAtDesc(UUID leadId);
    List<InteractTimelineEntity> findByDeal_DealIdOrderByOccurredAtDesc(UUID dealId);
}
