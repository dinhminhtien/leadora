package com.novax.leadora.application.usecase.roomrequest;

import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import com.novax.leadora.infrastructure.persistence.entity.RoomRequestEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.RoomRequestStatus;
import com.novax.leadora.infrastructure.persistence.repository.RoomRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Every branch of {@link RoomConfirmationReader#isRoomConfirmed}.
 *
 * <p>Nothing here refuses an operation: room confirmation is a condition recorded against a
 * quotation, not a precondition for sending or converting it. What matters is that the
 * reader only reports "confirmed" for an answer that still describes the quotation — a
 * stale or lapsed confirmation reported as good is what would let a rep promise a room the
 * Reservation team never held for those dates.
 */
@ExtendWith(MockitoExtension.class)
class RoomConfirmationReaderTest {

    @Mock
    private RoomRequestRepository roomRequestRepository;

    private RoomConfirmationReader reader;

    private UUID quotationId;
    private QuotationEntity quotation;
    private LocalDate checkIn;
    private LocalDate checkOut;

    @BeforeEach
    void setUp() {
        reader = new RoomConfirmationReader(roomRequestRepository);
        quotationId = UUID.randomUUID();
        checkIn = LocalDate.now().plusDays(10);
        checkOut = LocalDate.now().plusDays(12);
        quotation = QuotationEntity.builder()
                .quotationId(quotationId)
                .roomType("Deluxe Twin")
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .build();
    }

    private void givenCurrentRequest(RoomRequestEntity request) {
        when(roomRequestRepository.findFirstByQuotation_QuotationIdAndStatusNotOrderByCreatedAtDesc(
                quotationId, RoomRequestStatus.SUPERSEDED))
                .thenReturn(Optional.ofNullable(request));
    }

    private RoomRequestEntity request(RoomRequestStatus status) {
        return RoomRequestEntity.builder()
                .requestId(UUID.randomUUID())
                .quotation(quotation)
                .roomTypeRequested("Deluxe Twin")
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .quantity(2)
                .status(status)
                .build();
    }

    @Test
    @DisplayName("Nobody has asked the Reservation team yet → not confirmed")
    void notRequested() {
        givenCurrentRequest(null);

        assertThat(reader.isRoomConfirmed(quotation)).isFalse();
    }

    @Test
    @DisplayName("Request still waiting on the Reservation team → not confirmed")
    void pending() {
        givenCurrentRequest(request(RoomRequestStatus.PENDING));

        assertThat(reader.isRoomConfirmed(quotation)).isFalse();
    }

    @Test
    @DisplayName("Reservation said no → not confirmed")
    void rejected() {
        RoomRequestEntity rejected = request(RoomRequestStatus.REJECTED);
        rejected.setReservationNote("Fully booked that week");
        givenCurrentRequest(rejected);

        assertThat(reader.isRoomConfirmed(quotation)).isFalse();
    }

    @Test
    @DisplayName("Confirmed for other dates → not confirmed for these")
    void staleDates() {
        RoomRequestEntity confirmed = request(RoomRequestStatus.CONFIRMED);
        confirmed.setCheckInDate(checkIn.minusDays(3));
        givenCurrentRequest(confirmed);

        assertThat(reader.isRoomConfirmed(quotation)).isFalse();
    }

    @Test
    @DisplayName("Confirmed for another room type → not confirmed for this one")
    void staleRoomType() {
        RoomRequestEntity confirmed = request(RoomRequestStatus.CONFIRMED);
        confirmed.setRoomTypeRequested("Standard Queen");
        givenCurrentRequest(confirmed);

        assertThat(reader.isRoomConfirmed(quotation)).isFalse();
    }

    @Test
    @DisplayName("The hold the Reservation team committed to has lapsed → not confirmed")
    void holdExpired() {
        RoomRequestEntity confirmed = request(RoomRequestStatus.CONFIRMED);
        confirmed.setHeldUntil(OffsetDateTime.now().minusHours(1));
        givenCurrentRequest(confirmed);

        assertThat(reader.isRoomConfirmed(quotation)).isFalse();
    }

    @Test
    @DisplayName("Confirmed, matching, hold still live → confirmed")
    void confirmed() {
        RoomRequestEntity confirmed = request(RoomRequestStatus.CONFIRMED);
        confirmed.setHeldUntil(OffsetDateTime.now().plusHours(6));
        givenCurrentRequest(confirmed);

        assertThat(reader.isRoomConfirmed(quotation)).isTrue();
    }

    @Test
    @DisplayName("A null heldUntil is a confirmation with no deadline, not an expired one")
    void confirmedWithoutHoldDeadline() {
        givenCurrentRequest(request(RoomRequestStatus.CONFIRMED));

        assertThat(reader.isRoomConfirmed(quotation)).isTrue();
    }

    @Test
    @DisplayName("A direct booking with no quotation is never confirmed")
    void nullQuotation() {
        assertThat(reader.isRoomConfirmed(null)).isFalse();
    }

    @Test
    @DisplayName("currentRequest surfaces the live row for the clients to render")
    void currentRequestPassesThrough() {
        RoomRequestEntity pending = request(RoomRequestStatus.PENDING);
        givenCurrentRequest(pending);

        assertThat(reader.currentRequest(quotationId)).contains(pending);
    }
}
