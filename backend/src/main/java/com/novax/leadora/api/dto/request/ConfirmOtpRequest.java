package com.novax.leadora.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConfirmOtpRequest {
    @NotBlank(message = "OTP code cannot be blank")
    private String otpCode;
}
