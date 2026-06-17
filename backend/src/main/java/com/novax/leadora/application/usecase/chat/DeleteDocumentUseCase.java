package com.novax.leadora.application.usecase.chat;

import com.novax.leadora.common.exception.ResourceNotFoundException;
import com.novax.leadora.infrastructure.integration.ai.RagService;
import com.novax.leadora.infrastructure.persistence.entity.AiDocumentEntity;
import com.novax.leadora.infrastructure.persistence.repository.AiDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** UC (optional) — Delete a company document and all its chunks from the RAG store. */
@Service
@RequiredArgsConstructor
public class DeleteDocumentUseCase {

    private final AiDocumentRepository documentRepository;
    private final RagService ragService;

    @Transactional
    public void execute(UUID documentId) {
        AiDocumentEntity doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

        ragService.deleteDocument(documentId); // remove chunks from the vector store
        documentRepository.delete(doc);
    }
}
