package com.novax.leadora.api.controller;

import com.novax.leadora.api.dto.response.DocumentResponse;
import com.novax.leadora.application.usecase.chat.DeleteDocumentUseCase;
import com.novax.leadora.application.usecase.chat.ListDocumentsUseCase;
import com.novax.leadora.application.usecase.chat.UploadDocumentUseCase;
import com.novax.leadora.common.response.ApiResponse;
import com.novax.leadora.common.security.CurrentUserProvider;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/** Company-document knowledge base for the chat assistant's RAG (shared across users). */
@RestController
@RequestMapping("/api/v1/chat/documents")
@RequiredArgsConstructor
public class ChatDocumentController {

    private final CurrentUserProvider currentUserProvider;
    private final UploadDocumentUseCase uploadDocumentUseCase;
    private final ListDocumentsUseCase listDocumentsUseCase;
    private final DeleteDocumentUseCase deleteDocumentUseCase;

    /** Upload a document (PDF / DOCX / TXT / MD) and ingest it for RAG. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<DocumentResponse>> upload(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam("file") MultipartFile file) {
        UserEntity user = currentUserProvider.resolve(userId);
        DocumentResponse doc = uploadDocumentUseCase.execute(user, title, file);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(doc, "Document ingested"));
    }

    /** List ingested documents. */
    @GetMapping
    public ResponseEntity<ApiResponse<List<DocumentResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(listDocumentsUseCase.execute()));
    }

    /** Delete a document and its embeddings. */
    @DeleteMapping("/{documentId}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID documentId) {
        deleteDocumentUseCase.execute(documentId);
        return ResponseEntity.ok(ApiResponse.success(null, "Document deleted"));
    }
}
