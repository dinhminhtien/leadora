package com.novax.leadora.application.usecase.chat;

import com.novax.leadora.api.dto.response.ChatMessageResponse;
import com.novax.leadora.api.dto.response.SendMessageResponse;
import com.novax.leadora.application.usecase.chat.intent.ChatIntent;
import com.novax.leadora.application.usecase.chat.intent.CrmArea;
import com.novax.leadora.application.usecase.chat.intent.IntentClassifier;
import com.novax.leadora.application.usecase.chat.intent.IntentResult;
import com.novax.leadora.infrastructure.integration.ai.ChatLlmService;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * UC — Query Assigned Sales CRM Data / Query Team Sales CRM Summary via the chat assistant.
 *
 * <p>Orchestrates the hybrid pipeline:
 * <pre>
 *   record turn → classify → (block mutation/off-topic) → gather scoped context → generate → record
 * </pre>
 *
 * <p><b>Deliberately not transactional.</b> Only the first and last steps touch the database, and
 * each opens its own short transaction in {@link ChatTurnWriter}. Wrapping the whole method, as it
 * once was, held a connection for the entire model call — several seconds — so a handful of
 * concurrent conversations could exhaust the pool and stall requests that had nothing to do with
 * chat. Everything between the two writes runs with no transaction open, and works on detached
 * records ({@link ChatActor}, {@link ChatTurn}) so there is nothing lazy left to trip over.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SendChatMessageUseCase {

    private final ChatTurnWriter turnWriter;
    private final IntentClassifier intentClassifier;
    private final ContextAssembler contextAssembler;
    private final ChatLlmService chatLlmService;

    public SendMessageResponse execute(UUID sessionId, UserEntity user, String content) {
        ChatActor actor = ChatActor.from(user);

        // [0] One short transaction: verify ownership, record the question, read back the context.
        ChatTurnContext ctx = turnWriter.begin(sessionId, actor, content);
        List<String> priorUserMessages = ctx.priorUserMessages();

        // Reply language and subject areas are both resolved across the session rather than from
        // this turn alone: a follow-up ("ok", "yes, in detail") carries neither signal, and judging
        // it in isolation would flip the language and drop the listing the user is asking about.
        boolean vi = IntentClassifier.resolveVietnamese(content, priorUserMessages);
        Set<CrmArea> areas = IntentClassifier.resolveAreas(content, priorUserMessages);

        // [1] Guardrail. A blocked turn never reaches the model, so it costs no tokens.
        IntentResult intent = intentClassifier.classify(content, ctx.lastIntent(), vi);
        if (intent.blocked()) {
            ChatMessageResponse blocked =
                    turnWriter.complete(sessionId, intent.blockMessage(), intent.intent().name());
            return response(ctx.userMessage(), blocked, intent.intent(), true);
        }

        // [2] Gather context — outside any transaction, sources in parallel where they are
        // independent.
        String referenceBlock = contextAssembler.assemble(intent.intent(), actor, areas, content);

        // [3] Generate. Best-effort: an unavailable model degrades to a clear message in the
        // user's language rather than a stack trace.
        String reply = generate(sessionId, referenceBlock, ctx, content, vi);

        // [4] Second short transaction.
        ChatMessageResponse assistant =
                turnWriter.complete(sessionId, reply, intent.intent().name());
        return response(ctx.userMessage(), assistant, intent.intent(), false);
    }

    private String generate(UUID sessionId, String referenceBlock, ChatTurnContext ctx,
                            String content, boolean vietnamese) {
        try {
            String reply = chatLlmService.generate(referenceBlock, ctx.history(), content, vietnamese);
            return StringUtils.hasText(reply) ? reply : GuardrailMessages.noData(vietnamese);
        } catch (Exception ex) {
            log.error("LLM generation failed for session {}: {}", sessionId, ex.getMessage(), ex);
            // Distinguish "out of quota" from a genuine fault so the reply is actionable, and so
            // the two can be told apart without reading the logs.
            return AiErrorClassifier.userMessage(ex, vietnamese);
        }
    }

    private SendMessageResponse response(ChatMessageResponse userMessage,
                                         ChatMessageResponse assistantMessage,
                                         ChatIntent intent, boolean blocked) {
        return SendMessageResponse.builder()
                .userMessage(userMessage)
                .assistantMessage(assistantMessage)
                .intent(intent.name())
                .blocked(blocked)
                .build();
    }
}
