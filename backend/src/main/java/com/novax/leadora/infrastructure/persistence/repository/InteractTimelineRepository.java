package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.infrastructure.persistence.entity.InteractTimelineEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

@Repository
public interface InteractTimelineRepository
        extends JpaRepository<InteractTimelineEntity, UUID>, JpaSpecificationExecutor<InteractTimelineEntity> {
    List<InteractTimelineEntity> findByCustomer_CustomerIdOrderByOccurredAtDesc(UUID customerId);

    List<InteractTimelineEntity> findByLead_LeadIdOrderByOccurredAtDesc(UUID leadId);

    List<InteractTimelineEntity> findByDeal_DealIdOrderByOccurredAtDesc(UUID dealId);

    @Query("""
            SELECT u.fullName, COUNT(i)
            FROM InteractTimelineEntity i JOIN i.user u
            WHERE u.fullName IS NOT NULL
            GROUP BY u.fullName
            ORDER BY COUNT(i) DESC
            """)
    List<Object[]> findTopUserActivity(Pageable pageable);
}
