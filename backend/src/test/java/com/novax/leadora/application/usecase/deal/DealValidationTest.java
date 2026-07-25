package com.novax.leadora.application.usecase.deal;

import com.novax.leadora.api.dto.request.DealRequest;
import com.novax.leadora.common.exception.BusinessRuleException;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.application.usecase.audit.SystemAuditLogService;
import com.novax.leadora.infrastructure.persistence.entity.DealEntity;
import com.novax.leadora.infrastructure.persistence.entity.RoleEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.DealPipelineStage;
import com.novax.leadora.infrastructure.persistence.entity.enums.DealStatus;
import com.novax.leadora.infrastructure.persistence.entity.enums.BookingStatus;
import com.novax.leadora.infrastructure.persistence.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DealValidationTest {

        @Mock
        private BookingRepository bookingRepository;

        @Mock
        private CurrentUserProvider currentUserProvider;

        @Mock
        private SystemAuditLogService auditLogService;

        private DealValidation dealValidation;

        @BeforeEach
        void setUp() {
                dealValidation = new DealValidation(bookingRepository, currentUserProvider, auditLogService);
        }

        @Test
        void validateStageTransition_closedWonWithConfirmedBooking_shouldPass() {
                UUID dealId = UUID.randomUUID();
                DealEntity deal = DealEntity.builder()
                                .dealId(dealId)
                                .pipelineStage(DealPipelineStage.NEGOTIATION)
                                .status(DealStatus.OPEN)
                                .build();

                DealRequest request = new DealRequest();
                request.setStage("CLOSED_WON");
                request.setExpectedClose(LocalDate.now());

                when(bookingRepository.existsByQuotation_Deal_DealIdAndStatus(dealId, BookingStatus.CONFIRMED))
                                .thenReturn(true);

                dealValidation.validateStageTransition(DealPipelineStage.NEGOTIATION, DealPipelineStage.CLOSED_WON,
                                deal, request);

                verify(bookingRepository).existsByQuotation_Deal_DealIdAndStatus(dealId, BookingStatus.CONFIRMED);
        }

        @Test
        void validateStageTransition_closedWonNoBookingSalesRole_shouldThrowException() {
                UUID dealId = UUID.randomUUID();
                DealEntity deal = DealEntity.builder()
                                .dealId(dealId)
                                .pipelineStage(DealPipelineStage.NEGOTIATION)
                                .status(DealStatus.OPEN)
                                .build();

                DealRequest request = new DealRequest();
                request.setStage("CLOSED_WON");
                request.setExpectedClose(LocalDate.now());

                when(bookingRepository.existsByQuotation_Deal_DealIdAndStatus(dealId, BookingStatus.CONFIRMED))
                                .thenReturn(false);

                UserEntity currentUser = UserEntity.builder()
                                .role(RoleEntity.builder().roleName("SALES").build())
                                .fullName("Sales Agent")
                                .build();
                when(currentUserProvider.resolve(null)).thenReturn(currentUser);

                assertThatThrownBy(() -> dealValidation.validateStageTransition(DealPipelineStage.NEGOTIATION,
                                DealPipelineStage.CLOSED_WON, deal, request))
                                .isInstanceOf(BusinessRuleException.class)
                                .hasMessageContaining("A confirmed booking is required to mark a deal as Closed Won.");
        }

        @Test
        void validateStageTransition_closedWonNoBookingManagerRoleNoReason_shouldThrowException() {
                UUID dealId = UUID.randomUUID();
                DealEntity deal = DealEntity.builder()
                                .dealId(dealId)
                                .pipelineStage(DealPipelineStage.NEGOTIATION)
                                .status(DealStatus.OPEN)
                                .build();

                DealRequest request = new DealRequest();
                request.setStage("CLOSED_WON");
                request.setExpectedClose(LocalDate.now());

                when(bookingRepository.existsByQuotation_Deal_DealIdAndStatus(dealId, BookingStatus.CONFIRMED))
                                .thenReturn(false);

                UserEntity currentUser = UserEntity.builder()
                                .role(RoleEntity.builder().roleName("MANAGER").build())
                                .fullName("Manager User")
                                .build();
                when(currentUserProvider.resolve(null)).thenReturn(currentUser);

                assertThatThrownBy(() -> dealValidation.validateStageTransition(DealPipelineStage.NEGOTIATION,
                                DealPipelineStage.CLOSED_WON, deal, request))
                                .isInstanceOf(BusinessRuleException.class)
                                .hasMessageContaining(
                                                "A manager exception reason (at least 5 characters) must be provided in the Notes");
        }

        @Test
        void validateStageTransition_closedWonNoBookingManagerRoleWithReason_shouldPassAndAudit() {
                UUID dealId = UUID.randomUUID();
                DealEntity deal = DealEntity.builder()
                                .dealId(dealId)
                                .pipelineStage(DealPipelineStage.NEGOTIATION)
                                .status(DealStatus.OPEN)
                                .build();

                DealRequest request = new DealRequest();
                request.setStage("CLOSED_WON");
                request.setExpectedClose(LocalDate.now());
                request.setNotes("Bypassing because of client exception");

                when(bookingRepository.existsByQuotation_Deal_DealIdAndStatus(dealId, BookingStatus.CONFIRMED))
                                .thenReturn(false);

                UserEntity currentUser = UserEntity.builder()
                                .userId(UUID.randomUUID())
                                .role(RoleEntity.builder().roleName("MANAGER").build())
                                .fullName("Manager User")
                                .build();
                when(currentUserProvider.resolve(null)).thenReturn(currentUser);

                dealValidation.validateStageTransition(DealPipelineStage.NEGOTIATION, DealPipelineStage.CLOSED_WON,
                                deal, request);

                verify(auditLogService).log(eq("DEAL"), eq("Deal"), eq(dealId), eq("CLOSED_WON_EXCEPTION"),
                                eq(currentUser), any(), eq("WON"), anyString());
        }

        @Test
        void validateStageTransition_closedLostNoReason_shouldThrowException() {
                UUID dealId = UUID.randomUUID();
                DealEntity deal = DealEntity.builder()
                                .dealId(dealId)
                                .pipelineStage(DealPipelineStage.NEGOTIATION)
                                .status(DealStatus.OPEN)
                                .build();

                DealRequest request = new DealRequest();
                request.setStage("CLOSED_LOST");

                assertThatThrownBy(() -> dealValidation.validateStageTransition(DealPipelineStage.NEGOTIATION,
                                DealPipelineStage.CLOSED_LOST, deal, request))
                                .isInstanceOf(BusinessRuleException.class)
                                .hasMessageContaining(
                                                "A closed-lost reason must be provided in the Notes/Reason field to mark a deal as Closed Lost.");
        }

        @Test
        void validateStageTransition_closedLostWithReason_shouldPass() {
                UUID dealId = UUID.randomUUID();
                DealEntity deal = DealEntity.builder()
                                .dealId(dealId)
                                .pipelineStage(DealPipelineStage.NEGOTIATION)
                                .status(DealStatus.OPEN)
                                .build();

                DealRequest request = new DealRequest();
                request.setStage("CLOSED_LOST");
                request.setNotes("Client went with competitor");

                dealValidation.validateStageTransition(DealPipelineStage.NEGOTIATION, DealPipelineStage.CLOSED_LOST,
                                deal, request);
        }
}
