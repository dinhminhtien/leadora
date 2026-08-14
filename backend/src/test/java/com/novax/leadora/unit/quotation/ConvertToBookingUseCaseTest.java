package com.novax.leadora.unit.quotation;

import com.novax.leadora.api.dto.request.ConvertToBookingRequest;
import com.novax.leadora.api.dto.response.BookingResponse;
import com.novax.leadora.application.usecase.contract.ActivateContractUseCase;
import com.novax.leadora.application.usecase.quotation.ConvertToBookingUseCase;
import com.novax.leadora.application.usecase.deal.DealWorkflowSyncService;
import com.novax.leadora.application.usecase.quotation.QuotationAccessPolicy;
import com.novax.leadora.application.usecase.quotation.QuotationActionPolicy;
import com.novax.leadora.application.usecase.sla.StartSlaTrackingUseCase;
import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.persistence.entity.*;
import com.novax.leadora.infrastructure.persistence.entity.enums.BookingStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.ContractStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.QuotationStatus;
import com.novax.leadora.infrastructure.persistence.repository.BookingDetailRepository;
import com.novax.leadora.infrastructure.persistence.repository.BookingRepository;
import com.novax.leadora.infrastructure.persistence.repository.ContractRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationDetailRepository;
import com.novax.leadora.infrastructure.persistence.repository.QuotationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.novax.leadora.infrastructure.persistence.entity.ProductServiceEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.ProductCategory;
import com.novax.leadora.infrastructure.persistence.entity.enums.ProductStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConvertToBookingUseCaseTest {

    @Mock
    private QuotationRepository quotationRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private QuotationDetailRepository quotationDetailRepository;

    @Mock
    private BookingDetailRepository bookingDetailRepository;

    @Mock
    private QuotationAccessPolicy quotationAccessPolicy;

    @Mock
    private com.novax.leadora.application.usecase.roomrequest.RoomConfirmationReader roomConfirmationReader;

    @Mock
    private StartSlaTrackingUseCase startSlaTrackingUseCase;

    @Mock
    private ContractRepository contractRepository;

    @Mock
    private ActivateContractUseCase activateContractUseCase;

    @Mock
    private DealWorkflowSyncService dealWorkflowSyncService;

    private ConvertToBookingUseCase convertToBookingUseCase;

    /**
     * The policy is built for real from the same mocks rather than stubbed.
     *
     * <p>Its refusals are the subject of half these tests, and the whole point of extracting it
     * was that the conversion and the UI's eligibility read run the identical checks — a mocked
     * policy here would assert only that the use case calls something, and would keep passing if
     * the rules themselves broke.
     */
    @BeforeEach
    void setUp() {
        QuotationActionPolicy quotationActionPolicy = new QuotationActionPolicy(
                contractRepository,
                quotationDetailRepository,
                bookingRepository,
                roomConfirmationReader);

        convertToBookingUseCase = new ConvertToBookingUseCase(
                quotationRepository,
                bookingRepository,
                quotationDetailRepository,
                bookingDetailRepository,
                quotationAccessPolicy,
                quotationActionPolicy,
                startSlaTrackingUseCase,
                activateContractUseCase,
                dealWorkflowSyncService);
    }

    /** A quotation that clears every condition except the one a test is about. */
    private static QuotationEntity convertibleQuotation(UUID quotationId, QuotationStatus status) {
        return QuotationEntity.builder()
                .quotationId(quotationId)
                .status(status)
                .customer(CustomerEntity.builder().customerId(UUID.randomUUID()).build())
                .checkInDate(LocalDate.now().plusDays(2))
                .checkOutDate(LocalDate.now().plusDays(5))
                .build();
    }

    @Test
    @DisplayName("UT-CONVERT-01: Throw ResourceNotFoundException if quotation does not exist")
    void testQuotationNotFound() {
        UUID quotationId = UUID.randomUUID();
        when(quotationRepository.findByIdForUpdate(quotationId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> 
                convertToBookingUseCase.execute(quotationId, new ConvertToBookingRequest())
        );
    }

    @Test
    @DisplayName("UT-CONVERT-02: Throw BusinessException if quotation is not ACCEPTED")
    void testQuotationNotAccepted() {
        UUID quotationId = UUID.randomUUID();
        QuotationEntity quotation = QuotationEntity.builder()
                .quotationId(quotationId)
                .status(QuotationStatus.SENT)
                .build();

        when(quotationRepository.findByIdForUpdate(quotationId)).thenReturn(Optional.of(quotation));

        BusinessException exception = assertThrows(BusinessException.class, () -> 
                convertToBookingUseCase.execute(quotationId, new ConvertToBookingRequest())
        );
        assertEquals("QUOTATION_INVALID_STATUS", exception.getErrorCode());
        assertEquals(HttpStatus.CONFLICT, exception.getHttpStatus());
    }

    @Test
    @DisplayName("UT-CONVERT-03: Throw BusinessException if no contract exists")
    void testNoContractExists() {
        UUID quotationId = UUID.randomUUID();
        QuotationEntity quotation = convertibleQuotation(quotationId, QuotationStatus.ACCEPTED);

        when(quotationRepository.findByIdForUpdate(quotationId)).thenReturn(Optional.of(quotation));
        when(contractRepository.findByQuotation_QuotationId(quotationId)).thenReturn(Collections.emptyList());

        BusinessException exception = assertThrows(BusinessException.class, () -> 
                convertToBookingUseCase.execute(quotationId, new ConvertToBookingRequest())
        );
        assertEquals("CONTRACT_REQUIRED", exception.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
    }

    @Test
    @DisplayName("UT-CONVERT-04: Throw BusinessException if contract is in DRAFT or SENT status")
    void testContractNotAcknowledged() {
        UUID quotationId = UUID.randomUUID();
        QuotationEntity quotation = convertibleQuotation(quotationId, QuotationStatus.ACCEPTED);

        ContractEntity contract = ContractEntity.builder()
                .id(UUID.randomUUID())
                .status(ContractStatus.SENT)
                .version(1)
                .build();

        when(quotationRepository.findByIdForUpdate(quotationId)).thenReturn(Optional.of(quotation));
        when(contractRepository.findByQuotation_QuotationId(quotationId)).thenReturn(List.of(contract));

        BusinessException exception = assertThrows(BusinessException.class, () -> 
                convertToBookingUseCase.execute(quotationId, new ConvertToBookingRequest())
        );
        assertEquals("CONTRACT_NOT_ACKNOWLEDGED", exception.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
    }

    @Test
    @DisplayName("UT-CONVERT-05: Throw BusinessException if contract is CANCELLED")
    void testContractInvalidState() {
        UUID quotationId = UUID.randomUUID();
        QuotationEntity quotation = convertibleQuotation(quotationId, QuotationStatus.ACCEPTED);

        ContractEntity contract = ContractEntity.builder()
                .id(UUID.randomUUID())
                .status(ContractStatus.CANCELLED)
                .version(1)
                .build();

        when(quotationRepository.findByIdForUpdate(quotationId)).thenReturn(Optional.of(quotation));
        when(contractRepository.findByQuotation_QuotationId(quotationId)).thenReturn(List.of(contract));

        BusinessException exception = assertThrows(BusinessException.class, () -> 
                convertToBookingUseCase.execute(quotationId, new ConvertToBookingRequest())
        );
        assertEquals("CONTRACT_INVALID_STATE", exception.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
    }

    @Test
    @DisplayName("UT-CONVERT-09: Blocked when the Reservation team has not confirmed the rooms")
    void testBlockedWhenRoomsNotConfirmed() {
        UUID quotationId = UUID.randomUUID();
        QuotationEntity quotation = convertibleQuotation(quotationId, QuotationStatus.ACCEPTED);

        ContractEntity contract = ContractEntity.builder()
                .id(UUID.randomUUID())
                .status(ContractStatus.ACKNOWLEDGED)
                .version(1)
                .build();

        ProductServiceEntity roomProduct = ProductServiceEntity.builder()
                .productId(UUID.randomUUID())
                .name("Deluxe Room")
                .category(ProductCategory.ROOM)
                .status(ProductStatus.ACTIVE)
                .unitPrice(BigDecimal.valueOf(1000000))
                .build();

        QuotationDetailEntity detail = QuotationDetailEntity.builder()
                .productService(roomProduct)
                .description("Deluxe Room")
                .quantity(2)
                .nights(3)
                .unitPrice(BigDecimal.valueOf(1000000))
                .lineTotal(BigDecimal.valueOf(6000000))
                .build();

        when(quotationRepository.findByIdForUpdate(quotationId)).thenReturn(Optional.of(quotation));
        when(contractRepository.findByQuotation_QuotationId(quotationId)).thenReturn(List.of(contract));
        when(quotationDetailRepository.findByQuotation_QuotationId(quotationId)).thenReturn(List.of(detail));
        // No answer from Reservation — and that is now the whole gate. Published allotment,
        // however generous, no longer authorises a booking on its own (Report 1 FE-19/LI-02).
        when(roomConfirmationReader.isRoomConfirmed(quotation)).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                convertToBookingUseCase.execute(quotationId, new ConvertToBookingRequest()));

        assertEquals("ROOM_NOT_CONFIRMED", exception.getErrorCode());
        assertEquals(HttpStatus.CONFLICT, exception.getHttpStatus());
        verify(bookingRepository, never()).save(any(BookingEntity.class));
    }

    @Test
    @DisplayName("UT-CONVERT-06: Convert successfully with ACKNOWLEDGED contract and trigger activation")
    void testConvertSuccessfully() {
        UUID quotationId = UUID.randomUUID();
        UUID dealId = UUID.randomUUID();
        DealEntity deal = DealEntity.builder().dealId(dealId).build();
        CustomerEntity customer = CustomerEntity.builder().customerId(UUID.randomUUID()).build();
        QuotationEntity quotation = QuotationEntity.builder()
                .quotationId(quotationId)
                .status(QuotationStatus.ACCEPTED)
                .customer(customer)
                .checkInDate(LocalDate.now().plusDays(2))
                .checkOutDate(LocalDate.now().plusDays(5))
                .totalAmount(BigDecimal.valueOf(5000000))
                .deal(deal)
                .build();

        ContractEntity contract = ContractEntity.builder()
                .id(UUID.randomUUID())
                .status(ContractStatus.ACKNOWLEDGED)
                .version(1)
                .build();

        // The line must point at a product: availability is keyed on the product, and Convert
        // refuses a line it cannot resolve rather than skipping the check.
        ProductServiceEntity roomProduct = ProductServiceEntity.builder()
                .productId(UUID.randomUUID())
                .name("Deluxe Room")
                .category(ProductCategory.ROOM)
                .status(ProductStatus.ACTIVE)
                .unitPrice(BigDecimal.valueOf(1000000))
                .build();

        QuotationDetailEntity detail = QuotationDetailEntity.builder()
                .productService(roomProduct)
                .description("Deluxe Room")
                .quantity(2)
                .nights(3)
                .unitPrice(BigDecimal.valueOf(1000000))
                .lineTotal(BigDecimal.valueOf(6000000))
                .build();

        ConvertToBookingRequest request = new ConvertToBookingRequest();

        when(quotationRepository.findByIdForUpdate(quotationId)).thenReturn(Optional.of(quotation));
        when(contractRepository.findByQuotation_QuotationId(quotationId)).thenReturn(List.of(contract));
        when(quotationDetailRepository.findByQuotation_QuotationId(quotationId)).thenReturn(List.of(detail));
        // The room gate is now solely the Reservation team's recorded answer.
        when(roomConfirmationReader.isRoomConfirmed(quotation)).thenReturn(true);
        
        when(bookingRepository.save(any(BookingEntity.class))).thenAnswer(inv -> {
            BookingEntity booking = inv.getArgument(0);
            booking.setBookingId(UUID.randomUUID());
            return booking;
        });

        BookingResponse response = convertToBookingUseCase.execute(quotationId, request);

        assertNotNull(response);
        assertEquals(BookingStatus.PENDING, response.getStatus());
        assertEquals(QuotationStatus.CONVERTED, quotation.getStatus());

        // Verify activation is called
        verify(activateContractUseCase, times(1)).execute(contract.getId());
        verify(bookingRepository, times(1)).save(any(BookingEntity.class));
        verify(bookingDetailRepository, times(1)).saveAll(anyList());
        verify(dealWorkflowSyncService, times(1)).syncPipelineStage(dealId);
    }

    @Test
    @DisplayName("UT-CONVERT-07: Convert successfully with ACCEPTED_BY_CUSTOMER status and set status to BOOKING_REQUEST")
    void testConvertSuccessfullyAcceptedByCustomer() {
        UUID quotationId = UUID.randomUUID();
        UUID dealId = UUID.randomUUID();
        DealEntity deal = DealEntity.builder().dealId(dealId).build();
        CustomerEntity customer = CustomerEntity.builder().customerId(UUID.randomUUID()).build();
        QuotationEntity quotation = QuotationEntity.builder()
                .quotationId(quotationId)
                .status(QuotationStatus.ACCEPTED_BY_CUSTOMER)
                .customer(customer)
                .checkInDate(LocalDate.now().plusDays(2))
                .checkOutDate(LocalDate.now().plusDays(5))
                .totalAmount(BigDecimal.valueOf(5000000))
                .deal(deal)
                .build();

        ContractEntity contract = ContractEntity.builder()
                .id(UUID.randomUUID())
                .status(ContractStatus.ACKNOWLEDGED)
                .version(1)
                .build();

        // The line must point at a product: availability is keyed on the product, and Convert
        // refuses a line it cannot resolve rather than skipping the check.
        ProductServiceEntity roomProduct = ProductServiceEntity.builder()
                .productId(UUID.randomUUID())
                .name("Deluxe Room")
                .category(ProductCategory.ROOM)
                .status(ProductStatus.ACTIVE)
                .unitPrice(BigDecimal.valueOf(1000000))
                .build();

        QuotationDetailEntity detail = QuotationDetailEntity.builder()
                .productService(roomProduct)
                .description("Deluxe Room")
                .quantity(2)
                .nights(3)
                .unitPrice(BigDecimal.valueOf(1000000))
                .lineTotal(BigDecimal.valueOf(6000000))
                .build();

        ConvertToBookingRequest request = new ConvertToBookingRequest();

        when(quotationRepository.findByIdForUpdate(quotationId)).thenReturn(Optional.of(quotation));
        when(contractRepository.findByQuotation_QuotationId(quotationId)).thenReturn(List.of(contract));
        when(quotationDetailRepository.findByQuotation_QuotationId(quotationId)).thenReturn(List.of(detail));
        // The room gate is now solely the Reservation team's recorded answer.
        when(roomConfirmationReader.isRoomConfirmed(quotation)).thenReturn(true);
        
        when(bookingRepository.save(any(BookingEntity.class))).thenAnswer(inv -> {
            BookingEntity booking = inv.getArgument(0);
            booking.setBookingId(UUID.randomUUID());
            return booking;
        });

        BookingResponse response = convertToBookingUseCase.execute(quotationId, request);

        assertNotNull(response);
        assertEquals(BookingStatus.PENDING, response.getStatus());
        assertEquals(QuotationStatus.BOOKING_REQUEST, quotation.getStatus());

        // Verify activation is called
        verify(activateContractUseCase, times(1)).execute(contract.getId());
        verify(bookingRepository, times(1)).save(any(BookingEntity.class));
        verify(bookingDetailRepository, times(1)).saveAll(anyList());
        verify(dealWorkflowSyncService, times(1)).syncPipelineStage(dealId);
    }

    @Test
    @DisplayName("UT-CONVERT-08: Convert successfully with RESERVATION_PENDING status and DRAFT contract")
    void testConvertSuccessfullyReservationPendingDraftContract() {
        UUID quotationId = UUID.randomUUID();
        UUID dealId = UUID.randomUUID();
        DealEntity deal = DealEntity.builder().dealId(dealId).build();
        CustomerEntity customer = CustomerEntity.builder().customerId(UUID.randomUUID()).build();
        QuotationEntity quotation = QuotationEntity.builder()
                .quotationId(quotationId)
                .status(QuotationStatus.RESERVATION_PENDING)
                .customer(customer)
                .checkInDate(LocalDate.now().plusDays(2))
                .checkOutDate(LocalDate.now().plusDays(5))
                .totalAmount(BigDecimal.valueOf(5000000))
                .deal(deal)
                .build();

        ContractEntity contract = ContractEntity.builder()
                .id(UUID.randomUUID())
                .status(ContractStatus.DRAFT)
                .version(1)
                .build();

        // The line must point at a product: availability is keyed on the product, and Convert
        // refuses a line it cannot resolve rather than skipping the check.
        ProductServiceEntity roomProduct = ProductServiceEntity.builder()
                .productId(UUID.randomUUID())
                .name("Deluxe Room")
                .category(ProductCategory.ROOM)
                .status(ProductStatus.ACTIVE)
                .unitPrice(BigDecimal.valueOf(1000000))
                .build();

        QuotationDetailEntity detail = QuotationDetailEntity.builder()
                .productService(roomProduct)
                .description("Deluxe Room")
                .quantity(2)
                .nights(3)
                .unitPrice(BigDecimal.valueOf(1000000))
                .lineTotal(BigDecimal.valueOf(6000000))
                .build();

        ConvertToBookingRequest request = new ConvertToBookingRequest();

        when(quotationRepository.findByIdForUpdate(quotationId)).thenReturn(Optional.of(quotation));
        when(contractRepository.findByQuotation_QuotationId(quotationId)).thenReturn(List.of(contract));
        when(quotationDetailRepository.findByQuotation_QuotationId(quotationId)).thenReturn(List.of(detail));
        // The room gate is now solely the Reservation team's recorded answer.
        when(roomConfirmationReader.isRoomConfirmed(quotation)).thenReturn(true);
        
        when(bookingRepository.save(any(BookingEntity.class))).thenAnswer(inv -> {
            BookingEntity booking = inv.getArgument(0);
            booking.setBookingId(UUID.randomUUID());
            return booking;
        });

        BookingResponse response = convertToBookingUseCase.execute(quotationId, request);

        assertNotNull(response);
        assertEquals(BookingStatus.PENDING, response.getStatus());
        assertEquals(QuotationStatus.BOOKING_REQUEST, quotation.getStatus());

        // Verify activation is NOT called because contract is not ACKNOWLEDGED yet
        verify(activateContractUseCase, never()).execute(any());
        verify(bookingRepository, times(1)).save(any(BookingEntity.class));
        verify(bookingDetailRepository, times(1)).saveAll(anyList());
        verify(dealWorkflowSyncService, times(1)).syncPipelineStage(dealId);
    }
}
