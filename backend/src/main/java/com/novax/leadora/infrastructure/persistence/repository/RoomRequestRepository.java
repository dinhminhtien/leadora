package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.infrastructure.persistence.entity.RoomRequestEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.RoomRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoomRequestRepository extends JpaRepository<RoomRequestEntity, UUID> {

    /**
     * The request that currently speaks for a quotation: the newest one that hasn't
     * been superseded. Everything gating Send/Convert reads through this.
     */
    @EntityGraph(attributePaths = {"requestedBy", "respondedBy"})
    Optional<RoomRequestEntity> findFirstByQuotation_QuotationIdAndStatusNotOrderByCreatedAtDesc(
            UUID quotationId, RoomRequestStatus excludedStatus);

    /** Rows to mark SUPERSEDED when Sales changes the room type / dates / quantity. */
    List<RoomRequestEntity> findByQuotation_QuotationIdAndStatusIn(
            UUID quotationId, List<RoomRequestStatus> statuses);

    /**
     * Locked read for the answer path — a plain findById would let two Reservation
     * staff both pass the "still PENDING" check before either writes.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RoomRequestEntity r WHERE r.requestId = :id")
    Optional<RoomRequestEntity> findByIdForUpdate(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"quotation", "quotation.customer", "requestedBy", "respondedBy"})
    Page<RoomRequestEntity> findByStatus(RoomRequestStatus status, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"quotation", "quotation.customer", "requestedBy", "respondedBy"})
    Page<RoomRequestEntity> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"requestedBy", "respondedBy"})
    List<RoomRequestEntity> findByQuotation_QuotationIdOrderByCreatedAtDesc(UUID quotationId);
}
