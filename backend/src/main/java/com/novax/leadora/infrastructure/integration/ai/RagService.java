package com.novax.leadora.infrastructure.integration.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    private static final int TOP_K = 4;
    private static final double SIMILARITY_THRESHOLD = 0.5;

    private final VectorStore vectorStore;
    private final SemanticChunker semanticChunker;

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

        // Semantic chunking: each chunk is stamped with its parent documentId/title/file so a
        // document can be retrieved as labelled context and deleted as a unit.
        Map<String, Object> baseMetadata = Map.of(
                META_DOC_ID, documentId.toString(),
                META_TITLE, title,
                META_FILE, fileName);
        List<Document> chunks = semanticChunker.chunk(rawDocs, baseMetadata);

        if (chunks.isEmpty()) {
            log.warn("Document {} ({}) produced no extractable text", documentId, fileName);
            return 0;
        }
        vectorStore.add(chunks);
        log.info("Ingested document {} ({}) into {} chunks", documentId, fileName, chunks.size());
        return chunks.size();
    }

    /** Removes every chunk belonging to a document. */
    public void deleteDocument(UUID documentId) {
        var expr = new FilterExpressionBuilder().eq(META_DOC_ID, documentId.toString()).build();
        vectorStore.delete(expr);
    }

    /**
     * Retrieves the most relevant document chunks for a query and returns them as a single
     * text block (empty string when nothing relevant is found). Never throws — RAG is best-effort.
     */
    public String retrieveContext(String query) {
        try {
            SearchRequest request = SearchRequest.builder()
                    .query(query)
                    .topK(TOP_K)
                    .similarityThreshold(SIMILARITY_THRESHOLD)
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
