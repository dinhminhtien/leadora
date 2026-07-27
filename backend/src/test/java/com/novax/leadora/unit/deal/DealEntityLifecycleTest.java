package com.novax.leadora.unit.deal;

import com.novax.leadora.infrastructure.persistence.entity.DealEntity;
import com.novax.leadora.infrastructure.persistence.entity.enums.DealPipelineStage;
import com.novax.leadora.infrastructure.persistence.entity.enums.DealStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DealEntityLifecycleTest {

    @Test
    @DisplayName("UT-DEAL-LIFE-01: Setting pipelineStage to CLOSED_WON syncs status to WON")
    void testSetPipelineStageClosedWonSyncsStatus() {
        DealEntity deal = new DealEntity();
        deal.setPipelineStage(DealPipelineStage.CLOSED_WON);

        assertEquals(DealPipelineStage.CLOSED_WON, deal.getPipelineStage());
        assertEquals(DealStatus.WON, deal.getStatus());
    }

    @Test
    @DisplayName("UT-DEAL-LIFE-02: Setting pipelineStage to CLOSED_LOST syncs status to LOST")
    void testSetPipelineStageClosedLostSyncsStatus() {
        DealEntity deal = new DealEntity();
        deal.setPipelineStage(DealPipelineStage.CLOSED_LOST);

        assertEquals(DealPipelineStage.CLOSED_LOST, deal.getPipelineStage());
        assertEquals(DealStatus.LOST, deal.getStatus());
    }

    @Test
    @DisplayName("UT-DEAL-LIFE-03: Setting pipelineStage to an open stage syncs status to OPEN")
    void testSetPipelineStageOpenSyncsStatus() {
        DealEntity deal = new DealEntity();
        deal.setPipelineStage(DealPipelineStage.INQUIRY);

        assertEquals(DealPipelineStage.INQUIRY, deal.getPipelineStage());
        assertEquals(DealStatus.OPEN, deal.getStatus());
    }

    @Test
    @DisplayName("UT-DEAL-LIFE-04: Setting status to WON syncs pipelineStage to CLOSED_WON")
    void testSetStatusWonSyncsPipelineStage() {
        DealEntity deal = new DealEntity();
        deal.setStatus(DealStatus.WON);

        assertEquals(DealStatus.WON, deal.getStatus());
        assertEquals(DealPipelineStage.CLOSED_WON, deal.getPipelineStage());
    }

    @Test
    @DisplayName("UT-DEAL-LIFE-05: Setting status to LOST syncs pipelineStage to CLOSED_LOST")
    void testSetStatusLostSyncsPipelineStage() {
        DealEntity deal = new DealEntity();
        deal.setStatus(DealStatus.LOST);

        assertEquals(DealStatus.LOST, deal.getStatus());
        assertEquals(DealPipelineStage.CLOSED_LOST, deal.getPipelineStage());
    }

    @Test
    @DisplayName("UT-DEAL-LIFE-06: PrePersist/PreUpdate hook syncs status with pipelineStage")
    void testSyncStatusWithPipelineStageHook() {
        // Direct setting bypasses setters for testing hook backstop
        DealEntity deal = DealEntity.builder()
                .pipelineStage(DealPipelineStage.CLOSED_WON)
                .status(DealStatus.OPEN) // Desynced state
                .build();

        deal.syncStatusWithPipelineStage();

        assertEquals(DealStatus.WON, deal.getStatus());
    }
}
