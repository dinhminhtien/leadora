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

/** UC (optional) — Upload a company document into the RAG knowledge base. */
@Service
@RequiredArgsConstructor
public class UploadDocumentUseCase {

    private final AiDocumentRepository documentRepository;
    private final RagService ragService;

    @Transactional
    public DocumentResponse execute(UserEntity user, String title, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalStateException("Tệp tài liệu trống hoặc không hợp lệ.");
        }

        String fileName = StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename() : "document";
        String resolvedTitle = StringUtils.hasText(title) ? title.trim() : fileName;

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
