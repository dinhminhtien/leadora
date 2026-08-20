package com.novax.leadora.application.usecase.chat;

import com.novax.leadora.api.dto.response.ChatMessageResponse;
import com.novax.leadora.application.usecase.chat.intent.CrmArea;
import com.novax.leadora.application.usecase.chat.intent.IntentClassifier;
import com.novax.leadora.application.usecase.chat.intent.DateRangeResolver;
import com.novax.leadora.application.usecase.chat.intent.IntentResult;
import com.novax.leadora.application.usecase.chat.time.ChatDateRange;
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
    private final DateRangeResolver dateRangeResolver;
    private final ContextAssembler contextAssembler;
    private final ChatLlmService chatLlmService;
    private final Executor executor;

    public StreamChatMessageUseCase(ChatTurnWriter turnWriter, IntentClassifier intentClassifier,
                                    DateRangeResolver dateRangeResolver,
                                    ContextAssembler contextAssembler, ChatLlmService chatLlmService,
                                    @Qualifier("chatStreamExecutor") Executor executor) {
        this.turnWriter = turnWriter;
        this.intentClassifier = intentClassifier;
        this.dateRangeResolver = dateRangeResolver;
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

        // A terminal event, then close — never a bare close. A stream that ends without `done` or
        // `error` leaves the client with tokens it cannot attribute to a message, and the reply it
        // just watched arrive disappears off the screen.
        emitter.onTimeout(() -> {
            log.warn("Chat stream for session {} timed out after {}ms", sessionId, STREAM_TIMEOUT_MS);
            trySend(emitter, "error", Map.of("message",
                    GuardrailMessages.systemError(IntentClassifier.isVietnamese(content))));
            emitter.complete();
        });
        emitter.onError(ex -> log.debug("Chat stream for session {} ended early: {}",
                sessionId, ex.getMessage()));

        executor.execute(() -> run(emitter, sessionId, actor, content));
        return emitter;
    }

    private void run(SseEmitter emitter, UUID sessionId, ChatActor actor, String content) {
        // Tracked outside the try because the recovery path needs to know whether the question was
        // ever recorded: if begin() itself failed there is nothing to answer, and writing a reply
        // anyway could append to a session this caller does not own.
        ChatTurnContext ctx = null;
        boolean vi = IntentClassifier.isVietnamese(content);
        try {
            ctx = turnWriter.begin(sessionId, actor, content);
            List<String> priorUserMessages = ctx.priorUserMessages();

            // Language and subject areas are resolved across the session, not from this turn
            // alone: a follow-up carries neither signal on its own.
            vi = IntentClassifier.resolveVietnamese(content, priorUserMessages);
            Set<CrmArea> areas = IntentClassifier.resolveAreas(content, priorUserMessages);
            ChatDateRange range = dateRangeResolver.resolve(content, priorUserMessages);

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
                    contextAssembler.assemble(intent.intent(), actor, areas, content, range);

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
            recover(emitter, sessionId, ctx, ex, vi);
        }
    }

    /**
     * Ends a failed turn with an answer rather than with silence.
     *
     * <p>The question is persisted at the very start of the turn, so a failure anywhere after that
     * used to leave the conversation holding a question with no reply — and unlike a transient
     * error banner, that survives a page reload for ever. The user sees a message they sent that
     * was simply never answered, which reads as "the assistant ignored me" rather than "something
     * broke". Worse, the next turn replays that history to the model.
     *
     * <p>So the failure itself becomes the reply: recorded like any other assistant turn and
     * delivered as a normal {@code done}, which means the client needs no special case and the
     * explanation is still there tomorrow.
     *
     * <p>Falls back to a transient {@code error} event only when the recording itself fails —
     * typically because the database is the thing that broke — or when the question was never
     * recorded, in which case there is no turn to answer and writing one could append to a session
     * this caller does not own.
     */
    private void recover(SseEmitter emitter, UUID sessionId, ChatTurnContext ctx,
                         Exception cause, boolean vietnamese) {
        String message = AiErrorClassifier.userMessage(cause, vietnamese);
        if (ctx != null) {
            try {
                ChatMessageResponse assistant = turnWriter.complete(sessionId, message, null);
                trySend(emitter, "token", Map.of("t", message));
                trySend(emitter, "done", Map.of("assistantMessage", assistant));
                emitter.complete();
                return;
            } catch (Exception persistFailed) {
                log.error("Could not record the failure reply for session {}: {}",
                        sessionId, persistFailed.getMessage(), persistFailed);
            }
        }
        trySend(emitter, "error", Map.of("message", GuardrailMessages.systemError(vietnamese)));
        emitter.complete();
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
