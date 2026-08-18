package com.novax.leadora.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * What a reconciliation actually changed, night by night.
 *
 * <p>Returned rather than a bare success so the desk can see the correction it just made. A
 * reconciliation that silently accepted every figure would give no signal about how far the CRM
 * had drifted from the hotel — which is the one thing the exercise exists to measure.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AllotmentReconciliationResponse {

    private int nightsReconciled;
    /** Nights whose allotment actually moved — the drift that had built up. */
    private int nightsChanged;
    private List<Change> changes;

    @Getter
    @Builder
    public static class Change {
        private UUID productId;
        private String roomType;
        private LocalDate stayDate;
        /** Null when the night had never been published. */
        private Integer previousAllotted;
        private int newAllotted;
        /** Rooms already sold on this night — the figure the server added back. */
        private int booked;
        private int observedAvailable;
    }
}
