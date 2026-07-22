package com.novax.leadora.application.usecase.chat;

import com.novax.leadora.application.usecase.chat.intent.ChatIntent;
import com.novax.leadora.application.usecase.chat.intent.CrmArea;
import com.novax.leadora.infrastructure.integration.ai.RagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Step [2] of the pipeline: gathers everything the model should ground its answer in.
 *
 * <p><b>Why a single gate.</b> Each intent used to reach for its own sources inline, which left
 * nowhere to run them together. A general business question needs both the document search and the
 * CRM snapshot, and the two are entirely independent — one embeds a query against Gemini, the
 * other queries Postgres — so running them one after the other simply added their latencies. They
 * now overlap, and the cost of gathering context is the slower of the two rather than the sum.
 *
 * <p>Retrieval is best-effort by design: a failed or slow source degrades the answer, it does not
 * fail the turn. Anything still outstanding after {@link #GATHER_TIMEOUT_SECONDS} is dropped and
 * the model answers from what did arrive, which is a better outcome than making the user wait for
 * a source that may never respond.
 *
 * <p>Runs on the shared task executor, off the request thread, and touches no JPA entities — see
 * {@link ChatActor} for why that matters.
 */
@Slf4j
@Service
public class ContextAssembler {

    /** Beyond this, an answer grounded in partial context beats a spinner. */
    private static final int GATHER_TIMEOUT_SECONDS = 6;

    private final CrmSnapshotService crmSnapshotService;
    private final RagService ragService;
    private final Executor executor;

    public ContextAssembler(CrmSnapshotService crmSnapshotService, RagService ragService,
                            @Qualifier("taskExecutor") Executor executor) {
        this.crmSnapshotService = crmSnapshotService;
        this.ragService = ragService;
        this.executor = executor;
    }

    /**
     * @param intent  what the turn was classified as; decides which sources are worth consulting
     * @param actor   the asking user, for scope enforcement (BR-36)
     * @param areas   CRM areas to detail; every area still contributes its counts
     * @param query   the raw question, used as the document-search query
     * @return the reference block, empty when the intent needs no data
     */
    public String assemble(ChatIntent intent, ChatActor actor, Set<CrmArea> areas, String query) {
        return switch (intent) {
            // Operates on the conversation itself (translate/summarise/rephrase a previous
            // answer), so the history already sent to the model is the whole input. Consulting
            // no source at all makes this the cheapest non-blocked intent, in both latency and
            // tokens.
            case META_CONVERSATION -> "";
            // Explicit possessive ("lead của tôi") — pinned to the asker even for a Manager.
            case PERSONAL_DATA -> crmSnapshotService.personalSnapshot(actor, areas);
            case ASSIGNED_DATA -> crmSnapshotService.scopedSnapshot(actor, areas);
            // Team-wide figures are a Manager/Admin privilege; anyone else is quietly narrowed to
            // their own scope rather than refused, so the question still gets a useful answer.
            case TEAM_SUMMARY -> crmSnapshotService.canSeeAllData(actor)
                    ? crmSnapshotService.teamSummary()
                    : crmSnapshotService.scopedSnapshot(actor, areas);
            case DOC_QUERY -> ragService.retrieveContext(query);
            case GENERAL_BUSINESS -> blend(actor, areas, query);
            default -> "";
        };
    }

    /** Company documents and the user's own figures, gathered concurrently. */
    private String blend(ChatActor actor, Set<CrmArea> areas, String query) {
        CompletableFuture<String> rag = supply(() -> ragService.retrieveContext(query), "RAG");
        CompletableFuture<String> crm =
                supply(() -> crmSnapshotService.scopedSnapshot(actor, areas), "CRM snapshot");

        try {
            CompletableFuture.allOf(rag, crm).get(GATHER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception ex) {
            log.warn("Context gathering did not finish in time ({}); answering with whatever arrived",
                    ex.getClass().getSimpleName());
        }

        String ragText = rag.getNow("");
        String crmText = crm.getNow("");
        return (StringUtils.hasText(ragText) ? ragText + "\n" : "") + crmText;
    }

    private CompletableFuture<String> supply(java.util.function.Supplier<String> source, String name) {
        return CompletableFuture.supplyAsync(source, executor)
                .exceptionally(ex -> {
                    log.warn("{} unavailable for this turn: {}", name, ex.getMessage());
                    return "";
                });
    }
}
