package com.novax.leadora.infrastructure.integration.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * RAG over company documents (policies, handbooks...) backed by the pgvector store.
 *
 * <p>Documents are chunked by {@link SemanticChunker} (meaning-aware, not fixed token size),
 * embedded with the configured Google Gemini embedding model, and stored with a {@code documentId}
 * in each chunk's metadata so a logical document can be deleted as a unit. Company documents are
 * shared knowledge — not user-scoped.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private static final String META_DOC_ID = "documentId";
    private static final String META_TITLE = "title";
    private static final String META_FILE = "fileName";

    /** Cache of finished retrieval blocks, keyed by the normalised query. */
    static final String CONTEXT_CACHE = "rag-context";

    /**
     * How many chunks to feed the LLM as context. Higher = richer grounding (better recall on broad
     * questions) at the cost of a longer prompt and more chance of an off-topic chunk sneaking in.
     */
    @Value("${ai.rag.retrieval.top-k:6}")
    private int topK;

    /**
     * Minimum cosine similarity for a chunk to count as relevant (0..1). Lower = more permissive:
     * recovers loosely-worded matches but admits more noise. 0.4 favours recall for a Q&A assistant.
     */
    @Value("${ai.rag.retrieval.similarity-threshold:0.4}")
    private double similarityThreshold;

    private final VectorStore vectorStore;
    private final SemanticChunker semanticChunker;
    private final VisionOcrService visionOcrService;

    /**
     * Parses, chunks, embeds and stores a document. Returns the number of chunks ingested.
     */
    public int ingest(UUID documentId, String title, String fileName, byte[] content) {
        ByteArrayResource resource = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return fileName; // lets Tika pick the right parser (pdf/docx/txt/md)
            }
        };

        TikaDocumentReader reader = new TikaDocumentReader(resource);
        List<Document> rawDocs = reader.get();

        String tikaText = rawDocs.stream()
                .map(d -> d.getText())
                .filter(Objects::nonNull)
                .collect(Collectors.joining("\n"));

        // Text-first, vision-second: Tika read the text layer above; if enabled, transcribe the text
        // trapped inside images (scanned pages, charts, screenshots) and fold it into the same
        // chunking/embedding pipeline. Best-effort — an empty result just means "text only".
        String ocrText = visionOcrService.isEnabled()
                ? visionOcrService.ocr(fileName, content, tikaText)
                : "";
        // The two numbers that explain every "0 chunks" failure: how much the text layer gave and
        // how much vision recovered. Without them an image-only document that ingests to nothing
        // is indistinguishable from a quota error or a parser crash.
        log.info("Parsed {} ({}): {} chars from text layer, {} chars from vision OCR (enabled={})",
                documentId, fileName, tikaText.strip().length(), ocrText.strip().length(),
                visionOcrService.isEnabled());

        // One file, one text. The text layer and the vision transcription are two extraction routes
        // over the *same* document, so they are concatenated in reading order rather than chunked as
        // two separate inputs. Keeping them apart forced a minimum of two chunks out of an
        // image-only file: whatever scrap Word left in the text layer became a chunk of its own —
        // an all-but-empty vector carrying the document's title, competing for a top-K slot — because
        // SemanticChunker's min-chars merge can only combine fragments *within* one input document.
        // Joining also lets the semantic splitter see the run of meaning across the two sources.
        String fullText = Stream.of(tikaText, ocrText)
                .filter(StringUtils::hasText)
                .map(s -> s.strip())
                .collect(Collectors.joining("\n\n"));
        if (!StringUtils.hasText(fullText)) {
            log.warn("Document {} ({}) produced no extractable text", documentId, fileName);
            return 0;
        }

        // Semantic chunking: each chunk is stamped with its parent documentId/title/file so a
        // document can be retrieved as labelled context and deleted as a unit. Tika's own metadata
        // is carried over from the first parsed document so nothing it recorded is lost in the join.
        Map<String, Object> baseMetadata = Map.of(
                META_DOC_ID, documentId.toString(),
                META_TITLE, title,
                META_FILE, fileName);
        Document merged = Document.builder()
                .text(fullText)
                .metadata(rawDocs.isEmpty() ? new HashMap<>() : new HashMap<>(rawDocs.get(0).getMetadata()))
                .build();
        List<Document> chunks = semanticChunker.chunk(List.of(merged), baseMetadata);

        if (chunks.isEmpty()) {
            log.warn("Document {} ({}) produced no extractable text", documentId, fileName);
            return 0;
        }
        vectorStore.add(chunks);
        log.info("Ingested document {} ({}) into {} chunks", documentId, fileName, chunks.size());
        return chunks.size();
    }

    /** Removes every chunk belonging to a document, and invalidates any cached retrievals. */
    @CacheEvict(cacheNames = CONTEXT_CACHE, allEntries = true)
    public void deleteDocument(UUID documentId) {
        var expr = new FilterExpressionBuilder().eq(META_DOC_ID, documentId.toString()).build();
        vectorStore.delete(expr);
    }

    /**
     * Drops every cached retrieval. Called after a successful ingest so newly added chunks become
     * visible immediately instead of after the cache TTL.
     *
     * <p>Must be invoked from another bean — a call from inside this class would bypass the proxy
     * and silently do nothing.
     */
    @CacheEvict(cacheNames = CONTEXT_CACHE, allEntries = true)
    public void evictContextCache() {
        log.debug("RAG context cache evicted");
    }

    /**
     * Retrieves the most relevant document chunks for a query and returns them as a single
     * text block (empty string when nothing relevant is found). Never throws — RAG is best-effort.
     *
     * <p>Cached because the call is two round trips: embedding the query with Gemini, then the
     * vector search. Company documents change rarely, so a repeated question can be served from
     * Redis instead. Caching the finished text rather than just the query embedding covers both
     * hops; {@code VectorStore.similaritySearch} takes query text and embeds internally, so there
     * is no supported way to inject a pre-computed vector without bypassing the abstraction.
     *
     * <p>Empty results are not cached ({@code unless}), so a question asked before a document is
     * uploaded is retried afterwards rather than staying empty for the whole TTL.
     */
    @Cacheable(cacheNames = CONTEXT_CACHE, key = "#query.trim().toLowerCase()",
            unless = "#result == null || #result.isEmpty()")
    public String retrieveContext(String query) {
        try {
            SearchRequest request = SearchRequest.builder()
                    .query(query)
                    .topK(topK)
                    .similarityThreshold(similarityThreshold)
                    .build();
            List<Document> hits = vectorStore.similaritySearch(request);
            if (hits == null || hits.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder("== Relevant company document excerpts ==\n");
            for (Document d : hits) {
                Object title = d.getMetadata().get(META_TITLE);
                sb.append("[").append(title != null ? title : "Document").append("]\n");
                sb.append(d.getText()).append("\n---\n");
            }
            return sb.toString();
        } catch (Exception ex) {
            log.warn("RAG retrieval failed (vector store/embedding unavailable?): {}", ex.getMessage());
            return "";
        }
    }
}
