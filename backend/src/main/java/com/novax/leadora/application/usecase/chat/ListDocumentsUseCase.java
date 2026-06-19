package com.novax.leadora.application.usecase.chat;

import com.novax.leadora.api.dto.response.DocumentResponse;
import com.novax.leadora.infrastructure.persistence.repository.AiDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** UC (optional) — List ingested company documents (shared knowledge base). */
@Service
@RequiredArgsConstructor
public class ListDocumentsUseCase {

    private final AiDocumentRepository documentRepository;

    @Transactional(readOnly = true)
    public List<DocumentResponse> execute() {
        return documentRepository.findAllWithUploader()
                .stream()
                .map(DocumentResponse::from)
                .toList();
    }
}
