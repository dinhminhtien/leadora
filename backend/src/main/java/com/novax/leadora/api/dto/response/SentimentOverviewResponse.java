package com.novax.leadora.api.dto.response;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SentimentOverviewResponse {
    private AspectSentimentSummary attitude;
    private AspectSentimentSummary speed;
    private AspectSentimentSummary accuracy;
    private AspectSentimentSummary facility;
    private AspectSentimentSummary price;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AspectSentimentSummary {
        private long positive;
        private long neutral;
        private long negative;
        private int positivePercentage;
        private int neutralPercentage;
        private int negativePercentage;
        private long total;
    }
}
