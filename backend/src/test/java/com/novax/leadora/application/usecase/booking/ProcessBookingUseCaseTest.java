package com.novax.leadora.application.usecase.booking;

import com.novax.leadora.api.dto.request.ProcessBookingRequest;
import com.novax.leadora.api.dto.response.BookingResponse;
import com.novax.leadora.application.usecase.audit.SystemAuditLogService;
import com.novax.leadora.application.usecase.deal.DealWorkflowSyncService;
import com.novax.leadora.application.usecase.sla.ResolveSlaBreachUseCase;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.integration.email.EmailService;
import com.novax.leadora.infrastructure.persistence.entity.BookingDetailEntity;
import com.novax.leadora.infrastructure.persistence.entity.BookingEntity;
import com.novax.leadora.infrastructure.persistence.entity.CustomerEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.BookingStatus;
import com.novax.leadora.infrastructure.persistence.repository.BookingDetailRepository;
import com.novax.leadora.infrastructure.persistence.repository.BookingRepository;
import com.novax.leadora.infrastructure.persistence.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProcessBookingUseCaseTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingDetailRepository bookingDetailRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private BookingStatusTransitionService bookingStatusTransitionService;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private ResolveSlaBreachUseCase resolveSlaBreachUseCase;

    @Mock
    private SystemAuditLogService systemAuditLogService;

    @Mock
    private DealWorkflowSyncService dealWorkflowSyncService;

    @InjectMocks
    private ProcessBookingUseCase processBookingUseCase;

    private UUID bookingId;
    private BookingEntity bookingEntity;
    private CustomerEntity customerEntity;
    private UserEntity assignedUser;
    private List<BookingDetailEntity> bookingDetails;

    @BeforeEach
    void setUp() {
        bookingId = UUID.randomUUID();
        customerEntity = CustomerEntity.builder()
                .customerId(UUID.randomUUID())
                .fullName("John Doe")
                .email("john.doe@example.com")
                .build();
        assignedUser = UserEntity.builder()
                .userId(UUID.randomUUID())
                .fullName("Agent Smith")
                .build();

        bookingEntity = BookingEntity.builder()
                .bookingId(bookingId)
                .bookingCode("BK-TEST1234")
                .customer(customerEntity)
                .assignedUser(assignedUser)
                .status(BookingStatus.PENDING)
                .checkInDate(LocalDate.now().plusDays(2))
                .checkOutDate(LocalDate.now().plusDays(5))
                .totalAmount(new BigDecimal("1500000.00"))
                .build();

        bookingDetails = Collections.singletonList(
                BookingDetailEntity.builder()
                        .bookingDetailId(UUID.randomUUID())
                        .booking(bookingEntity)
                        .description("Deluxe Suite")
                        .roomNumber("301")
                        .quantity(1)
                        .nights(3)
                        .unitPrice(new BigDecimal("500000.00"))
                        .lineTotal(new BigDecimal("1500000.00"))
                        .build());

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(bookingEntity));
        when(bookingRepository.save(any(BookingEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(bookingDetailRepository.findByBooking_BookingId(bookingId)).thenReturn(bookingDetails);
        when(bookingStatusTransitionService.transition(any(UUID.class), any(BookingStatus.class),
                any(TransitionActor.class), any()))
                .thenAnswer(invocation -> {
                    BookingStatus newStatus = invocation.getArgument(1);
                    String reason = invocation.getArgument(3);
                    bookingEntity.setStatus(newStatus);
                    bookingEntity.setStatusReason(reason);
                    return bookingEntity;
                });
    }

    @Test
    void shouldSendConfirmationEmailWhenBookingTransitionsToConfirmed() {
        ProcessBookingRequest request = new ProcessBookingRequest();
        request.setStatus("CONFIRMED");

        BookingResponse response = processBookingUseCase.execute(bookingId, request);

        assertThat(response.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verify(emailService, times(1)).sendBookingConfirmationEmail(any(BookingEntity.class), eq(bookingDetails));
    }

    @Test
    void shouldNotSendEmailWhenBookingIsAlreadyConfirmed() {
        bookingEntity.setStatus(BookingStatus.CONFIRMED);
        ProcessBookingRequest request = new ProcessBookingRequest();
        request.setStatus("CONFIRMED");

        BookingResponse response = processBookingUseCase.execute(bookingId, request);

        assertThat(response.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verify(emailService, never()).sendBookingConfirmationEmail(any(BookingEntity.class), anyList());
    }

    @Test
    void shouldNotSendEmailWhenBookingIsRejected() {
        ProcessBookingRequest request = new ProcessBookingRequest();
        request.setStatus("REJECTED");
        request.setStatusReason("Invalid documents");

        BookingResponse response = processBookingUseCase.execute(bookingId, request);

        assertThat(response.getStatus()).isEqualTo(BookingStatus.REJECTED);
        verify(emailService, never()).sendBookingConfirmationEmail(any(BookingEntity.class), anyList());
    }

    @Test
    void shouldKeepBookingConfirmedWhenEmailSendingFails() {
        ProcessBookingRequest request = new ProcessBookingRequest();
        request.setStatus("CONFIRMED");

        doThrow(new RuntimeException("SMTP Server Down")).when(emailService)
                .sendBookingConfirmationEmail(any(BookingEntity.class), anyList());

        BookingResponse response = processBookingUseCase.execute(bookingId, request);

        assertThat(response.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verify(emailService, times(1)).sendBookingConfirmationEmail(any(BookingEntity.class), eq(bookingDetails));
    }

    @Test
    void shouldThrowExceptionWhenBookingNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(bookingRepository.findById(unknownId)).thenReturn(Optional.empty());

        ProcessBookingRequest request = new ProcessBookingRequest();
        request.setStatus("CONFIRMED");

        assertThatThrownBy(() -> processBookingUseCase.execute(unknownId, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Booking not found");
    }
}
