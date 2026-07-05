package com.novax.leadora.application.usecase.chat;

import com.novax.leadora.infrastructure.integration.ai.RagService;
import com.novax.leadora.infrastructure.persistence.repository.AiDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Background worker for RAG document ingestion (parse → chunk → embed → store).
 *
 * <p>Why async: ingesting a large Word/PDF takes minutes (per-sentence + per-chunk embedding
 * against the Gemini free tier), longer than a browser keeps one HTTP request open. The old
 * synchronous flow made the client give up and report failure even though the server later
 * committed — the "upload fails, but the file appears after the next upload" symptom. Now
 * {@link UploadDocumentUseCase} only persists the metadata row and returns; this worker does
 * the heavy lifting on the single-threaded {@code documentIngestExecutor}.
 *
 * <p><b>Status protocol (no schema change):</b> {@code chunk_count == 0} means "processing".
 * On success the real chunk count is written; on failure the metadata row and any partial
 * chunks are removed, so a document row that disappears means the ingest failed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestService {

    private final AiDocumentRepository documentRepository;
    private final RagService ragService;

    @Async("documentIngestExecutor")
    public void ingestInBackground(UUID documentId, String title, String fileName, byte[] content) {
        // This line proves the async hand-off worked — if an upload sticks at 0 chunks and
        // this never appears in the log, the executor/proxy is the problem, not the ingest.
        log.info("Background ingest STARTED for document {} ({}, {} bytes)", documentId, fileName, content.length);
        try {
            int chunks = ragService.ingest(documentId, title, fileName, content);
            if (chunks == 0) {
                throw new IllegalStateException("Không trích xuất được nội dung văn bản từ tệp này.");
            }

            // Replace-by-title AFTER the new version is fully live: a failed re-upload must
            // never destroy the previous good version (the old sync flow got this from its
            // transaction rollback; the async flow gets it from operation ordering instead).
            documentRepository.findByTitleIgnoreCase(title).stream()
                    .filter(old -> !documentId.equals(old.getDocumentId()))
                    .forEach(old -> {
                        ragService.deleteDocument(old.getDocumentId());
                        documentRepository.delete(old);
                    });

            documentRepository.findById(documentId).ifPresent(doc -> {
                doc.setChunkCount(chunks);
                documentRepository.save(doc);
            });
            log.info("Background ingest finished for document {} ({}) — {} chunks", documentId, fileName, chunks);
        } catch (Throwable ex) {
            // Throwable, not Exception: a huge PDF can OOM the parser (an Error) — with a
            // narrower catch the row would be left stuck at 0 chunks with no cleanup.
            log.error("Background ingest FAILED for document {} ({}): {}", documentId, fileName, ex.getMessage(), ex);
            // Clean up so no orphan chunks pollute retrieval and the row's disappearance
            // signals the failure to the client.
            try {
                ragService.deleteDocument(documentId);
            } catch (Exception cleanupEx) {
                log.warn("Could not clean vector chunks of failed document {}: {}", documentId, cleanupEx.getMessage());
            }
            try {
                documentRepository.deleteById(documentId);
            } catch (Exception cleanupEx) {
                log.warn("Could not delete metadata row of failed document {}: {}", documentId, cleanupEx.getMessage());
            }
        }
    }
}
