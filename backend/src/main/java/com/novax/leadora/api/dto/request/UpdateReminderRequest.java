package com.novax.leadora.api.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class UpdateReminderRequest {

    @Size(max = 255)
    private String title;

    private String description;

    @Future(message = "Reminder date must be in the future")
    private OffsetDateTime remindAt;

    @Pattern(regexp = "^(HIGH|MEDIUM|LOW)$", message = "Priority must be HIGH, MEDIUM, or LOW")
    private String priority;

    private Boolean markAsDone;

    private boolean forceIfDone = false;
}
