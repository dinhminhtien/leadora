package com.novax.leadora.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Getter
@Setter
public class UpdateStatusRequest {

    @NotBlank(message = "Trạng thái đặt phòng không được để trống")
    private String status;

    private String reason;

    private LocalDate checkInDate;

    private LocalDate checkOutDate;
}
