package com.novax.leadora.infrastructure.persistence.entity.enums;

/**
 * Stage a deal has reached in the sales pipeline.
 *
 * <p>Mapped as a STRING, so every constant here must match a value the database actually holds:
 * a name that exists in one place but not the other makes Hibernate throw
 * {@code No enum constant DealPipelineStage.X} while materialising the row, which takes out the
 * deal listing and — because a quotation is fetched together with its deal — the quotation listing
 * with it.
 */
public enum DealPipelineStage {
    INQUIRY,
    QUALIFICATION,
    QUOTATION_SENT,
    NEGOTIATION,
    PENDING_CONFIRMATION,
    BOOKING_CONFIRMED,
    CLOSED_WON,
    CLOSED_LOST
}
