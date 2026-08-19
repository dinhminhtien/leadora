package com.novax.leadora.application.usecase.chat;

import com.novax.leadora.application.usecase.chat.intent.ChatIntent;
import com.novax.leadora.application.usecase.chat.intent.CrmArea;
import com.novax.leadora.application.usecase.chat.time.ChatDateRange;
import com.novax.leadora.infrastructure.integration.ai.RagService;
import com.novax.leadora.infrastructure.persistence.repository.AiDocumentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
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
    private static final int GATHER_TIMEOUT_SECONDS = 4;

    private final CrmSnapshotService crmSnapshotService;
    private final PerformanceSnapshotService performanceSnapshotService;
    private final RagService ragService;
    private final AiDocumentRepository documentRepository;
    private final Executor executor;

    public ContextAssembler(CrmSnapshotService crmSnapshotService,
                            PerformanceSnapshotService performanceSnapshotService,
                            RagService ragService,
                            AiDocumentRepository documentRepository,
                            @Qualifier("taskExecutor") Executor executor) {
        this.crmSnapshotService = crmSnapshotService;
        this.performanceSnapshotService = performanceSnapshotService;
        this.ragService = ragService;
        this.documentRepository = documentRepository;
        this.executor = executor;
    }

    /**
     * @param intent  what the turn was classified as; decides which sources are worth consulting
     * @param actor   the asking user, for scope enforcement (BR-36)
     * @param areas   CRM areas to detail; every area still contributes its counts
     * @param query   the raw question, used as the document-search query
     * @return the reference block, empty when the intent needs no data
     */
    public String assemble(ChatIntent intent, ChatActor actor, Set<CrmArea> areas, String query,
                           ChatDateRange range) {
        try {
            return gather(intent, actor, areas, query, range);
        } catch (Exception ex) {
            // The class contract is that retrieval degrades the answer rather than failing the
            // turn, but only the parallel branches honoured it: they wrap each source in
            // supply(), which swallows failures. The branches that call a source directly did
            // not, so a single hiccup in the database — a dropped connection, a pool timeout —
            // propagated out of here, past the use case, and killed the whole turn. The user's
            // question was already persisted by then, so the conversation was left showing a
            // question with no answer, permanently, and the next turn behaved the same way only
            // sometimes. Answering with no reference data at least produces a reply that says so.
            log.warn("Context gathering failed for intent {}; answering without reference data: {}",
                    intent, ex.getMessage(), ex);
            return "";
        }
    }

    private String gather(ChatIntent intent, ChatActor actor, Set<CrmArea> areas, String query,
                          ChatDateRange range) {
        return switch (intent) {
            // Operates on the conversation itself (translate/summarise/rephrase a previous
            // answer), so the history already sent to the model is the whole input. Consulting
            // no source at all makes this the cheapest non-blocked intent, in both latency and
            // tokens.
            case META_CONVERSATION -> "";
            // Explicit possessive ("lead của tôi") — pinned to the asker even for a Manager.
            case PERSONAL_DATA -> crmSnapshotService.personalSnapshot(actor, areas, range);
            // A question naming a staff member ("deal của Tiến Đinh") gets a snapshot scoped to
            // that person — exact totals, their rows — instead of the generic unfiltered listing
            // that used to force a "filter it on the screen yourself" answer. BR-36 is enforced
            // inside: non-privileged callers never match, and fall through to their own scope.
            case ASSIGNED_DATA -> mentionedStaffOr(actor, areas, query, range,
                    () -> crmSnapshotService.scopedSnapshot(actor, areas, range));
            // Team-wide figures are a Manager/Admin privilege; anyone else is quietly narrowed to
            // their own scope rather than refused, so the question still gets a useful answer.
            case TEAM_SUMMARY -> crmSnapshotService.canSeeAllData(actor)
                    ? mentionedStaffOr(actor, areas, query, range,
                            () -> crmSnapshotService.teamSummary(range))
                    : crmSnapshotService.scopedSnapshot(actor, areas, range);
            // Rates AND rows: the report block carries the ratios a snapshot cannot express, the
            // snapshot carries the records behind them. Answering "who converts best" from ratios
            // alone leaves the assistant unable to name a single deal when asked why.
            case PERFORMANCE_REPORT -> performance(actor, areas, query, range);
            case DOC_QUERY -> documentContext(query);
            case GENERAL_BUSINESS -> blend(actor, areas, query, range);
            default -> "";
        };
    }

    /** Performance ratios plus the underlying records, gathered concurrently. */
    private String performance(ChatActor actor, Set<CrmArea> areas, String query,
                               ChatDateRange range) {
        CompletableFuture<String> report =
                supply(() -> performanceSnapshotService.render(actor, range), "performance report");
        CompletableFuture<String> crm = supply(() -> mentionedStaffOr(actor, areas, query, range,
                () -> crmSnapshotService.scopedSnapshot(actor, areas, range)), "CRM snapshot");
        return joinWithin(report, crm);
    }

    /**
     * Retrieved document excerpts, always preceded by an inventory of the knowledge base.
     *
     * <p>Retrieval alone is not enough to answer a document question honestly. When it comes back
     * empty the model was handed nothing at all, and — having been told it may only use the
     * reference data — concluded it had "no access to company policy documents". That is the wrong
     * answer twice over: it is not a permission problem, and it hides whether the document is
     * missing, still processing, or simply worded differently from the question.
     *
     * <p>The inventory is a handful of titles, so it costs almost nothing and turns that dead end
     * into a useful reply: either "nothing in <i>Employee Handbook</i> or <i>Refund Policy</i>
     * covers this" or "the knowledge base is empty — upload the document first".
     */
    private String documentContext(String query) {
        String excerpts = ragService.retrieveContext(query);
        StringBuilder sb = new StringBuilder();
        List<String> titles = searchableTitles();
        if (titles.isEmpty()) {
            sb.append("== Company knowledge base ==\nNo company document has been ingested yet, "
                    + "so there is nothing to search. Uploading one is done on the assistant's "
                    + "Company documents panel.\n");
        } else {
            sb.append("== Company knowledge base: ").append(titles.size())
                    .append(" searchable document(s) ==\n");
            titles.forEach(t -> sb.append("- ").append(t).append('\n'));
            if (!StringUtils.hasText(excerpts)) {
                sb.append("No excerpt from these documents matched this question.\n");
            }
        }
        if (StringUtils.hasText(excerpts)) {
            sb.append('\n').append(excerpts);
        }
        return sb.toString();
    }

    /** Never fails the turn: an unreachable database degrades the answer, it does not break it. */
    private List<String> searchableTitles() {
        try {
            return documentRepository.findSearchableTitles();
        } catch (Exception ex) {
            log.warn("Could not list knowledge-base documents: {}", ex.getMessage());
            return List.of();
        }
    }

    /** The named colleague's snapshot when the question mentions one, else the given fallback. */
    private String mentionedStaffOr(ChatActor actor, Set<CrmArea> areas, String query,
                                    ChatDateRange range,
                                    java.util.function.Supplier<String> fallback) {
        String mentioned = crmSnapshotService.mentionedStaffSnapshot(actor, areas, query, range);
        return StringUtils.hasText(mentioned) ? mentioned : fallback.get();
    }

    /** Company documents and the user's own figures, gathered concurrently. */
    private String blend(ChatActor actor, Set<CrmArea> areas, String query, ChatDateRange range) {
        CompletableFuture<String> rag = supply(() -> ragService.retrieveContext(query), "RAG");
        CompletableFuture<String> crm = supply(() -> mentionedStaffOr(actor, areas, query, range,
                () -> crmSnapshotService.scopedSnapshot(actor, areas, range)), "CRM snapshot");
        return joinWithin(rag, crm);
    }

    /**
     * Waits for both sources up to the gather timeout, then concatenates whatever arrived.
     *
     * <p>{@code getNow("")} is what makes the timeout non-fatal: a source that has not finished
     * contributes nothing and the answer is built from the rest, which beats making the user wait
     * on something that may never respond.
     */
    private String joinWithin(CompletableFuture<String> first, CompletableFuture<String> second) {
        try {
            CompletableFuture.allOf(first, second).get(GATHER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception ex) {
            log.warn("Context gathering did not finish in time ({}); answering with whatever arrived",
                    ex.getClass().getSimpleName());
        }
        String a = first.getNow("");
        String b = second.getNow("");
        return (StringUtils.hasText(a) ? a + "\n" : "") + b;
    }

    private CompletableFuture<String> supply(java.util.function.Supplier<String> source, String name) {
        return CompletableFuture.supplyAsync(source, executor)
                .exceptionally(ex -> {
                    log.warn("{} unavailable for this turn: {}", name, ex.getMessage());
                    return "";
                });
    }
}
