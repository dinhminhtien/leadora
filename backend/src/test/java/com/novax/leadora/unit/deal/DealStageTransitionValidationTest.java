package com.novax.leadora.unit.deal;
import com.novax.leadora.application.usecase.deal.*;

import com.novax.leadora.api.dto.request.DealRequest;
import com.novax.leadora.common.exception.BusinessException;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.application.usecase.audit.SystemAuditLogService;
import com.novax.leadora.infrastructure.persistence.entity.DealEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.DealPipelineStage;
import com.novax.leadora.infrastructure.persistence.entity.enums.DealStatus;
import com.novax.leadora.infrastructure.persistence.repository.BookingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DealStageTransitionValidationTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private SystemAuditLogService auditLogService;

    @Mock
    private DealWorkflowResolver dealWorkflowResolver;

    @InjectMocks
    private DealValidation dealValidation;

    @Test
    void validateStageTransition_fromClosedWon_throwsStateConflictException() {
        // Arrange
        DealEntity deal = new DealEntity();
        deal.setDealId(UUID.randomUUID());
        DealRequest request = new DealRequest();

        // Act & Assert
        assertThatThrownBy(() -> dealValidation.validateStageTransition(
                DealPipelineStage.CLOSED_WON,
                DealPipelineStage.NEGOTIATION,
                deal,
                request
        ))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> {
            BusinessException be = (BusinessException) ex;
            assertThat(be.getErrorCode()).isEqualTo("DEAL_STATE_CONFLICT");
            assertThat(be.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
        });
    }

    @Test
    void validateStageTransition_toClosedLost_withPaidPayments_throwsConflictException() {
        // Arrange
        DealEntity deal = new DealEntity();
        deal.setDealId(UUID.randomUUID());
        DealRequest request = new DealRequest();
        request.setNotes("Customer cancelled");

        when(dealWorkflowResolver.hasPaidPaymentForActiveBooking(deal.getDealId())).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> dealValidation.validateStageTransition(
                DealPipelineStage.NEGOTIATION,
                DealPipelineStage.CLOSED_LOST,
                deal,
                request
        ))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> {
            BusinessException be = (BusinessException) ex;
            assertThat(be.getErrorCode()).isEqualTo("WORKFLOW_CONSTRAINT_VIOLATION");
            assertThat(be.getHttpStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        });
    }

    @Test
    void validateStatusTransition_fromWon_throwsStateConflictException() {
        // Arrange
        DealEntity deal = new DealEntity();
        deal.setDealId(UUID.randomUUID());

        // Act & Assert
        assertThatThrownBy(() -> dealValidation.validateStatusTransition(
                DealStatus.WON,
                DealStatus.LOST,
                deal,
                "Reason"
        ))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> {
            BusinessException be = (BusinessException) ex;
            assertThat(be.getErrorCode()).isEqualTo("DEAL_STATE_CONFLICT");
            assertThat(be.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
        });
    }

    @Test
    void validateStatusTransition_toLost_withPaidPayments_throwsConflictException() {
        // Arrange
        DealEntity deal = new DealEntity();
        deal.setDealId(UUID.randomUUID());

        when(dealWorkflowResolver.hasPaidPaymentForActiveBooking(deal.getDealId())).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> dealValidation.validateStatusTransition(
                DealStatus.OPEN,
                DealStatus.LOST,
                deal,
                "Reason"
        ))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> {
            BusinessException be = (BusinessException) ex;
            assertThat(be.getErrorCode()).isEqualTo("WORKFLOW_CONSTRAINT_VIOLATION");
            assertThat(be.getHttpStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        });
    }
}
