package com.novax.leadora.unit.reporting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novax.leadora.api.dto.response.RepScorecardAiReviewResponse;
import com.novax.leadora.api.dto.response.RepScorecardResponse;
import com.novax.leadora.api.dto.response.RepScorecardResponse.RepMetrics;
import com.novax.leadora.api.dto.response.RepScorecardResponse.RepScore;
import com.novax.leadora.api.dto.response.RepScorecardResponse.RepScorecard;
import com.novax.leadora.api.dto.response.RepScorecardResponse.TeamBaseline;
import com.novax.leadora.application.usecase.reporting.GetRepScorecardUseCase;
import com.novax.leadora.application.usecase.reporting.RepScorecardAiReviewUseCase;
import com.novax.leadora.common.exception.BusinessRuleException;
import com.novax.leadora.infrastructure.integration.ai.ScorecardAdvisorLlmService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** UC-23.7 — what the model is given, and what stays true when it does not answer. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RepScorecardAiReviewUseCaseTest {

    @Mock
    private GetRepScorecardUseCase getRepScorecardUseCase;
    @Mock
    private ScorecardAdvisorLlmService advisorLlmService;

    private RepScorecardAiReviewUseCase useCase;

    private static final UUID ANNA = UUID.randomUUID();
    private static final UUID BINH = UUID.randomUUID();
    private static final LocalDate FROM = LocalDate.of(2026, 7, 1);
    private static final LocalDate TO = LocalDate.of(2026, 7, 30);

    @BeforeEach
    void setUp() {
        useCase = new RepScorecardAiReviewUseCase(
                getRepScorecardUseCase, advisorLlmService, new ObjectMapper());
        when(advisorLlmService.review(anyString(), anyBoolean())).thenReturn("### Anna — 70 / 100");
    }

    private void scorecard(RepScorecard... reps) {
        when(getRepScorecardUseCase.execute(any(), any())).thenReturn(RepScorecardResponse.builder()
                .dateFrom(FROM)
                .dateTo(TO)
                .periodMonths(1.0)
                .timezone("Asia/Ho_Chi_Minh")
                .weights(RepScorecardResponse.Weights.builder()
                        .outcome(30).efficiency(25).velocity(15).discipline(20).quality(10).build())
                .team(TeamBaseline.builder().repCount(reps.length).winRate(55.0).medianScore(60.0).build())
                .reps(List.of(reps))
                .build());
    }

    private static RepScorecard rep(UUID id, String name, boolean lowConfidence) {
        return RepScorecard.builder()
                .userId(id)
                .name(name)
                .lowConfidence(lowConfidence)
                .ranked(!lowConfidence)
                .metrics(RepMetrics.builder()
                        .revenue(BigDecimal.valueOf(90_000_000))
                        .dealsWon(6).dealsClosed(8).winRate(75.0)
                        .csat(null).csatSamples(0)
                        .activeDays(22).sampleSize(lowConfidence ? 2 : 9)
                        .build())
                .score(RepScore.builder()
                        .outcome(80.0).efficiency(70.0).quality(null)
                        .total(74.2).weightCovered(90.0).build())
                .dataGaps(List.of("No customer feedback was received — the quality axis is not scored."))
                .build();
    }

    private String capturedPayload() {
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(advisorLlmService).review(payload.capture(), anyBoolean());
        return payload.getValue();
    }

    @Test
    @DisplayName("an empty period never reaches the model")
    void emptyPeriodIsNotSentToTheModel() {
        scorecard();

        RepScorecardAiReviewResponse review = useCase.execute(FROM, TO, null, true);

        verify(advisorLlmService, never()).review(anyString(), anyBoolean());
        assertThat(review.isGenerated())
                .as("asking a model to review nobody produces a fluent review of nobody")
                .isFalse();
        assertThat(review.getReview()).contains("chưa có gì để nhận xét");
    }

    @Test
    @DisplayName("the model is given the numbers and nothing a caller typed")
    void payloadIsBuiltFromTheScorecard() {
        scorecard(rep(ANNA, "Anna", false));

        useCase.execute(FROM, TO, null, true);
        String payload = capturedPayload();

        assertThat(payload).contains("\"name\":\"Anna\"", "\"winRatePct\":75.0", "\"dealsWon\":6");
        assertThat(payload).contains("\"axisWeights\"", "\"teamBaseline\"", "\"periodMonths\":1.0");
        assertThat(payload)
                .as("user ids are pure tokens to a language model")
                .doesNotContain(ANNA.toString());
    }

    @Test
    @DisplayName("an unmeasured metric is sent as an explicit null, never as a zero")
    void nullsSurviveIntoThePayload() {
        scorecard(rep(ANNA, "Anna", false));

        useCase.execute(FROM, TO, null, true);
        String payload = capturedPayload();

        assertThat(payload)
                .as("the prompt's rule about nulls only works if the model can see one")
                .contains("\"csat\":null", "\"quality\":null");
        assertThat(payload).contains("dataGaps");
    }

    @Test
    @DisplayName("reviewing one rep sends only that rep")
    void singleRepScopeIsNarrowed() {
        scorecard(rep(ANNA, "Anna", false), rep(BINH, "Binh", false));

        RepScorecardAiReviewResponse review = useCase.execute(FROM, TO, BINH, true);

        assertThat(capturedPayload()).contains("Binh").doesNotContain("Anna");
        assertThat(review.getScope()).isEqualTo("Binh");
    }

    @Test
    @DisplayName("a rep with no scorecard in the period is refused rather than silently widened")
    void unknownRepIsRefused() {
        scorecard(rep(ANNA, "Anna", false));

        assertThatThrownBy(() -> useCase.execute(FROM, TO, UUID.randomUUID(), true))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no scorecard");
    }

    @Test
    @DisplayName("the thin-evidence warning is produced in code, not asked of the model")
    void lowConfidenceIsNamedByTheServer() {
        scorecard(rep(ANNA, "Anna", true), rep(BINH, "Binh", false));

        RepScorecardAiReviewResponse review = useCase.execute(FROM, TO, null, true);

        assertThat(review.getLowConfidenceReps()).containsExactly("Anna");
        assertThat(review.getDisclaimer())
                .as("a caveat that depends on the model complying is not a caveat")
                .contains("không phải đánh giá nhân sự");
    }

    @Test
    @DisplayName("the disclaimer follows the requested language")
    void disclaimerIsLocalised() {
        scorecard(rep(ANNA, "Anna", false));

        assertThat(useCase.execute(FROM, TO, null, false).getDisclaimer())
                .contains("not a personnel assessment");
    }

    @Test
    @DisplayName("an exhausted LLM quota explains itself instead of failing the request")
    void quotaExhaustionDegradesGracefully() {
        scorecard(rep(ANNA, "Anna", false));
        when(advisorLlmService.review(anyString(), anyBoolean()))
                .thenThrow(new RuntimeException("429 RESOURCE_EXHAUSTED: quota exceeded"));

        RepScorecardAiReviewResponse review = useCase.execute(FROM, TO, null, true);

        assertThat(review.isGenerated()).isFalse();
        assertThat(review.getReview()).isNotBlank();
        assertThat(review.getDisclaimer())
                .as("the caveats still stand when the paragraph does not")
                .isNotBlank();
        assertThat(review.getLowConfidenceReps()).isNotNull();
    }

    @Test
    @DisplayName("a generated review carries the period it was written about")
    void reviewCarriesItsPeriod() {
        scorecard(rep(ANNA, "Anna", false));

        RepScorecardAiReviewResponse review = useCase.execute(FROM, TO, null, true);

        assertThat(review.isGenerated()).isTrue();
        assertThat(review.getDateFrom()).isEqualTo(FROM);
        assertThat(review.getDateTo()).isEqualTo(TO);
        assertThat(review.getScope()).isEqualTo("Whole team");
        assertThat(review.getLanguage()).isEqualTo("vi");
    }

    @Test
    @DisplayName("the review comes back as data the screen can lay out")
    void structuredReviewIsParsed() {
        scorecard(rep(ANNA, "Anna", false));
        when(advisorLlmService.review(anyString(), anyBoolean())).thenReturn("""
                {"reps":[{"name":"Anna","headline":"Strong close rate on a small book.",
                "strengths":["Win rate 75% on 8 decided deals"],
                "needsWork":["No customer feedback collected"],
                "actions":[{"action":"Ask two closed customers for feedback","metric":"CSAT"},
                           {"action":"Send quotations within a day","metric":"Quotation turnaround"},
                           {"action":"Log first contact on every new lead","metric":"First response"}],
                "evidenceNote":"9 settled outcomes; quality axis not measured."}],
                "teamRead":["Feedback collection is thin across the team."]}
                """);

        RepScorecardAiReviewResponse review = useCase.execute(FROM, TO, null, false);

        assertThat(review.getStructured()).isNotNull();
        assertThat(review.getStructured().getReps()).singleElement().satisfies(rep -> {
            assertThat(rep.getName()).isEqualTo("Anna");
            assertThat(rep.getStrengths()).hasSize(1);
            assertThat(rep.getActions()).hasSize(3);
            assertThat(rep.getActions().get(0).getMetric()).isEqualTo("CSAT");
            assertThat(rep.getEvidenceNote()).contains("9 settled outcomes");
        });
        assertThat(review.getStructured().getTeamRead()).hasSize(1);
    }

    @Test
    @DisplayName("a fenced or chatty answer is still parsed")
    void codeFenceAndPreambleAreStripped() {
        scorecard(rep(ANNA, "Anna", false));
        when(advisorLlmService.review(anyString(), anyBoolean())).thenReturn("""
                Sure! Here is the review:
                ```json
                {"reps":[{"name":"Anna"}]}
                ```
                """);

        assertThat(useCase.execute(FROM, TO, null, false).getStructured().getReps())
                .singleElement()
                .satisfies(rep -> assertThat(rep.getName()).isEqualTo("Anna"));
    }

    @Test
    @DisplayName("an unparseable answer keeps the advice and loses only the layout")
    void unparseableAnswerFallsBackToText() {
        scorecard(rep(ANNA, "Anna", false));
        when(advisorLlmService.review(anyString(), anyBoolean()))
                .thenReturn("### Anna\nShe had a good month.");

        RepScorecardAiReviewResponse review = useCase.execute(FROM, TO, null, false);

        assertThat(review.getStructured()).isNull();
        assertThat(review.isGenerated()).isTrue();
        assertThat(review.getReview()).contains("good month");
    }
}
