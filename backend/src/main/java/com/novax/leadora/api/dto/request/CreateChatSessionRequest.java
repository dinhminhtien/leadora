package com.novax.leadora.api.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** UC — Create New Chat Session. Title is optional; auto-generated from the first message if blank. */
@Data
public class CreateChatSessionRequest {

    @Size(max = 255, message = "Title must be at most 255 characters")
    private String title;
}
