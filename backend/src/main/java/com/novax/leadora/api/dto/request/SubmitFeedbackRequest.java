package com.novax.leadora.api.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SubmitFeedbackRequest {

    @NotNull(message = "Rating is required")
    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 5, message = "Rating must be at most 5")
    private Short rating;

    @Min(value = 1, message = "Attitude rating must be at least 1")
    @Max(value = 5, message = "Attitude rating must be at most 5")
    private Short ratingAttitude;

    @Min(value = 1, message = "Speed rating must be at least 1")
    @Max(value = 5, message = "Speed rating must be at most 5")
    private Short ratingSpeed;

    @Min(value = 1, message = "Accuracy rating must be at least 1")
    @Max(value = 5, message = "Accuracy rating must be at most 5")
    private Short ratingAccuracy;

    @NotBlank(message = "Comment is required")
    private String comment;

    @Min(value = 0, message = "Recommend score must be at least 0")
    @Max(value = 10, message = "Recommend score must be at most 10")
    private Integer recommendScore;
}
