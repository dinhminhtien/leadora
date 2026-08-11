package com.novax.leadora.api.dto.response;

import lombok.*;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SentimentTrendResponse {
    private List<TrendPoint> points;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendPoint {
        private String period; // e.g. "2026-W32" or "2026-08"
        private AspectTrendSummary overall;
        private AspectTrendSummary attitude;
        private AspectTrendSummary speed;
        private AspectTrendSummary accuracy;
        private AspectTrendSummary facility;
        private AspectTrendSummary price;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AspectTrendSummary {
        private long positive;
        private long neutral;
        private long negative;
    }
}
