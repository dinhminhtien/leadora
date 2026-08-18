package com.novax.leadora.unit.deal;

import com.novax.leadora.api.dto.request.DealRequest;
import com.novax.leadora.application.usecase.deal.DealValidation;
import com.novax.leadora.application.usecase.deal.DealWorkflowResolver;
import com.novax.leadora.common.exception.BusinessRuleException;
import com.novax.leadora.infrastructure.persistence.entity.CustomerEntity;
import com.novax.leadora.infrastructure.persistence.entity.DealEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.RoleEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.DealPipelineStage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.novax.leadora.infrastructure.persistence.repository.BookingRepository;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.application.usecase.audit.SystemAuditLogService;
import com.novax.leadora.infrastructure.persistence.entity.enums.BookingStatus;
import java.util.UUID;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;

import static org.junit.jupiter.api.Assertions.*;

class DealValidationTest {

        private DealValidation dealValidation;
        private BookingRepository bookingRepository;
        private CurrentUserProvider currentUserProvider;
        private SystemAuditLogService auditLogService;
        private DealWorkflowResolver dealWorkflowResolver;

        @BeforeEach
        void setUp() {
                bookingRepository = mock(BookingRepository.class);
                currentUserProvider = mock(CurrentUserProvider.class);
                auditLogService = mock(SystemAuditLogService.class);
                dealWorkflowResolver = mock(DealWorkflowResolver.class);
                dealValidation = new DealValidation(bookingRepository, currentUserProvider, auditLogService,
                                dealWorkflowResolver);
        }

        @Test
        @DisplayName("UT-DEAL-VAL-01: Same stage transition → no exception")
        void testSameStageTransition() {
                DealEntity deal = DealEntity.builder().build();
                DealRequest request = DealRequest.builder().build();

                assertDoesNotThrow(() -> dealValidation.validateStageTransition(
                                DealPipelineStage.INQUIRY, DealPipelineStage.INQUIRY, deal, request));
        }

        @Test
        @DisplayName("UT-DEAL-VAL-02: INQUIRY → QUALIFICATION without contact info → throws")
        void testQualificationWithoutContactThrows() {
                DealEntity deal = DealEntity.builder().customer(null).build();
                DealRequest request = DealRequest.builder()
                                .email("")
                                .phone("")
                                .build();

                assertThrows(BusinessRuleException.class, () -> dealValidation.validateStageTransition(
                                DealPipelineStage.INQUIRY, DealPipelineStage.QUALIFICATION, deal, request));
        }

        @Test
        @DisplayName("UT-DEAL-VAL-03: INQUIRY → QUALIFICATION with email → passes")
        void testQualificationWithEmailPasses() {
                DealEntity deal = DealEntity.builder().build();
                DealRequest request = DealRequest.builder()
                                .email("contact@hotel.vn")
                                .phone("")
                                .build();

                assertDoesNotThrow(() -> dealValidation.validateStageTransition(
                                DealPipelineStage.INQUIRY, DealPipelineStage.QUALIFICATION, deal, request));
        }

        @Test
        @DisplayName("UT-DEAL-VAL-04: INQUIRY → QUALIFICATION with phone from customer → passes")
        void testQualificationWithCustomerPhonePasses() {
                CustomerEntity customer = CustomerEntity.builder()
                                .phone("0912345678")
                                .build();
                DealEntity deal = DealEntity.builder().customer(customer).build();
                DealRequest request = DealRequest.builder()
                                .email("")
                                .phone("")
                                .build();

                assertDoesNotThrow(() -> dealValidation.validateStageTransition(
                                DealPipelineStage.INQUIRY, DealPipelineStage.QUALIFICATION, deal, request));
        }

        @Test
        @DisplayName("UT-DEAL-VAL-05: → QUOTATION_SENT without deal value → throws")
        void testProposalWithoutValueThrows() {
                DealEntity deal = DealEntity.builder().expectedRevenue(null).build();
                DealRequest request = DealRequest.builder()
                                .email("contact@hotel.vn")
                                .value(null)
                                .build();

                assertThrows(BusinessRuleException.class, () -> dealValidation.validateStageTransition(
                                DealPipelineStage.INQUIRY, DealPipelineStage.QUOTATION_SENT, deal, request));
        }

