package com.novax.leadora.api.dto.request;

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

    private OffsetDateTime remindAt;

    private String priority;

    private Boolean markAsDone;

    private boolean forceIfDone = false;
}
