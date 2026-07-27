package com.novax.leadora.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResolveTaskRequest {

    @NotBlank(message = "Result note is required to resolve task")
    private String resultNote;
}
