package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.infrastructure.persistence.entity.OpHandoverEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.HandoverStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OpHandoverRepository
        extends JpaRepository<OpHandoverEntity, UUID>, JpaSpecificationExecutor<OpHandoverEntity> {
    List<OpHandoverEntity> findByBooking_BookingId(UUID bookingId);
    List<OpHandoverEntity> findByStatus(HandoverStatus status);

    /**
     * The bookings that already have a handover, for the "confirmed bookings still waiting for a
     * handover" list (UC-20.1).
     *
     * <p>Deliberately unscoped. Whether a handover exists for a booking is not confidential to
     * whoever created it, and scoping it is precisely what broke the caller: the frontend used to
     * derive this set from the paged handover list, which became owner-scoped, so a handover written
     * by a colleague made its booking look untouched and it was offered for a second handover.
     */
    @Query("select distinct h.booking.bookingId from OpHandoverEntity h where h.booking is not null")
    List<UUID> findBookingIdsWithHandover();
}
