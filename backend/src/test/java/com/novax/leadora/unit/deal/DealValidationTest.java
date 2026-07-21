package com.novax.leadora.unit.deal;

import com.novax.leadora.api.dto.request.DealRequest;
import com.novax.leadora.application.usecase.deal.DealValidation;
import com.novax.leadora.common.exception.BusinessRuleException;
import com.novax.leadora.infrastructure.persistence.entity.CustomerEntity;
import com.novax.leadora.infrastructure.persistence.entity.DealEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.DealPipelineStage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class DealValidationTest {

    private DealValidation dealValidation;

    @BeforeEach
    void setUp() {
        dealValidation = new DealValidation();
    }

    @Test
    @DisplayName("UT-DEAL-VAL-01: Same stage transition → no exception")
    void testSameStageTransition() {
        DealEntity deal = DealEntity.builder().build();
        DealRequest request = DealRequest.builder().build();

        assertDoesNotThrow(() -> dealValidation.validateStageTransition(
                DealPipelineStage.PROSPECTING, DealPipelineStage.PROSPECTING, deal, request));
    }

    @Test
    @DisplayName("UT-DEAL-VAL-02: PROSPECTING → QUALIFICATION without contact info → throws")
    void testQualificationWithoutContactThrows() {
        DealEntity deal = DealEntity.builder().customer(null).build();
        DealRequest request = DealRequest.builder()
                .email("")
                .phone("")
                .build();

        assertThrows(BusinessRuleException.class, () -> dealValidation.validateStageTransition(
                DealPipelineStage.PROSPECTING, DealPipelineStage.QUALIFICATION, deal, request));
    }

    @Test
    @DisplayName("UT-DEAL-VAL-03: PROSPECTING → QUALIFICATION with email → passes")
    void testQualificationWithEmailPasses() {
        DealEntity deal = DealEntity.builder().build();
        DealRequest request = DealRequest.builder()
                .email("contact@hotel.vn")
                .phone("")
                .build();

        assertDoesNotThrow(() -> dealValidation.validateStageTransition(
                DealPipelineStage.PROSPECTING, DealPipelineStage.QUALIFICATION, deal, request));
    }

    @Test
    @DisplayName("UT-DEAL-VAL-04: PROSPECTING → QUALIFICATION with phone from customer → passes")
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
                DealPipelineStage.PROSPECTING, DealPipelineStage.QUALIFICATION, deal, request));
    }

    @Test
    @DisplayName("UT-DEAL-VAL-05: → PROPOSAL without deal value → throws")
    void testProposalWithoutValueThrows() {
        DealEntity deal = DealEntity.builder().expectedRevenue(null).build();
        DealRequest request = DealRequest.builder()
                .email("contact@hotel.vn")
                .value(null)
                .build();

        assertThrows(BusinessRuleException.class, () -> dealValidation.validateStageTransition(
                DealPipelineStage.PROSPECTING, DealPipelineStage.PROPOSAL, deal, request));
    }

    @Test
    @DisplayName("UT-DEAL-VAL-06: → PROPOSAL with zero value → throws")
    void testProposalWithZeroValueThrows() {
        DealEntity deal = DealEntity.builder().expectedRevenue(BigDecimal.ZERO).build();
        DealRequest request = DealRequest.builder()
                .email("contact@hotel.vn")
                .value(BigDecimal.ZERO)
                .build();

        assertThrows(BusinessRuleException.class, () -> dealValidation.validateStageTransition(
                DealPipelineStage.PROSPECTING, DealPipelineStage.PROPOSAL, deal, request));
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
                DealPipelineStage.PROSPECTING, DealPipelineStage.NEGOTIATION, deal, request));
    }

    @Test
    @DisplayName("UT-DEAL-VAL-08: → CLOSED_WON without close date → throws")
    void testClosedWonWithoutDateThrows() {
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
                DealPipelineStage.PROSPECTING, DealPipelineStage.CLOSED_WON, deal, request));
    }

    @Test
    @DisplayName("UT-DEAL-VAL-09: Full pipeline PROSPECTING → CLOSED_WON with all fields → passes")
    void testFullPipelineTransitionPasses() {
        DealEntity deal = DealEntity.builder()
                .expectedRevenue(BigDecimal.valueOf(100000000))
                .notes("Corporate wedding event 200 guests")
                .expectedCloseDate(LocalDate.of(2026, 12, 31))
                .build();
        DealRequest request = DealRequest.builder()
                .email("corporate@hotel.vn")
                .value(BigDecimal.valueOf(100000000))
                .notes("Corporate wedding event 200 guests")
                .expectedClose(LocalDate.of(2026, 12, 31))
                .build();

        assertDoesNotThrow(() -> dealValidation.validateStageTransition(
                DealPipelineStage.PROSPECTING, DealPipelineStage.CLOSED_WON, deal, request));
    }
}
