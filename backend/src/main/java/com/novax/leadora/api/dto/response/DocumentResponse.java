package com.novax.leadora.api.dto.response;

import com.novax.leadora.infrastructure.persistence.entity.AiDocumentEntity;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class DocumentResponse {

    private UUID documentId;
    private String title;
    private String fileName;
    private String contentType;
    private int chunkCount;
    /** True while the document is still being parsed/embedded in the background (chunkCount == 0). */
    private boolean processing;
    private OffsetDateTime createdAt;
    private UUID uploadedById;
    private String uploadedByName;

    public static DocumentResponse from(AiDocumentEntity doc) {
        return DocumentResponse.builder()
                .documentId(doc.getDocumentId())
                .title(doc.getTitle())
                .fileName(doc.getFileName())
                .contentType(doc.getContentType())
                .chunkCount(doc.getChunkCount())
                .processing(doc.getChunkCount() == 0)
                .createdAt(doc.getCreatedAt())
                .uploadedById(doc.getUploadedBy() != null ? doc.getUploadedBy().getUserId() : null)
                .uploadedByName(doc.getUploadedBy() != null ? doc.getUploadedBy().getFullName() : null)
                .build();
    }
}
