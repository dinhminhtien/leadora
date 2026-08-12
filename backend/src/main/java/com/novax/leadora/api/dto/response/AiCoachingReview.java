package com.novax.leadora.api.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * UC-23.7 — the coaching review as data rather than prose.
 *
 * <p>The model used to answer in markdown and the screen rendered it as a blob. Asking for these
 * fields instead lets each one land in the component built for it — a strength beside the metric it
 * came from, an action in an action card — and it makes the output checkable: a missing section is
 * a null field the UI can notice, not a heading the model quietly skipped.
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} is not laziness. A language model
 * occasionally adds a helpful extra key, and the difference between ignoring it and throwing is the
 * difference between a review with one unused field and no review at all.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(builder = AiCoachingReview.AiCoachingReviewBuilder.class)
public class AiCoachingReview {

    private List<RepCoaching> reps;
    /** Patterns across the team. Empty when only one rep was reviewed. */
    private List<String> teamRead;

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AiCoachingReviewBuilder {
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonDeserialize(builder = RepCoaching.RepCoachingBuilder.class)
    public static class RepCoaching {
        private String name;
        /** One sentence summing the period up. */
        private String headline;
        private List<String> strengths;
        private List<String> needsWork;
        private List<CoachingAction> actions;
        /** What the score rests on, and what could not be measured. */
        private String evidenceNote;

        @JsonPOJOBuilder(withPrefix = "")
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class RepCoachingBuilder {
        }
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonDeserialize(builder = CoachingAction.CoachingActionBuilder.class)
    public static class CoachingAction {
        private String action;
        /** The metric this action is meant to move — rendered as a chip beside it. */
        private String metric;

        @JsonPOJOBuilder(withPrefix = "")
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class CoachingActionBuilder {
        }
    }
}
