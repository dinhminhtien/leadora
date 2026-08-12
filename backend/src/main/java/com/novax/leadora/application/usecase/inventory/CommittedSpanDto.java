package com.novax.leadora.application.usecase.inventory;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;

public record CommittedSpanDto(
        UUID productId,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        Integer quantity
) implements Serializable {}
