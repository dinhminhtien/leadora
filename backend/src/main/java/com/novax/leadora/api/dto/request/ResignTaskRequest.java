package com.novax.leadora.api.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
public class ResignTaskRequest {

    private String title;
    private String description;
    private String priority;
    private UUID assignedUserId;
    private String resignNote;
    private OffsetDateTime startAt;
    private OffsetDateTime endAt;
}
