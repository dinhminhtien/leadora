package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.infrastructure.persistence.entity.OpHandoverEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.HandoverStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OpHandoverRepository extends JpaRepository<OpHandoverEntity, UUID> {
    List<OpHandoverEntity> findByBooking_BookingId(UUID bookingId);
    List<OpHandoverEntity> findByStatus(HandoverStatus status);
}
