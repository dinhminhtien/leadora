package com.novax.leadora.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CancelReservationRequest {

    @NotBlank(message = "Lý do hủy đặt phòng không được để trống")
    private String reason;
}