        @Test
        @DisplayName("UT-DEAL-VAL-06: → QUOTATION_SENT with zero value → throws")
        void testProposalWithZeroValueThrows() {
                DealEntity deal = DealEntity.builder().expectedRevenue(BigDecimal.ZERO).build();
                DealRequest request = DealRequest.builder()
                                .email("contact@hotel.vn")
                                .value(BigDecimal.ZERO)
                                .build();

                assertThrows(BusinessRuleException.class, () -> dealValidation.validateStageTransition(
                                DealPipelineStage.INQUIRY, DealPipelineStage.QUOTATION_SENT, deal, request));
        }

        @Test
        @DisplayName("UT-DEAL-VAL-07: → NEGOTIATION with short notes → throws")
        void testNegotiationWithShortNotesThrows() {
                DealEntity deal = DealEntity.builder().notes(null).build();
                DealRequest request = DealRequest.builder()
                                .email("contact@hotel.vn")
                                .value(BigDecimal.valueOf(50000000))
                                .notes("Hi")
                                .build();

                assertThrows(BusinessRuleException.class, () -> dealValidation.validateStageTransition(
                                DealPipelineStage.INQUIRY, DealPipelineStage.NEGOTIATION, deal, request));
        }

        @Test
        @DisplayName("UT-DEAL-VAL-08: → PENDING_CONFIRMATION without close date → throws")
        void testPendingConfirmationWithoutDateThrows() {
                DealEntity deal = DealEntity.builder()
                                .expectedCloseDate(null)
                                .notes("Detailed guest requirements for wedding party")
                                .build();
                DealRequest request = DealRequest.builder()
                                .email("contact@hotel.vn")
                                .value(BigDecimal.valueOf(50000000))
                                .notes("Detailed guest requirements for wedding party")
                                .expectedClose(null)
                                .build();

                assertThrows(BusinessRuleException.class, () -> dealValidation.validateStageTransition(
                                DealPipelineStage.INQUIRY, DealPipelineStage.PENDING_CONFIRMATION, deal, request));
        }

        @Test
        @DisplayName("UT-DEAL-VAL-09: → BOOKING_CONFIRMED without booking → throws")
        void testBookingConfirmedWithoutBookingThrows() {
                UUID dealId = UUID.randomUUID();
                DealEntity deal = DealEntity.builder()
                                .dealId(dealId)
                                .notes("Detailed guest requirements for wedding party")
                                .expectedCloseDate(LocalDate.of(2026, 12, 31))
                                .build();
                DealRequest request = DealRequest.builder()
                                .email("contact@hotel.vn")
                                .value(BigDecimal.valueOf(50000000))
                                .notes("Detailed guest requirements for wedding party")
                                .expectedClose(LocalDate.of(2026, 12, 31))
                                .build();

                when(bookingRepository.existsByQuotation_Deal_DealIdAndStatus(eq(dealId), eq(BookingStatus.CONFIRMED)))
                                .thenReturn(false);

                assertThrows(BusinessRuleException.class, () -> dealValidation.validateStageTransition(
                                DealPipelineStage.INQUIRY, DealPipelineStage.BOOKING_CONFIRMED, deal, request));
        }

        @Test
        @DisplayName("UT-DEAL-VAL-10: Standard path BOOKING_CONFIRMED → CLOSED_WON with paid payment → passes")
        void testStandardPathClosedWonPasses() {
                UUID dealId = UUID.randomUUID();
                DealEntity deal = DealEntity.builder()
                                .dealId(dealId)
                                .notes("Detailed guest requirements for wedding party")
                                .expectedCloseDate(LocalDate.of(2026, 12, 31))
                                .build();
                DealRequest request = DealRequest.builder()
                                .notes("Finished payment")
                                .build();

                when(bookingRepository.existsByQuotation_Deal_DealIdAndStatus(eq(dealId), eq(BookingStatus.CONFIRMED)))
                                .thenReturn(true);
                when(dealWorkflowResolver.hasPaidPaymentForActiveBooking(eq(dealId)))
                                .thenReturn(true);

                assertDoesNotThrow(() -> dealValidation.validateStageTransition(
                                DealPipelineStage.BOOKING_CONFIRMED, DealPipelineStage.CLOSED_WON, deal, request));
        }

