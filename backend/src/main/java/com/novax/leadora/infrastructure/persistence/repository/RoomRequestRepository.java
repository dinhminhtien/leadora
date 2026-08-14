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
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoomRequestRepository extends JpaRepository<RoomRequestEntity, UUID> {

    /**
     * The request that currently speaks for a quotation: the newest one that is neither
     * superseded nor cancelled. Everything reporting on Send/Convert reads through this.
     *
     * <p>Takes a collection rather than a single excluded status because a withdrawn
     * request (UC-26.4) is no more an answer than a superseded one — leaving it in would
     * let a cancellation hide the confirmation that preceded it.
     */
    @EntityGraph(attributePaths = {"requestedBy", "respondedBy"})
    Optional<RoomRequestEntity> findFirstByQuotation_QuotationIdAndStatusNotInOrderByCreatedAtDesc(
            UUID quotationId, Collection<RoomRequestStatus> excludedStatuses);

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

    /**
     * The Reservation inbox, ordered by how urgent each row is to answer.
     *
     * <p>Three keys, in this order:
     *
     * <ol>
     *   <li><b>Unanswered first.</b> Matters on the "All" view, where a plain date sort buried
     *       live questions among rows that were settled weeks ago.</li>
     *   <li><b>Soonest check-in first.</b> A stay starting on Friday has to be answered before one
     *       starting in three months, however long each has been sitting there. Ordering purely by
     *       when the request was raised got this backwards whenever a rep quoted far ahead.</li>
     *   <li><b>Longest-waiting first.</b> The tie-break, and the SLA clock started then.</li>
     * </ol>
     *
     * <p>The ranking is in the query because it spans the status enum, so callers pass an
     * <b>unsorted</b> {@code Pageable}.
     *
     * @param status {@code null} for every status
     */
    @EntityGraph(attributePaths = {"quotation", "quotation.customer", "requestedBy", "respondedBy"})
    @Query("""
            SELECT r FROM RoomRequestEntity r
            WHERE (:status IS NULL OR r.status = :status)
            ORDER BY
              CASE WHEN r.status = com.novax.leadora.infrastructure.persistence.entity.enums.RoomRequestStatus.PENDING
                   THEN 0 ELSE 1 END ASC,
              r.checkInDate ASC,
              r.createdAt ASC
            """)
    Page<RoomRequestEntity> findInbox(@Param("status") RoomRequestStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"requestedBy", "respondedBy"})
    List<RoomRequestEntity> findByQuotation_QuotationIdOrderByCreatedAtDesc(UUID quotationId);
}
