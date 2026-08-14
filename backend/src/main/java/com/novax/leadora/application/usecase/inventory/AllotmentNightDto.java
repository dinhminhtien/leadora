package com.novax.leadora.application.usecase.inventory;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AllotmentNightDto(
        UUID productId,
        LocalDate stayDate,
        Integer allottedQty,
        Boolean closed,
        OffsetDateTime asOf
) implements Serializable {}
