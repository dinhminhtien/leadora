package com.novax.leadora.infrastructure.persistence.entity.enums;

/**
 * Stage a deal has reached in the sales pipeline.
 *
 * <p><b>This list is currently a union of two designs, on purpose.</b> The team renamed the
 * pipeline on {@code dev} and migrated the shared database to the new names; this branch is a long
 * way behind and still held only the old ones. Since the stage is mapped as a STRING, every deal
 * written since that migration became unreadable here — Hibernate threw
 * {@code No enum constant DealPipelineStage.INQUIRY} while materialising the row, which took out
 * the deal listing and, because a quotation is fetched together with its deal, the quotation
 * listing as well. Nothing in the assistant could show either.
 *
 * <p>The first eight constants are the current pipeline and match {@code dev} exactly. The last two
 * are the superseded names; no row in the database holds them any more, but code on this branch
 * ({@code DealMapper}, {@code DealValidation}, {@code CreateDealUseCase}) still refers to them, and
 * rewriting that logic here would mean guessing at a mapping the deal module has already defined
 * properly on {@code dev}.
 *
 * <p><b>When this branch is merged with {@code dev}, delete the two superseded constants</b> and
 * take the deal module's own version of the mapping with them. They exist only to keep this branch
 * compiling until then.
 */
public enum DealPipelineStage {

    // ── Current pipeline (matches dev and the database) ──────────────────────
    INQUIRY,
    QUALIFICATION,
    QUOTATION_SENT,
    NEGOTIATION,
    PENDING_CONFIRMATION,
    BOOKING_CONFIRMED,
    CLOSED_WON,
    CLOSED_LOST,

    // ── Superseded; retained only so this branch's deal module still compiles ──
    PROSPECTING,
    PROPOSAL
}
