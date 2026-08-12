package com.novax.leadora.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * UC-23.7 — an AI reading of a rep scorecard.
 *
 * <p>{@link #disclaimer} and {@link #lowConfidenceReps} are built in code, not by the model. The
 * caveats on a document that scores people are the part that must not depend on a language model
 * choosing to comply with its prompt this time.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonDeserialize(builder = RepScorecardAiReviewResponse.RepScorecardAiReviewResponseBuilder.class)
public class RepScorecardAiReviewResponse {

    private LocalDate dateFrom;
    private LocalDate dateTo;
    /** "Whole team" or the reviewed rep's name. */
    private String scope;
    private String language;

    /** Deterministic, code-generated. Rendered above the model's text, never inside it. */
    private String disclaimer;
    /** Named in code so the warning survives the model ignoring rule 3. */
    private List<String> lowConfidenceReps;

    /**
     * The coaching notes as data, ready to lay out. Null when the model answered in a shape that
     * could not be parsed — in which case {@link #review} still carries the text.
     */
    private AiCoachingReview structured;

    /** The model's raw answer, or a canned explanation when the provider was unavailable. */
    private String review;
    /** False when the text above is the canned fallback rather than a generated review. */
    private boolean generated;

    @JsonPOJOBuilder(withPrefix = "")
    public static class RepScorecardAiReviewResponseBuilder {
    }
}
