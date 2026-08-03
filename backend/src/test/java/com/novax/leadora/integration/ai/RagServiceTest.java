package com.novax.leadora.integration.ai;
import com.novax.leadora.infrastructure.integration.ai.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins how the two extraction routes over one upload — Tika's text layer and the vision
 * transcription of its images — are combined before chunking.
 *
 * <p>The chunker itself is real but running in its token-splitting mode, so these assertions
 * describe {@link RagService}'s own composition rather than the semantic splitter's judgement.
 */
class RagServiceTest {

    private final VectorStore vectorStore = mock(VectorStore.class);
    private final VisionOcrService visionOcr = mock(VisionOcrService.class);

    /** A real chunker with semantic mode off: short input in, one chunk out, no embedding calls. */
    private static SemanticChunker plainChunker() {
        SemanticChunker chunker = new SemanticChunker(mock(EmbeddingModel.class));
        ReflectionTestUtils.setField(chunker, "enabled", false);
        return chunker;
    }

    private RagService service() {
        return new RagService(vectorStore, plainChunker(), visionOcr);
    }

    private static byte[] txt(String body) {
        return body.getBytes(StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private List<Document> captureStoredChunks() {
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("text layer and vision OCR are joined into one document, not chunked separately")
    void mergesTextLayerAndOcrIntoOneChunk() {
        // Regression: a Word file whose content is one pasted-in screenshot still leaves a scrap in
        // its text layer. That scrap used to be handed to the chunker as a document of its own, so
        // the upload always produced at least two chunks — one of them an all-but-empty vector
        // carrying the document's title, competing for a slot in the top-K retrieval.
        UUID id = UUID.randomUUID();
        when(visionOcr.isEnabled()).thenReturn(true);
        when(visionOcr.ocr(anyString(), any(), anyString()))
                .thenReturn("Rule 1: office hours are 08:00-17:30. Rule 2: badge in at reception.");

        int chunks = service().ingest(id, "Company policy", "policy.txt", txt("Nội quy"));

        assertThat(chunks).isEqualTo(1);
        List<Document> stored = captureStoredChunks();
        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).getText())
                .contains("Nội quy")          // the text layer, in reading order first
                .contains("Rule 1")           // followed by the vision transcription
                .contains("Rule 2");
        assertThat(stored.get(0).getMetadata())
                .containsEntry("documentId", id.toString())
                .containsEntry("title", "Company policy")
                .containsEntry("fileName", "policy.txt");
    }

    @Test
    @DisplayName("a file with no text layer ingests its OCR text alone")
    void ingestsOcrOnlyDocument() {
        when(visionOcr.isEnabled()).thenReturn(true);
        when(visionOcr.ocr(anyString(), any(), anyString())).thenReturn("Scanned policy body.");

        int chunks = service().ingest(UUID.randomUUID(), "Scan", "scan.txt", txt("   "));

        assertThat(chunks).isEqualTo(1);
        assertThat(captureStoredChunks().get(0).getText()).isEqualTo("Scanned policy body.");
    }

    @Test
    @DisplayName("nothing extractable: reports 0 chunks and never writes to the vector store")
    void emptyDocumentIsNotStored() {
        when(visionOcr.isEnabled()).thenReturn(true);
        when(visionOcr.ocr(anyString(), any(), anyString())).thenReturn("");

        assertThat(service().ingest(UUID.randomUUID(), "Blank", "blank.txt", txt("  \n "))).isZero();
        verifyNoInteractions(vectorStore);
    }

    @Test
    @DisplayName("with OCR disabled the text layer alone is ingested, and vision is never called")
    void ocrDisabledIngestsTextLayerOnly() {
        when(visionOcr.isEnabled()).thenReturn(false);

        int chunks = service().ingest(UUID.randomUUID(), "Handbook", "handbook.txt",
                txt("Section 1. Attendance is recorded at the reception desk."));

        assertThat(chunks).isEqualTo(1);
        assertThat(captureStoredChunks().get(0).getText()).contains("Attendance is recorded");
        verify(visionOcr, never()).ocr(anyString(), any(), anyString());
    }
}
