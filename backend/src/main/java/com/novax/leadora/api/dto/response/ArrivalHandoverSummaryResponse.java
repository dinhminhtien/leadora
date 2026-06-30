package com.novax.leadora.api.dto.response;

import lombok.Builder;
import lombok.Getter;

/** Front Office desk summary (UC-22.1) — counts of arrival handovers by readiness. */
@Getter
@Builder
public class ArrivalHandoverSummaryResponse {
    private long total;
    private long pendingReview;
    private long reviewed;
    private long readyForArrival;
    private long needClarification;
}
