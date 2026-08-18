package com.novax.leadora.application.usecase.reporting;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.novax.leadora.api.dto.response.AiCoachingReview;
import com.novax.leadora.api.dto.response.RepScorecardAiReviewResponse;
import com.novax.leadora.api.dto.response.RepScorecardResponse;
import com.novax.leadora.api.dto.response.RepScorecardResponse.RepMetrics;
import com.novax.leadora.api.dto.response.RepScorecardResponse.RepScorecard;
import com.novax.leadora.application.usecase.chat.AiErrorClassifier;
import com.novax.leadora.common.exception.BusinessRuleException;
import com.novax.leadora.infrastructure.integration.ai.ScorecardAdvisorLlmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * UC-23.7 — AI review of the rep performance scorecard.
 *
 * <p>Strictly read-only (BR-35): it fetches UC-23.6's output, hands the numbers to the model, and
 * returns text. Nothing here writes a record other than the audit log of the request itself.
 *
 * <h2>What is guaranteed in code rather than by the prompt</h2>
 *
 * <p>A prompt is a request, not a constraint — the model complies almost always, which is the worst
 * kind of reliability for a caveat. So the things that must be true of a document that scores people
 * are produced here:
 *
 * <ul>
 *   <li>The <b>disclaimer</b> and the <b>list of thin-evidence reps</b> are generated in code and
 *       returned as their own fields, so the UI can render them outside the model's text.</li>
 *   <li>The <b>payload is built here</b> from the scorecard, not assembled from anything a caller
 *       typed. There is no user-supplied text in the prompt at all, which removes prompt injection
 *       as a category rather than defending against it.</li>
 *   <li>An <b>empty period never reaches the model</b>. Asking it to review nobody produces a
 *       fluent review of nobody.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RepScorecardAiReviewUseCase {

    private static final String DISCLAIMER_VI = """
            Bản nhận xét này do AI sinh ra từ đúng các số liệu trong báo cáo, và là **gợi ý huấn \
            luyện — không phải đánh giá nhân sự**. Điểm số dựa trên trọng số và ngưỡng do người \
            cấu hình, không phải phép đo khách quan. Hãy đọc kèm số liệu thô và đối chiếu với \
            bối cảnh mà hệ thống không nhìn thấy.""";

    private static final String DISCLAIMER_EN = """
            This review is AI-generated from the figures in the report and is a **coaching aid, not \
            a personnel assessment**. The scores rest on configured weights and thresholds, not on \
            objective measurement. Read it alongside the raw numbers, and against context the system \
            cannot see.""";

    private static final String NO_DATA_VI =
            "Không có nhân viên nào phát sinh hoạt động trong kỳ này, nên chưa có gì để nhận xét.";
    private static final String NO_DATA_EN =
            "Nobody recorded activity in this period, so there is nothing to review yet.";

    private final GetRepScorecardUseCase getRepScorecardUseCase;
    private final ScorecardAdvisorLlmService advisorLlmService;
    private final ObjectMapper objectMapper;

    /**
     * Deliberately <b>not</b> {@code @Transactional}.
     *
     * <p>{@link GetRepScorecardUseCase#execute} opens and closes its own read-only transaction, and
     * that is the only database work here. Wrapping this method too would hold the JDBC connection
     * for the whole Gemini call — Hibernate's default connection handling releases only at
     * transaction end — so a handful of managers pressing "Review the team" at once would drain the
     * HikariCP pool for as long as the model took to answer, and take the rest of the application
     * down with it. Nothing about a language model's latency belongs inside a database transaction.
     *
     * <p>The key carries today's date for the same reason the scorecard's does: the default window
     * slides, so a constant key would serve yesterday's period after midnight.
     *
     * @param userId     review one rep, or null for the whole team
     * @param vietnamese reply language
     */
    @Cacheable(value = "rep-scorecard-ai-review",
            key = "#dateFrom + '_' + #dateTo + '_' + #userId + '_' + #vietnamese"
                    + " + '_' + T(java.time.LocalDate).now()",
            unless = "#result == null || !#result.generated")
    public RepScorecardAiReviewResponse execute(LocalDate dateFrom, LocalDate dateTo,
                                                UUID userId, boolean vietnamese) {
        RepScorecardResponse scorecard = getRepScorecardUseCase.execute(dateFrom, dateTo);
        List<RepScorecard> scope = scope(scorecard, userId);

        RepScorecardAiReviewResponse.RepScorecardAiReviewResponseBuilder response =
                RepScorecardAiReviewResponse.builder()
                        .dateFrom(scorecard.getDateFrom())
                        .dateTo(scorecard.getDateTo())
                        .scope(userId == null ? "Whole team" : scope.get(0).getName())
                        .language(vietnamese ? "vi" : "en")
                        .disclaimer(vietnamese ? DISCLAIMER_VI : DISCLAIMER_EN)
                        .lowConfidenceReps(scope.stream()
                                .filter(rep -> rep.isLowConfidence())
                                .map(rep -> rep.getName())
                                .toList());

        if (scope.isEmpty()) {
            // Never ask the model to review nobody: it will oblige, at length.
            return response.review(vietnamese ? NO_DATA_VI : NO_DATA_EN).generated(false).build();
        }

        try {
            String payload = payload(scorecard, scope);
            String answer = advisorLlmService.review(payload, vietnamese);
            return response
                    .review(answer)
                    .structured(parseCoaching(answer))
                    .generated(true)
                    .build();
        } catch (JsonProcessingException e) {
            log.error("Could not serialise the scorecard for AI review", e);
            throw new BusinessRuleException("Could not prepare the scorecard for review.");
        } catch (RuntimeException e) {
            // Quota exhaustion is the expected failure on the Gemini free tier, and it is not a bug.
            // The report itself is unaffected, so this degrades to an explanation rather than a 500.
            log.warn("AI scorecard review unavailable: {}", e.getMessage());
            return response.review(AiErrorClassifier.userMessage(e, vietnamese)).generated(false).build();
        }
    }

    /**
     * Parses the model's JSON, and returns null rather than failing when it cannot.
     *
     * <p>The prompt asks for bare JSON, but a model that has been asked for JSON its whole life
     * wraps it in a ```json fence often enough that stripping one is cheaper than arguing. Anything
     * still unparseable degrades to the raw text, which the screen already knows how to render:
     * a differently-shaped answer should cost the pretty layout, never the answer.
     */
    private AiCoachingReview parseCoaching(String answer) {
        String json = unfence(answer);
        if (json == null) {
            return null;
        }
        try {
            AiCoachingReview parsed = objectMapper.readValue(json, AiCoachingReview.class);
            return (parsed == null || parsed.getReps() == null || parsed.getReps().isEmpty())
                    ? null
                    : parsed;
        } catch (JsonProcessingException e) {
            log.warn("AI review did not come back as the requested shape; falling back to raw text: {}",
                    e.getOriginalMessage());
            return null;
        }
    }

    /** Trims anything either side of the outermost JSON object, fence or commentary alike. */
    private static String unfence(String answer) {
        if (answer == null) {
            return null;
        }
        int start = answer.indexOf('{');
        int end = answer.lastIndexOf('}');
        return (start < 0 || end <= start) ? null : answer.substring(start, end + 1);
    }

    private static List<RepScorecard> scope(RepScorecardResponse scorecard, UUID userId) {
        if (userId == null) {
            return scorecard.getReps();
        }
        return scorecard.getReps().stream()
                .filter(rep -> userId.equals(rep.getUserId()))
                .findFirst()
                .map(List::of)
                .orElseThrow(() -> new BusinessRuleException(
                        "That user has no scorecard in the selected period."));
    }

    /**
     * The exact document the model sees.
     *
     * <p>Built field by field rather than by serialising the response DTO: it keeps the prompt small,
     * and — more usefully — it means adding a field to the scorecard never silently starts feeding
     * the model something nobody decided to feed it. User ids are left out; they are pure tokens.
     */
    private String payload(RepScorecardResponse scorecard, List<RepScorecard> scope)
            throws JsonProcessingException {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("periodFrom", String.valueOf(scorecard.getDateFrom()));
        root.put("periodTo", String.valueOf(scorecard.getDateTo()));
        root.put("periodMonths", scorecard.getPeriodMonths());
        root.put("scoreScale", "0-100, higher is better; null means not measured in this period");

        ObjectNode weights = root.putObject("axisWeights");
        weights.put("outcome", scorecard.getWeights().getOutcome());
        weights.put("efficiency", scorecard.getWeights().getEfficiency());
        weights.put("velocity", scorecard.getWeights().getVelocity());
        weights.put("discipline", scorecard.getWeights().getDiscipline());
        weights.put("quality", scorecard.getWeights().getQuality());

        ObjectNode team = root.putObject("teamBaseline");
        team.put("repCount", scorecard.getTeam().getRepCount());
        putDouble(team, "medianScore", scorecard.getTeam().getMedianScore());
        putDouble(team, "winRatePct", scorecard.getTeam().getWinRate());
        putDouble(team, "leadConversionPct", scorecard.getTeam().getLeadConversionRate());
        putDouble(team, "quotationAcceptancePct", scorecard.getTeam().getQuotationAcceptanceRate());
        putDouble(team, "slaCompliancePct", scorecard.getTeam().getSlaComplianceRate());
        putDouble(team, "taskCompletionPct", scorecard.getTeam().getTaskCompletionRate());
        putDouble(team, "csat", scorecard.getTeam().getCsat());

        ArrayNode reps = root.putArray("reps");
        for (RepScorecard rep : scope) {
            reps.add(repNode(rep));
        }
        return objectMapper.writeValueAsString(root);
    }

    private ObjectNode repNode(RepScorecard rep) {
        RepMetrics m = rep.getMetrics();
        ObjectNode node = objectMapper.createObjectNode();
        node.put("name", rep.getName());
        node.put("lowConfidence", rep.isLowConfidence());
        node.put("ranked", rep.isRanked());
        if (rep.getRank() != null) {
            node.put("rank", rep.getRank());
        }

        ObjectNode score = node.putObject("score");
        putDouble(score, "total", rep.getScore().getTotal());
        putDouble(score, "outcome", rep.getScore().getOutcome());
        putDouble(score, "efficiency", rep.getScore().getEfficiency());
        putDouble(score, "velocity", rep.getScore().getVelocity());
        putDouble(score, "discipline", rep.getScore().getDiscipline());
        putDouble(score, "quality", rep.getScore().getQuality());
        score.put("weightCovered", rep.getScore().getWeightCovered());

        ObjectNode metrics = node.putObject("metrics");
        putDecimal(metrics, "revenueCollected", m.getRevenue());
        metrics.put("dealsWon", m.getDealsWon());
        metrics.put("dealsClosed", m.getDealsClosed());
        putDouble(metrics, "winRatePct", m.getWinRate());
        metrics.put("leadsRaised", m.getLeadsCreated());
        metrics.put("leadsFromThisPeriodConverted", m.getCohortConverted());
        putDouble(metrics, "leadConversionPct", m.getLeadConversionRate());
        metrics.put("quotationsCreated", m.getQuotationsCreated());
        metrics.put("quotationsAccepted", m.getQuotationsAccepted());
        putDouble(metrics, "quotationAcceptancePct", m.getQuotationAcceptanceRate());
        putDouble(metrics, "firstResponseHours", m.getFirstResponseHours());
        putDouble(metrics, "quotationTurnaroundHours", m.getQuotationTurnaroundHours());
        putDouble(metrics, "dealCycleDays", m.getDealCycleDays());
        putDouble(metrics, "slaCompliancePct", m.getSlaComplianceRate());
        metrics.put("slaDecided", m.getSlaDecided());
        putDouble(metrics, "taskCompletionPct", m.getTaskCompletionRate());
        putDouble(metrics, "taskOverduePct", m.getTaskOverdueRate());
        metrics.put("tasksTotal", m.getTasksTotal());
        putDouble(metrics, "avgDiscountPct", m.getAvgDiscountPercent());
        putDouble(metrics, "revisionsPerQuotation", m.getRevisionsPerQuotation());
        putDouble(metrics, "collectionOnTimePct", m.getCollectionOnTimeRate());
        putDouble(metrics, "forecastAccuracyPct", m.getForecastAccuracyRate());
        putDouble(metrics, "csat", m.getCsat());
        metrics.put("csatSamples", m.getCsatSamples());
        metrics.put("activeDays", m.getActiveDays());
        metrics.put("settledOutcomes", m.getSampleSize());

        if (rep.getDataGaps() != null && !rep.getDataGaps().isEmpty()) {
            ArrayNode gaps = node.putArray("dataGaps");
            rep.getDataGaps().forEach(gaps::add);
        }
        if (rep.getTopLostReasons() != null && !rep.getTopLostReasons().isEmpty()) {
            ArrayNode reasons = node.putArray("topLostReasons");
            rep.getTopLostReasons().forEach(reason -> {
                ObjectNode entry = reasons.addObject();
                entry.put("reason", reason.getReason());
                entry.put("count", reason.getCount());
            });
        }
        return node;
    }

    /** Null is written as an explicit JSON null: rule 2 of the prompt depends on seeing it. */
    private static void putDouble(ObjectNode node, String field, Double value) {
        if (value == null) {
            node.putNull(field);
        } else {
            node.put(field, value);
        }
    }

    private static void putDecimal(ObjectNode node, String field, BigDecimal value) {
        if (value == null) {
            node.putNull(field);
        } else {
            node.put(field, value);
        }
    }
}
