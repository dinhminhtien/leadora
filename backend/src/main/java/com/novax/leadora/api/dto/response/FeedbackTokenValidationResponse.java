package com.novax.leadora.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FeedbackTokenValidationResponse {
    private boolean valid;
    private String bookingCode;
    private String customerName;
    private String hotelName;
    private LocalDate checkOutDate;
    private String salesStaffName;
    private String salesStaffAvatar;
    private OffsetDateTime expiresAt;
}
