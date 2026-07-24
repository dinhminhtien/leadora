package com.novax.leadora.infrastructure.integration.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Reads the text <em>inside images</em> of an uploaded document, using the same Google Gemini
 * multimodal model already wired for chat ({@code gemini-2.5-flash}). Tika only extracts a
 * document's text layer, so a scanned page or a chart/screenshot is otherwise lost to RAG; this
 * service transcribes those pixels back into text that {@link RagService} can chunk and embed.
 *
 * <p><b>Why it is gated and capped.</b> Vision OCR runs on the chat model, so every image is a
 * request against the <em>same</em> daily free-tier quota as the assistant, and an image costs more
 * tokens than a line of text. Left unbounded, one multi-page scan would exhaust the day's quota and
 * starve the chat. Hence the default is {@code OFF}, {@code SCANNED_ONLY} spends nothing on documents
 * that already have text, and {@code max-images} hard-caps the spend per document.
 *
 * <p>Best-effort, exactly like the rest of RAG: any failure returns an empty string and ingestion
 * continues text-only rather than failing.
 */
@Slf4j
@Service
public class VisionOcrService {

    /**
     * When vision OCR runs.
     * <ul>
     *   <li>{@code OFF} — never (default). No image is sent to the model.</li>
     *   <li>{@code SCANNED_ONLY} — only when Tika extracted little/no text, i.e. a scanned or
     *       image-only document. Cheap: digital documents that already have text are left untouched.</li>
     *   <li>{@code ALL_IMAGES} — every embedded image, even in a text-rich document (reads charts,
     *       screenshots, tables-as-pictures). Highest quota cost.</li>
     * </ul>
     */
    public enum Mode { OFF, SCANNED_ONLY, ALL_IMAGES }

    /** A transcription engine, not a chat turn — narrow enough to curb hallucination, rich enough to index visuals. */
    private static final String OCR_SYSTEM = """
            You are a precise document-vision engine, NOT a chat assistant. Your output is indexed for
            retrieval, so it must faithfully capture everything in the image and invent nothing.

            1. TEXT: transcribe EVERY legible character verbatim, in natural reading order. Render any
               table as a GitHub-Markdown table, preserving rows and columns. Keep numbers, codes,
               dates and proper nouns exactly as shown.
            2. VISUALS: if the image is (or contains) a chart, graph, diagram, floor-plan or photo,
               ALSO add a short, factual description under a line "[Figure] ...": what it depicts, the
               axes/series/legend, and any values or trend that are actually printed. This makes the
               figure findable — but describe ONLY what is visibly present.
            3. NEVER translate, summarise the document, editorialise, or infer values that are not
               shown. If a character is not clearly legible, omit it rather than guess.
            4. If the image has no legible text AND nothing meaningful to describe (blank, texture,
               solid colour, decorative rule), output EXACTLY this token and nothing else: [[NO_TEXT]]
            """;

    private static final String OCR_USER = "Transcribe and, where relevant, describe this document image.";

    /** Sentinel the model returns for a picture with no readable text; such results are dropped. */
    private static final String NO_TEXT = "[[NO_TEXT]]";

    private final ChatClient visionClient;
    private final DocumentImageExtractor imageExtractor;

    @Value("${ai.rag.vision-ocr.mode:OFF}")
    private Mode mode;

    /** At or above this many characters of Tika text, a document is "digital" and SCANNED_ONLY skips it. */
    @Value("${ai.rag.vision-ocr.scanned-text-threshold:200}")
    private int scannedTextThreshold;

    /** Hard cap on images sent to the vision model per document — the main guard on quota/cost. */
    @Value("${ai.rag.vision-ocr.max-images:20}")
    private int maxImages;

    /**
     * Optional model override for OCR only. Blank = use the same model as chat ({@code gemini-2.5-flash}).
     * Set to e.g. {@code gemini-2.5-pro} to trade quota for markedly better reading of dense scans,
     * complex tables and figures, without changing the chat model.
     */
    @Value("${ai.rag.vision-ocr.model:}")
    private String visionModel;

    public VisionOcrService(ChatClient.Builder chatClientBuilder, DocumentImageExtractor imageExtractor) {
        // A dedicated client (no chat system prompt) sharing the same underlying Gemini model.
        this.visionClient = chatClientBuilder.build();
        this.imageExtractor = imageExtractor;
    }

    /** True when any mode other than OFF is configured — lets {@link RagService} skip the work entirely. */
    public boolean isEnabled() {
        return mode != Mode.OFF;
    }

    /**
     * Transcribes the document's images and returns the combined text, or {@code ""} when OCR does
     * not apply (disabled; SCANNED_ONLY on a text-rich document; no images; nothing legible; error).
     *
     * @param fileName original filename — decides the extractor (pdf/docx) and appears in logs
     * @param content  the raw uploaded bytes
     * @param tikaText the text Tika already extracted, used to decide SCANNED_ONLY applicability
     */
    public String ocr(String fileName, byte[] content, String tikaText) {
        if (mode == Mode.OFF) {
            return "";
        }
        int tikaLen = tikaText == null ? 0 : tikaText.strip().length();
        if (mode == Mode.SCANNED_ONLY && tikaLen >= scannedTextThreshold) {
            return ""; // already has ample text — don't spend vision quota on a digital document
        }
        try {
            List<byte[]> images = imageExtractor.extractPngImages(fileName, content, maxImages);
            if (images.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            int transcribed = 0;
            for (byte[] png : images) {
                String text = transcribeOne(png);
                if (StringUtils.hasText(text)) {
                    sb.append(text).append("\n\n");
                    transcribed++;
                }
            }
            if (transcribed > 0) {
                log.info("Vision OCR recovered text from {}/{} image(s) of {}",
                        transcribed, images.size(), fileName);
            }
            return sb.toString().strip();
        } catch (Exception ex) {
            log.warn("Vision OCR failed for {} ({}) — continuing text-only", fileName, ex.getMessage());
            return "";
        }
    }

    /** OCRs a single PNG; returns "" for an unreadable image or on any model error. */
    private String transcribeOne(byte[] png) {
        try {
            Media media = new Media(MimeTypeUtils.IMAGE_PNG, new ByteArrayResource(png));
            String out = visionClient.prompt()
                    .options(ocrOptions())
                    .system(OCR_SYSTEM)
                    .user(u -> u.text(OCR_USER).media(media))
                    .call()
                    .content();
            if (out == null) {
                return "";
            }
            out = out.strip();
            if (out.isEmpty() || out.contains(NO_TEXT)) {
                return "";
            }
            return out;
        } catch (Exception ex) {
            log.warn("Vision OCR call failed on one image: {}", ex.getMessage());
            return "";
        }
    }

    /** Temperature 0 for a faithful, repeatable transcription; optional stronger model for OCR only. */
    private ChatOptions ocrOptions() {
        ChatOptions.Builder builder = ChatOptions.builder().temperature(0.0);
        if (StringUtils.hasText(visionModel)) {
            builder.model(visionModel.trim());
        }
        return builder.build();
    }
}
