package com.novax.leadora.application.usecase.chat;

import com.novax.leadora.api.dto.response.ChatMessageResponse;
import com.novax.leadora.application.usecase.chat.intent.CrmArea;
import com.novax.leadora.application.usecase.chat.intent.IntentClassifier;
import com.novax.leadora.application.usecase.chat.intent.IntentResult;
import com.novax.leadora.infrastructure.integration.ai.ChatLlmService;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * The same pipeline as {@link SendChatMessageUseCase}, delivered token by token.
 *
 * <p>The model spends most of a turn writing rather than deciding, so waiting for the final token
 * before showing anything turns a five-second answer into five seconds of blank screen. Streaming
 * does not make the answer finish sooner — it makes it <em>start</em> sooner, and that is the part
 * a reader experiences. Time-to-first-token is roughly the context gathering plus the model's
 * prefill, under a second, against several seconds for the whole reply.
 *
 * <p>Event protocol, one JSON object per event:
 * <pre>
 *   start  {userMessage, intent, blocked}   once, before any text
 *   token  {t}                              zero or more; concatenate in order
 *   done   {assistantMessage}               once, after the reply is persisted
 *   error  {message}                        instead of done; the text is user-facing
 * </pre>
 *
 * <p>A blocked turn emits {@code start}, the refusal as a single {@code token}, then {@code done},
 * so a client only needs one code path.
 *
 * <p><b>Persistence happens once, at the end.</b> Writing partial replies would leave a torn
 * message behind whenever a client disconnected mid-answer.
 */
@Slf4j
@Service
public class StreamChatMessageUseCase {

    /** Generous: the ceiling exists to release a wedged connection, not to cut answers short. */
    private static final long STREAM_TIMEOUT_MS = 180_000L;

    private final ChatTurnWriter turnWriter;
    private final IntentClassifier intentClassifier;
    private final ContextAssembler contextAssembler;
    private final ChatLlmService chatLlmService;
    private final Executor executor;

    public StreamChatMessageUseCase(ChatTurnWriter turnWriter, IntentClassifier intentClassifier,
                                    ContextAssembler contextAssembler, ChatLlmService chatLlmService,
                                    @Qualifier("chatStreamExecutor") Executor executor) {
        this.turnWriter = turnWriter;
        this.intentClassifier = intentClassifier;
        this.contextAssembler = contextAssembler;
        this.chatLlmService = chatLlmService;
        this.executor = executor;
    }

    /**
     * Returns immediately with an open stream; the work runs on {@code chatStreamExecutor}.
     *
     * <p>The acting user is resolved by the caller, on the request thread, while the entity is
     * still attached — {@link ChatActor} explains why that matters here.
     */
    public SseEmitter execute(UUID sessionId, UserEntity user, String content) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        ChatActor actor = ChatActor.from(user);

        emitter.onTimeout(emitter::complete);
        emitter.onError(ex -> log.debug("Chat stream for session {} ended early: {}",
                sessionId, ex.getMessage()));

        executor.execute(() -> run(emitter, sessionId, actor, content));
        return emitter;
    }

    private void run(SseEmitter emitter, UUID sessionId, ChatActor actor, String content) {
        try {
            ChatTurnContext ctx = turnWriter.begin(sessionId, actor, content);
            List<String> priorUserMessages = ctx.priorUserMessages();

            // Language and subject areas are resolved across the session, not from this turn
            // alone: a follow-up carries neither signal on its own.
            boolean vi = IntentClassifier.resolveVietnamese(content, priorUserMessages);
            Set<CrmArea> areas = IntentClassifier.resolveAreas(content, priorUserMessages);

            IntentResult intent = intentClassifier.classify(content, ctx.lastIntent(), vi);
            send(emitter, "start", Map.of(
                    "userMessage", ctx.userMessage(),
                    "intent", intent.intent().name(),
                    "blocked", intent.blocked()));

            if (intent.blocked()) {
                // No model call at all, so the refusal arrives whole and instantly.
                send(emitter, "token", Map.of("t", intent.blockMessage()));
                finish(emitter, sessionId, intent.blockMessage(), intent.intent().name());
                return;
            }

            String referenceBlock =
                    contextAssembler.assemble(intent.intent(), actor, areas, content);

            StringBuilder full = new StringBuilder();
            try {
                // Blocking iteration is intentional: this already runs on a worker thread, and it
                // keeps the failure and completion paths in one place rather than split across
                // reactive callbacks.
                chatLlmService.stream(referenceBlock, ctx.history(), content, vi)
                        .toStream()
                        .forEach(chunk -> {
                            if (StringUtils.hasText(chunk)) {
                                full.append(chunk);
                                send(emitter, "token", Map.of("t", chunk));
                            }
                        });
            } catch (StreamClosedException closed) {
                throw closed;
            } catch (Exception ex) {
                log.error("LLM streaming failed for session {}: {}", sessionId, ex.getMessage(), ex);
                // Anything already streamed stays on screen; append the explanation rather than
                // replacing it, so the user is not left with a half-sentence and no reason.
                String message = AiErrorClassifier.userMessage(ex, vi);
                send(emitter, "token", Map.of("t", "\n\n" + message));
                full.append(full.isEmpty() ? message : "\n\n" + message);
            }

            // Chunk boundaries are the provider's, so post-processing waits for the whole text.
            String reply = ChatLlmService.stripReasoning(full.toString());
            if (!StringUtils.hasText(reply)) {
                reply = GuardrailMessages.noData(vi);
                send(emitter, "token", Map.of("t", reply));
            }
            finish(emitter, sessionId, reply, intent.intent().name());

        } catch (StreamClosedException closed) {
            // The client navigated away or refreshed. Nothing was persisted; nothing to report.
            log.debug("Client disconnected from chat stream for session {}", sessionId);
            emitter.complete();
        } catch (Exception ex) {
            log.error("Chat stream failed for session {}: {}", sessionId, ex.getMessage(), ex);
            trySend(emitter, "error", Map.of("message",
                    GuardrailMessages.systemError(IntentClassifier.isVietnamese(content))));
            emitter.complete();
        }
    }

    private void finish(SseEmitter emitter, UUID sessionId, String reply, String intentName) {
        ChatMessageResponse assistant = turnWriter.complete(sessionId, reply, intentName);
        trySend(emitter, "done", Map.of("assistantMessage", assistant));
        emitter.complete();
    }

    private void send(SseEmitter emitter, String event, Object payload) {
        try {
            emitter.send(SseEmitter.event().name(event).data(payload, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException ex) {
            // Unwound as an unchecked exception so it can escape the forEach lambda, which cannot
            // declare IOException.
            throw new StreamClosedException(ex);
        }
    }

    /** Best-effort send for terminal events: the client may already be gone. */
    private void trySend(SseEmitter emitter, String event, Object payload) {
        try {
            send(emitter, event, payload);
        } catch (StreamClosedException ignored) {
            // Nothing useful left to do — the reply is already persisted either way.
        }
    }

    /** Signals that the client is no longer listening; not an application error. */
    private static final class StreamClosedException extends RuntimeException {
        StreamClosedException(Throwable cause) {
            super(cause);
        }
    }
}
