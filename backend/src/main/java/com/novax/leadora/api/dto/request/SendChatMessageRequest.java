package com.novax.leadora.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** UC — Query CRM Data / Continue Chat. A single user turn sent to the assistant. */
@Data
public class SendChatMessageRequest {

    @NotBlank(message = "Message content must not be blank")
    @Size(max = 4000, message = "Message must be at most 4000 characters")
    private String content;
}
