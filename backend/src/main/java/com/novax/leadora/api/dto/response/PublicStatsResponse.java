package com.novax.leadora.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicStatsResponse {

    private BigDecimal pipelineValueLogged;
    private double weeklySalesGrowthPct;
    private double corporateSlaRatingPct;
    private double directChannelConversionPct;
}
