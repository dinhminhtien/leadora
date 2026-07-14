package com.novax.leadora.application.usecase.chat;

import com.novax.leadora.api.dto.response.DocumentResponse;
import com.novax.leadora.infrastructure.persistence.entity.AiDocumentEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.repository.AiDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * UC (optional) — Upload a company document into the RAG knowledge base.
 *
 * <p><b>Accept-then-ingest:</b> parsing + embedding a document against the Gemini free tier can
 * take minutes — longer than a browser keeps one HTTP request open — which used to make the client
 * report failure even though the server later committed. This use case therefore only validates the
 * file and persists a metadata row (with {@code chunkCount == 0}, the "processing" marker), then
 * hands the heavy work to {@link DocumentIngestService} on a background executor and returns
 * immediately. The UI polls the document list and flips the row to "ready" once its chunk count > 0
 * (or removes it if the row disappears, which signals an ingest failure).
 */
@Service
@RequiredArgsConstructor
public class UploadDocumentUseCase {

    /**
     * Hard cap on upload size (5 MB). Enforced here as well as in the UI so the backend never
     * relies on the client's check alone. Kept below {@code spring.servlet.multipart.max-file-size}
     * so a slightly-too-big file is rejected with this friendly message rather than a raw 500.
     */
    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024;

    private static final List<String> ALLOWED_EXTENSIONS =
            List.of(".pdf", ".docx", ".doc", ".txt", ".md");

    private final AiDocumentRepository documentRepository;
    private final DocumentIngestService documentIngestService;

    @Transactional
    public DocumentResponse execute(UserEntity user, String title, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalStateException("The document file is empty or invalid.");
        }

        // Size gate first — cheapest rejection, and we never buffer an oversized file into memory.
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalStateException(
                    "The file exceeds the maximum allowed size (5MB). "
                            + "Please choose a smaller file or split the document.");
        }

        String fileName = StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename() : "document";

        // Whitelist by extension — the backend must not rely on the UI's `accept` filter alone.
        String lowerName = fileName.toLowerCase();
        if (ALLOWED_EXTENSIONS.stream().noneMatch(lowerName::endsWith)) {
            throw new IllegalStateException(
                    "Unsupported file format. Accepted: PDF, DOCX, DOC, TXT, MD.");
        }

        String resolvedTitle = StringUtils.hasText(title) ? title.trim() : fileName;

        // Read the bytes NOW: the multipart stream is tied to this request and is closed once we
        // return, so the background worker must receive an in-memory copy (safe — capped at 5 MB).
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Could not read the file content: " + e.getMessage());
        }

        // Persist the metadata row as "processing" (chunkCount == 0). saveAndFlush so the row is
        // committed and its audit fields (createdAt) are populated before we return it to the UI.
        // Replace-by-title is done by the worker AFTER the new version ingests successfully, so a
        // failed re-upload never destroys the previous good version.
        AiDocumentEntity doc = AiDocumentEntity.builder()
                .title(resolvedTitle)
                .fileName(fileName)
                .contentType(file.getContentType())
                .chunkCount(0)
                .uploadedBy(user)
                .build();
        doc = documentRepository.saveAndFlush(doc);

        // Hand off the heavy parse → chunk → embed → store work to the background executor.
        documentIngestService.ingestInBackground(doc.getDocumentId(), resolvedTitle, fileName, bytes);

        return DocumentResponse.from(doc); // chunkCount == 0 → UI shows "Processing…"
    }
}
