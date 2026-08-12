package com.novax.leadora.api.dto.response;

import java.math.BigDecimal;

public record AspectResultDto(
    String sentiment,
    BigDecimal confidence
) {}
