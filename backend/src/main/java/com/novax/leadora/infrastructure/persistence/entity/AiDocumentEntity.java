package com.novax.leadora.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Metadata for a company document ingested into the RAG knowledge base.
 * The actual text/embeddings live in the pgvector store (table {@code leadora_vector_store});
 * each chunk there carries this {@code documentId} in its metadata so a document can be
 * listed and deleted as a unit. Company documents are shared (not user-scoped).
 */
@Entity
@Table(name = "ai_documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiDocumentEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by")
    private UserEntity uploadedBy;
}
