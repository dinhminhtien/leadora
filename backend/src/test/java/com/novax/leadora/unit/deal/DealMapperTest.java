package com.novax.leadora.unit.deal;
import com.novax.leadora.application.usecase.deal.*;

import com.novax.leadora.infrastructure.persistence.entity.enums.DealPipelineStage;
import com.novax.leadora.infrastructure.persistence.entity.enums.DealStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class DealMapperTest {

    private DealMapper dealMapper;

    @BeforeEach
    void setUp() {
        dealMapper = new DealMapper();
        ReflectionTestUtils.setField(dealMapper, "inquiryProbability", 10);
        ReflectionTestUtils.setField(dealMapper, "qualificationProbability", 30);
        ReflectionTestUtils.setField(dealMapper, "quotationSentProbability", 50);
        ReflectionTestUtils.setField(dealMapper, "negotiationProbability", 70);
        ReflectionTestUtils.setField(dealMapper, "pendingConfirmationProbability", 80);
        ReflectionTestUtils.setField(dealMapper, "bookingConfirmedProbability", 90);
    }

    @Test
    @DisplayName("UT-DEAL-MAP-01: Map stage strings to enum")
    void testMapStageToEnum() {
        assertEquals(DealPipelineStage.INQUIRY, dealMapper.mapStageToEnum("inquiry"));
        assertEquals(DealPipelineStage.QUALIFICATION, dealMapper.mapStageToEnum("site visit"));
        assertEquals(DealPipelineStage.QUALIFICATION, dealMapper.mapStageToEnum("qualification"));
        assertEquals(DealPipelineStage.QUOTATION_SENT, dealMapper.mapStageToEnum("proposal"));
        assertEquals(DealPipelineStage.NEGOTIATION, dealMapper.mapStageToEnum("negotiation"));
        assertEquals(DealPipelineStage.PENDING_CONFIRMATION, dealMapper.mapStageToEnum("contract"));
        assertEquals(DealPipelineStage.BOOKING_CONFIRMED, dealMapper.mapStageToEnum("confirmed"));
        assertEquals(DealPipelineStage.INQUIRY, dealMapper.mapStageToEnum(null));
        assertEquals(DealPipelineStage.CLOSED_WON, dealMapper.mapStageToEnum("CLOSED_WON"));
    }

    @Test
    @DisplayName("UT-DEAL-MAP-02: Map enum stages to strings for Kanban display")
    void testMapStageToString() {
        assertEquals("Inquiry", dealMapper.mapStageToString(DealPipelineStage.INQUIRY, DealStatus.OPEN));
        assertEquals("Qualification", dealMapper.mapStageToString(DealPipelineStage.QUALIFICATION, DealStatus.OPEN));
        assertEquals("Proposal", dealMapper.mapStageToString(DealPipelineStage.QUOTATION_SENT, DealStatus.OPEN));
        assertEquals("Negotiation", dealMapper.mapStageToString(DealPipelineStage.NEGOTIATION, DealStatus.OPEN));
        assertEquals("Contract", dealMapper.mapStageToString(DealPipelineStage.PENDING_CONFIRMATION, DealStatus.OPEN));
        assertEquals("Confirmed", dealMapper.mapStageToString(DealPipelineStage.BOOKING_CONFIRMED, DealStatus.OPEN));
        assertEquals("Confirmed", dealMapper.mapStageToString(DealPipelineStage.CLOSED_WON, DealStatus.WON));
        assertEquals("Confirmed", dealMapper.mapStageToString(DealPipelineStage.CLOSED_LOST, DealStatus.LOST));
        assertEquals("Inquiry", dealMapper.mapStageToString(null, DealStatus.OPEN));
    }

    @Test
    @DisplayName("UT-DEAL-MAP-03: Calculate probability for different stages and statuses")
    void testCalculateProbability() {
        assertEquals(100, dealMapper.calculateProbability(DealPipelineStage.INQUIRY, DealStatus.WON));
        assertEquals(0, dealMapper.calculateProbability(DealPipelineStage.BOOKING_CONFIRMED, DealStatus.LOST));
        assertEquals(10, dealMapper.calculateProbability(DealPipelineStage.INQUIRY, DealStatus.OPEN));
        assertEquals(30, dealMapper.calculateProbability(DealPipelineStage.QUALIFICATION, DealStatus.OPEN));
        assertEquals(50, dealMapper.calculateProbability(DealPipelineStage.QUOTATION_SENT, DealStatus.OPEN));
        assertEquals(70, dealMapper.calculateProbability(DealPipelineStage.NEGOTIATION, DealStatus.OPEN));
        assertEquals(80, dealMapper.calculateProbability(DealPipelineStage.PENDING_CONFIRMATION, DealStatus.OPEN));
        assertEquals(90, dealMapper.calculateProbability(DealPipelineStage.BOOKING_CONFIRMED, DealStatus.OPEN));
        assertEquals(100, dealMapper.calculateProbability(DealPipelineStage.CLOSED_WON, DealStatus.OPEN));
        assertEquals(0, dealMapper.calculateProbability(DealPipelineStage.CLOSED_LOST, DealStatus.OPEN));
    }
}
