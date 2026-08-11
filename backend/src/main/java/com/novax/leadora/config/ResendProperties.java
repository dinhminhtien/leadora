package com.novax.leadora.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.time.Duration;

@ConfigurationProperties(prefix = "resend")
@Validated
public record ResendProperties(
    @NotBlank(message = "Resend API key is required")
    String apiKey,
    @NotBlank(message = "Resend default-from address is required")
    String defaultFrom,
    @NotBlank(message = "Resend base URL is required")
    String baseUrl,
    @NotNull(message = "Resend timeout is required")
    Duration timeout
) {}
