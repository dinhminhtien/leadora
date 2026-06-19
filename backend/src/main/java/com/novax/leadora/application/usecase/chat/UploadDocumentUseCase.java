package com.novax.leadora.application.usecase.chat;

import com.novax.leadora.api.dto.response.DocumentResponse;
import com.novax.leadora.infrastructure.integration.ai.RagService;
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

/** UC (optional) — Upload a company document into the RAG knowledge base. */
@Service
@RequiredArgsConstructor
public class UploadDocumentUseCase {

    private static final List<String> ALLOWED_EXTENSIONS =
            List.of(".pdf", ".docx", ".doc", ".txt", ".md");

    private final AiDocumentRepository documentRepository;
    private final RagService ragService;

    @Transactional
    public DocumentResponse execute(UserEntity user, String title, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalStateException("Tệp tài liệu trống hoặc không hợp lệ.");
        }

        String fileName = StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename() : "document";

        // Whitelist by extension — the backend must not rely on the UI's `accept` filter alone.
        String lowerName = fileName.toLowerCase();
        if (ALLOWED_EXTENSIONS.stream().noneMatch(lowerName::endsWith)) {
            throw new IllegalStateException(
                    "Định dạng tệp không được hỗ trợ. Chỉ chấp nhận: PDF, DOCX, DOC, TXT, MD.");
        }

        String resolvedTitle = StringUtils.hasText(title) ? title.trim() : fileName;

        // Replace-by-title: re-uploading a document with the same title replaces the old version
        // (its metadata row + all its chunks) so the assistant never mixes stale and fresh content.
        // Same transaction as the new ingest below → if ingest fails, the old version is rolled back in.
        List<AiDocumentEntity> previousVersions = documentRepository.findByTitleIgnoreCase(resolvedTitle);
        for (AiDocumentEntity old : previousVersions) {
            ragService.deleteDocument(old.getDocumentId());
            documentRepository.delete(old);
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Không đọc được nội dung tệp: " + e.getMessage());
        }

        AiDocumentEntity doc = AiDocumentEntity.builder()
                .title(resolvedTitle)
                .fileName(fileName)
                .contentType(file.getContentType())
                .chunkCount(0)
                .uploadedBy(user)
                .build();
        doc = documentRepository.save(doc); // obtain the id used to tag vector-store chunks

        int chunks = ragService.ingest(doc.getDocumentId(), resolvedTitle, fileName, bytes);
        if (chunks == 0) {
            // Roll back: no orphan metadata row for a document we couldn't read.
            throw new IllegalStateException("Không trích xuất được nội dung văn bản từ tệp này.");
        }

        doc.setChunkCount(chunks);
        return DocumentResponse.from(documentRepository.save(doc));
    }
}
