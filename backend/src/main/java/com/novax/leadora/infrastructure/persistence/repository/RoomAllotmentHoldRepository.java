package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.infrastructure.persistence.entity.RoomAllotmentHoldEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.HoldStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface RoomAllotmentHoldRepository extends JpaRepository<RoomAllotmentHoldEntity, UUID> {

    /** One live hold: which room type, how many rooms, and the nights it covers. */
    interface HoldSpan {
        UUID getProductId();
        UUID getQuotationId();
        LocalDate getCheckInDate();
        LocalDate getCheckOutDate();
        Integer getQuantity();
    }

    /**
     * Live holds overlapping a window, expanded to nights by {@code RoomAvailabilityService}.
     *
     * <p>{@code quotationId} comes back so a quotation being revised can discount its own
     * existing hold. Without that, editing a quotation would find the rooms it is already
     * holding counted against it and report itself out of stock.
     */
    @Query("""
            SELECT h.product.productId     AS productId,
                   h.quotation.quotationId AS quotationId,
                   h.checkInDate           AS checkInDate,
                   h.checkOutDate          AS checkOutDate,
                   h.quantity              AS quantity
            FROM RoomAllotmentHoldEntity h
            WHERE h.status = com.novax.leadora.infrastructure.persistence.entity.enums.HoldStatus.ACTIVE
              AND h.checkInDate < :toExclusive
              AND h.checkOutDate > :from
              AND h.product.productId IN :productIds
            """)
    List<HoldSpan> findActiveSpans(
            @Param("productIds") Collection<UUID> productIds,
            @Param("from") LocalDate from,
            @Param("toExclusive") LocalDate toExclusive);

    /** Holds attached to one quotation, for releasing or converting them as a set. */
    List<RoomAllotmentHoldEntity> findByQuotation_QuotationIdAndStatus(UUID quotationId, HoldStatus status);

    /**
     * The expiry sweep. A bulk {@code UPDATE} rather than load-mutate-save: the rows are only
     * being stamped, none of the entity behaviour applies, and a backlog after downtime could
     * otherwise pull a large batch into memory.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE RoomAllotmentHoldEntity h
               SET h.status = com.novax.leadora.infrastructure.persistence.entity.enums.HoldStatus.EXPIRED,
                   h.releasedAt = :now
             WHERE h.status = com.novax.leadora.infrastructure.persistence.entity.enums.HoldStatus.ACTIVE
               AND h.expiresAt < :now
            """)
    int expireLapsedHolds(@Param("now") OffsetDateTime now);
}
