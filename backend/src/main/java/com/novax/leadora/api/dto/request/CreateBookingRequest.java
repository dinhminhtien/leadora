package com.novax.leadora.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateBookingRequest {

    @NotNull(message = "Quotation ID is required")
    private UUID quotationId;

    @NotNull(message = "Customer ID is required")
    private UUID customerId;

    private UUID assignedUserId;

    @NotNull(message = "Check-in date is required")
    private LocalDate checkInDate;

    @NotNull(message = "Check-out date is required")
    private LocalDate checkOutDate;

    private String specialRequests;

    @NotEmpty(message = "Booking details must not be empty")
    @Valid
    private List<BookingDetailRequest> details;
}
