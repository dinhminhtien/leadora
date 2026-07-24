package com.novax.leadora.infrastructure.integration.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Covers the quota-guarding gates that decide whether an image is ever sent to the model. The model
 * call itself needs a live Gemini key, so it is not exercised here; these tests pin the cheap,
 * deterministic decisions that keep the daily quota from being burned.
 */
class VisionOcrServiceTest {

    private final DocumentImageExtractor extractor = mock(DocumentImageExtractor.class);

    private VisionOcrService service(VisionOcrService.Mode mode, int threshold) {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(mock(ChatClient.class));
        VisionOcrService svc = new VisionOcrService(builder, extractor);
        ReflectionTestUtils.setField(svc, "mode", mode);
        ReflectionTestUtils.setField(svc, "scannedTextThreshold", threshold);
        ReflectionTestUtils.setField(svc, "maxImages", 5);
        return svc;
    }

    @Test
    @DisplayName("OFF: disabled, and never touches the extractor or the model")
    void offModeDoesNothing() {
        VisionOcrService svc = service(VisionOcrService.Mode.OFF, 200);

        assertThat(svc.isEnabled()).isFalse();
        assertThat(svc.ocr("doc.pdf", new byte[]{1, 2, 3}, "")).isEmpty();
        verifyNoInteractions(extractor);
    }

    @Test
    @DisplayName("SCANNED_ONLY: a text-rich document is skipped without extracting images")
    void scannedOnlySkipsTextRichDocument() {
        VisionOcrService svc = service(VisionOcrService.Mode.SCANNED_ONLY, 200);
        String ampleText = "x".repeat(500);

        assertThat(svc.isEnabled()).isTrue();
        assertThat(svc.ocr("doc.pdf", new byte[]{1, 2, 3}, ampleText)).isEmpty();
        verifyNoInteractions(extractor);
    }

    @Test
    @DisplayName("no images extracted: returns empty, never calls the model")
    void noImagesReturnsEmpty() {
        VisionOcrService svc = service(VisionOcrService.Mode.ALL_IMAGES, 200);
        when(extractor.extractPngImages("doc.pdf", new byte[]{1}, 5)).thenReturn(java.util.List.of());

        assertThat(svc.ocr("doc.pdf", new byte[]{1}, "anything")).isEmpty();
    }
}
