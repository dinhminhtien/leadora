package com.novax.leadora.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CustomerHistoryItem(
        /** "DEAL", "BOOKING", or "QUOTATION" */
        String type,
        String id,
        String title,
        String status,
        /** Pipeline stage label — deals only */
        String stage,
        BigDecimal amount,
        /** ISO date string — for bookings/quotations: checkInDate */
        String checkIn,
        /** ISO date string — for bookings/quotations: checkOutDate */
        String checkOut,
        /** ISO date string — deals: expectedCloseDate */
        String expectedClose,
        /** ISO datetime string — used for chronological sorting */
        String createdAt,
        String notes
) {}
