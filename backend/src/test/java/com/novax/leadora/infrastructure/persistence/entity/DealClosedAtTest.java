package com.novax.leadora.infrastructure.persistence.entity;

import com.novax.leadora.infrastructure.persistence.entity.enums.DealPipelineStage;
import com.novax.leadora.infrastructure.persistence.entity.enums.DealStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code deals.closed_at} is maintained by the entity itself so no use case has to remember it.
 * These cover the three ways that can go wrong: never stamped, stamped twice, or left behind when a
 * deal is reopened.
 */
class DealClosedAtTest {

    private DealEntity openDeal() {
        DealEntity deal = new DealEntity();
        deal.setPipelineStage(DealPipelineStage.INQUIRY);
        return deal;
    }

    @Test
    @DisplayName("an open deal has no close timestamp")
    void openDealIsNotStamped() {
        assertThat(openDeal().getClosedAt()).isNull();
    }

    @Test
    @DisplayName("moving to a terminal stage stamps the close timestamp")
    void closingStampsTheTimestamp() {
        DealEntity deal = openDeal();

        deal.setPipelineStage(DealPipelineStage.CLOSED_WON);

        assertThat(deal.getStatus()).isEqualTo(DealStatus.WON);
        assertThat(deal.getClosedAt()).isNotNull();
    }

    @Test
    @DisplayName("setting the status directly stamps it too")
    void closingViaStatusStampsTheTimestamp() {
        DealEntity deal = openDeal();

        deal.setStatus(DealStatus.LOST);

        assertThat(deal.getPipelineStage()).isEqualTo(DealPipelineStage.CLOSED_LOST);
        assertThat(deal.getClosedAt()).isNotNull();
    }

    @Test
    @DisplayName("re-saving a closed deal does not move its close date into the current period")
    void resavingDoesNotRewriteHistory() {
        DealEntity deal = openDeal();
        deal.setPipelineStage(DealPipelineStage.CLOSED_WON);
        OffsetDateTime originalClose = deal.getClosedAt();

        // A correction (BR-44) touches the record months later; the JPA lifecycle hook fires again.
        deal.setNotes("corrected by manager");
        deal.syncStatusWithPipelineStage();
        deal.setPipelineStage(DealPipelineStage.CLOSED_WON);

        assertThat(deal.getClosedAt())
                .as("otherwise a deal won in May silently becomes an outcome of the month it was edited")
                .isEqualTo(originalClose);
    }

    @Test
    @DisplayName("reopening a deal clears the close timestamp")
    void reopeningClearsTheTimestamp() {
        DealEntity deal = openDeal();
        deal.setPipelineStage(DealPipelineStage.CLOSED_WON);
        assertThat(deal.getClosedAt()).isNotNull();

        deal.setPipelineStage(DealPipelineStage.NEGOTIATION);

        assertThat(deal.getStatus()).isEqualTo(DealStatus.OPEN);
        assertThat(deal.getClosedAt())
                .as("a reopened deal is not an outcome of the month it was previously closed in")
                .isNull();
    }

    @Test
    @DisplayName("reopening then closing again stamps the new close date")
    void reclosingStampsAfresh() {
        DealEntity deal = openDeal();
        deal.setPipelineStage(DealPipelineStage.CLOSED_LOST);
        OffsetDateTime firstClose = deal.getClosedAt();

        deal.setPipelineStage(DealPipelineStage.NEGOTIATION);
        deal.setPipelineStage(DealPipelineStage.CLOSED_WON);

        assertThat(deal.getClosedAt()).isNotNull().isAfterOrEqualTo(firstClose);
    }
}
