package com.novax.leadora.unit.quotation;

import com.novax.leadora.api.dto.request.ConvertToBookingRequest;
import com.novax.leadora.api.dto.response.BookingResponse;
import com.novax.leadora.application.event.ReservationApprovedEvent;
import com.novax.leadora.application.event.ReservationRejectedEvent;
import com.novax.leadora.application.usecase.activitylog.ActivityLogPublisher;
import com.novax.leadora.application.usecase.audit.SystemAuditLogService;
import com.novax.leadora.application.usecase.quotation.ApproveReservationUseCase;
import com.novax.leadora.application.usecase.quotation.ConvertToBookingUseCase;
import com.novax.leadora.application.usecase.quotation.RejectReservationUseCase;
import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.NotificationEntity;
import com.novax.leadora.infrastructure.persistence.entity.QuotationEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.ReservationRejectReason;
import com.novax.leadora.infrastructure.persistence.repository.NotificationRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationRepository;
import com.novax.leadora.infrastructure.persistence.repository.ReservationDecisionLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationDecisionUseCaseTest {

    @Mock private QuotationRepository quotationRepository;
    @Mock private ConvertToBookingUseCase convertToBookingUseCase;
    @Mock private ReservationDecisionLogRepository decisionLogRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private CurrentUserProvider currentUserProvider;
    @Mock private ActivityLogPublisher activityLogPublisher;
    @Mock private SystemAuditLogService systemAuditLogService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private ApproveReservationUseCase approveReservationUseCase;
    private RejectReservationUseCase rejectReservationUseCase;

    private UserEntity mockStaff;
    private UUID quotationId;

    @BeforeEach
    void setUp() {
        approveReservationUseCase = new ApproveReservationUseCase(
                quotationRepository,
                convertToBookingUseCase,
                decisionLogRepository,
                currentUserProvider,
                eventPublisher
        );

        rejectReservationUseCase = new RejectReservationUseCase(
                quotationRepository,
                decisionLogRepository,
                notificationRepository,
                currentUserProvider,
                activityLogPublisher,
                systemAuditLogService,
                eventPublisher
        );

        mockStaff = UserEntity.builder()
                .userId(UUID.randomUUID())
                .fullName("Reservation Staff Member")
                .build();
        quotationId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Approve Reservation - Success Flow")
    void testApproveReservationSuccess() {
        QuotationEntity quotation = QuotationEntity.builder()
                .quotationId(quotationId)
                .status(QuotationStatus.RESERVATION_PENDING)
                .build();

        UUID bookingId = UUID.randomUUID();
        BookingResponse bookingResponse = BookingResponse.builder()
                .bookingId(bookingId)
                .quotationId(quotationId)
                .build();

        when(quotationRepository.findById(quotationId)).thenReturn(Optional.of(quotation));
        when(currentUserProvider.resolve(null)).thenReturn(mockStaff);
        when(convertToBookingUseCase.execute(eq(quotationId), any(ConvertToBookingRequest.class)))
                .thenReturn(bookingResponse);

        BookingResponse result = approveReservationUseCase.execute(quotationId);

        assertNotNull(result);
        assertEquals(bookingId, result.getBookingId());

        verify(decisionLogRepository, times(1)).save(argThat(log -> 
                log.getQuotationId().equals(quotationId) &&
                "APPROVED".equals(log.getDecision()) &&
                log.getBookingId().equals(bookingId) &&
                log.getDecidedBy().equals(mockStaff.getUserId())
        ));
        verify(eventPublisher, times(1)).publishEvent(any(ReservationApprovedEvent.class));
    }

    @Test
    @DisplayName("Approve Reservation - Fails when status is not RESERVATION_PENDING")
    void testApproveReservationInvalidStatus() {
        QuotationEntity quotation = QuotationEntity.builder()
                .quotationId(quotationId)
                .status(QuotationStatus.SENT)
                .build();

        when(quotationRepository.findById(quotationId)).thenReturn(Optional.of(quotation));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                approveReservationUseCase.execute(quotationId)
        );

        assertEquals("INVALID_QUOTATION_STATUS", ex.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        verifyNoInteractions(convertToBookingUseCase, decisionLogRepository, eventPublisher);
    }

    @Test
    @DisplayName("Reject Reservation - Success Flow")
    void testRejectReservationSuccess() {
        UserEntity salesRep = UserEntity.builder()
                .userId(UUID.randomUUID())
                .fullName("Sales Agent")
                .build();

        QuotationEntity quotation = QuotationEntity.builder()
                .quotationId(quotationId)
                .status(QuotationStatus.RESERVATION_PENDING)
                .createdBy(salesRep)
                .build();

        when(quotationRepository.findById(quotationId)).thenReturn(Optional.of(quotation));
        when(currentUserProvider.resolve(null)).thenReturn(mockStaff);
        when(quotationRepository.save(any(QuotationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        QuotationEntity result = rejectReservationUseCase.execute(quotationId, ReservationRejectReason.NO_ROOM_AVAILABLE, "Out of executive suites.");

        assertNotNull(result);
        assertEquals(QuotationStatus.RESERVATION_REJECTED, result.getStatus());

        verify(decisionLogRepository, times(1)).save(argThat(log -> 
                log.getQuotationId().equals(quotationId) &&
                "REJECTED".equals(log.getDecision()) &&
                log.getRejectReason() == ReservationRejectReason.NO_ROOM_AVAILABLE &&
                "Out of executive suites.".equals(log.getNote()) &&
                log.getDecidedBy().equals(mockStaff.getUserId())
        ));
        verify(notificationRepository, times(1)).save(any(NotificationEntity.class));
        verify(eventPublisher, times(1)).publishEvent(any(ReservationRejectedEvent.class));
    }
}