        @Test
        @DisplayName("UT-DEAL-VAL-11: Exception path CLOSED_WON for non-Manager role → throws")
        void testExceptionPathNonManagerThrows() {
                UUID dealId = UUID.randomUUID();
                DealEntity deal = DealEntity.builder()
                                .dealId(dealId)
                                .build();
                DealRequest request = DealRequest.builder()
                                .notes("Exception requested")
                                .build();

                when(bookingRepository.existsByQuotation_Deal_DealIdAndStatus(eq(dealId), eq(BookingStatus.CONFIRMED)))
                                .thenReturn(false);

                UserEntity employee = UserEntity.builder()
                                .role(RoleEntity.builder().roleName("EMPLOYEE").build())
                                .build();
                when(currentUserProvider.resolve(any())).thenReturn(employee);

                assertThrows(BusinessRuleException.class, () -> dealValidation.validateStageTransition(
                                DealPipelineStage.BOOKING_CONFIRMED, DealPipelineStage.CLOSED_WON, deal, request));
        }

        @Test
        @DisplayName("UT-DEAL-VAL-12: Exception path CLOSED_WON for Admin role → throws")
        void testExceptionPathAdminThrows() {
                UUID dealId = UUID.randomUUID();
                DealEntity deal = DealEntity.builder()
                                .dealId(dealId)
                                .build();
                DealRequest request = DealRequest.builder()
                                .notes("Exception requested")
                                .build();

                when(bookingRepository.existsByQuotation_Deal_DealIdAndStatus(eq(dealId), eq(BookingStatus.CONFIRMED)))
                                .thenReturn(false);

                UserEntity admin = UserEntity.builder()
                                .role(RoleEntity.builder().roleName("ADMIN").build())
                                .build();
                when(currentUserProvider.resolve(any())).thenReturn(admin);

                assertThrows(BusinessRuleException.class, () -> dealValidation.validateStageTransition(
                                DealPipelineStage.BOOKING_CONFIRMED, DealPipelineStage.CLOSED_WON, deal, request));
        }

        @Test
        @DisplayName("UT-DEAL-VAL-13: Exception path CLOSED_WON for Manager with reason too short → throws")
        void testExceptionPathManagerShortReasonThrows() {
                UUID dealId = UUID.randomUUID();
                DealEntity deal = DealEntity.builder()
                                .dealId(dealId)
                                .build();
                DealRequest request = DealRequest.builder()
                                .notes("Byp")
                                .build();

                when(bookingRepository.existsByQuotation_Deal_DealIdAndStatus(eq(dealId), eq(BookingStatus.CONFIRMED)))
                                .thenReturn(false);

                UserEntity manager = UserEntity.builder()
                                .role(RoleEntity.builder().roleName("MANAGER").build())
                                .build();
                when(currentUserProvider.resolve(any())).thenReturn(manager);

                assertThrows(BusinessRuleException.class, () -> dealValidation.validateStageTransition(
                                DealPipelineStage.BOOKING_CONFIRMED, DealPipelineStage.CLOSED_WON, deal, request));
        }

        @Test
        @DisplayName("UT-DEAL-VAL-14: Exception path CLOSED_WON for Manager with valid reason → passes")
        void testExceptionPathManagerValidReasonPasses() {
                UUID dealId = UUID.randomUUID();
                DealEntity deal = DealEntity.builder()
                                .dealId(dealId)
                                .build();
                DealRequest request = DealRequest.builder()
                                .notes("Approved bypass by SM")
                                .build();

                when(bookingRepository.existsByQuotation_Deal_DealIdAndStatus(eq(dealId), eq(BookingStatus.CONFIRMED)))
                                .thenReturn(false);

                UserEntity manager = UserEntity.builder()
                                .role(RoleEntity.builder().roleName("MANAGER").build())
                                .build();
                when(currentUserProvider.resolve(any())).thenReturn(manager);

                assertDoesNotThrow(() -> dealValidation.validateStageTransition(
                                DealPipelineStage.BOOKING_CONFIRMED, DealPipelineStage.CLOSED_WON, deal, request));
        }
}
