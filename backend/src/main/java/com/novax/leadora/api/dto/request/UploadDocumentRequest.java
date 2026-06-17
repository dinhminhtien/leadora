package com.novax.leadora.api.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Metadata that accompanies a company-document upload for the RAG knowledge base.
 * The file itself is sent as a multipart part; this carries the optional title.
 */
@Data
public class UploadDocumentRequest {

    @Size(max = 255, message = "Title must be at most 255 characters")
    private String title;
}
