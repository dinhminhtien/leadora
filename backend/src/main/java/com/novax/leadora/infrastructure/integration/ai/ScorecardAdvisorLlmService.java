package com.novax.leadora.infrastructure.integration.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * The LLM half of UC-23.7 — turning a rep scorecard into written coaching notes.
 *
 * <p>Separate from {@link ChatLlmService} on purpose. That service's prompt is built for an
 * open-ended chat with a rule about declining off-topic questions and a whole vocabulary about
 * which CRM areas it can see; none of it applies here, and the risks are different. This prompt has
 * one job, one input shape, and its own guardrails.
 *
 * <p>It does <b>not</b> go through {@code IntentClassifier} either: that gate exists to stop a user
 * asking the chat assistant to change data, and here there is no user text at all. The input is a
 * server-generated JSON document; the only thing a caller controls is which period to score.
 */
@Slf4j
@Service
public class ScorecardAdvisorLlmService {

    /**
     * The guardrails that matter are the ones about being wrong <em>about a person</em>.
     *
     * <p>Rule 1 exists because a language model asked to comment on a table will fluently invent the
     * one number that would make its point. Rule 4 exists because the natural register for "review
     * this employee's performance" is a manager's, and a model writing in that register will
     * recommend formal warnings and dismissals it has no standing to recommend — from twelve
     * measurements, some of which rest on four events.
     */
    private static final String SYSTEM_PROMPT = """
            You are a sales coaching assistant for Leadora, a hospitality CRM. You are given a
            performance scorecard as JSON and you write short, practical coaching notes from it.

            HARD RULES — these override any instinct about what makes a good review:

            1. USE ONLY THE SUPPLIED NUMBERS. Every figure, name, rate and count you write must
               appear verbatim in the JSON. Never estimate, extrapolate, infer a missing figure, or
               compare against an industry benchmark you happen to know. If something is not in the
               JSON, you do not know it, and you say so rather than filling the gap.

            2. A NULL IS NOT A ZERO. A null score or metric means it could not be measured in this
               period — not that the rep scored nothing. Read the "dataGaps" list and treat those
               areas as unknown. Never describe an unmeasured area as a weakness.

            3. THIN EVIDENCE MUST BE SAID OUT LOUD. When a rep has "lowConfidence": true, or a low
               "sampleSize", or a "weightCovered" below 100, open that rep's section by saying the
               evidence is thin and the score should be read as a hint. Do not soften this.

            4. COACHING, NOT PERSONNEL DECISIONS. Recommend things a manager and a rep can do:
               practise, shadow, change a habit, adjust a routine, review specific deals. Never
               recommend or imply dismissal, formal warnings, pay changes, ranking someone last, or
               any disciplinary action. Never speculate about attitude, effort, motivation, health or
               personal circumstances — you can see numbers, not people.

            5. SCORES ARE A SUMMARY, NOT A VERDICT. The weights and thresholds behind them are
               configured judgement calls, not measurements. Never present a score as an objective
               fact about someone's ability.

            6. BE SHORT AND CONCRETE. No preamble, no restating the JSON, no motivational filler.
               Each action must name the specific metric it targets and be something doable this
               month.

            OUTPUT — return ONE JSON object and nothing else. No prose before or after it, no
            markdown code fence. This exact shape:

            {
              "reps": [
                {
                  "name": "<exactly as it appears in the JSON>",
                  "headline": "<one sentence summing up the period>",
                  "strengths": ["<1-3 items, each citing the metric behind it>"],
                  "needsWork": ["<0-3 items, each citing the metric behind it>"],
                  "actions": [
                    {"action": "<something doable this month>", "metric": "<the metric it targets>"}
                  ],
                  "evidenceNote": "<one line on sample size and anything unmeasured>"
                }
              ],
              "teamRead": ["<2-4 observations across the team; empty array when only one rep>"]
            }

            "actions" must contain exactly 3 entries. "needsWork" may be empty — do not invent a
            weakness for symmetry. "evidenceNote" is required for every rep. Every string is plain
            text: no markdown, no bullet characters, no headings.
            """;

    private static final String LANGUAGE_VI =
            "\n\nWrite every string VALUE in Vietnamese. The JSON keys stay exactly as specified in "
                    + "English, and rep names stay exactly as they appear in the input.";
    private static final String LANGUAGE_EN =
            "\n\nWrite every string value in English.";

    private final ChatClient chatClient;

    public ScorecardAdvisorLlmService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * @param scorecardJson the server-built payload — the only data the model is allowed to use
     * @param vietnamese    reply language
     * @throws RuntimeException whatever Spring AI throws; the caller classifies it
     */
    public String review(String scorecardJson, boolean vietnamese) {
        return chatClient.prompt()
                .system(SYSTEM_PROMPT + (vietnamese ? LANGUAGE_VI : LANGUAGE_EN))
                .user("SCORECARD JSON:\n" + scorecardJson)
                .call()
                .content();
    }
}
