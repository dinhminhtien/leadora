package com.novax.leadora.infrastructure.integration.ai;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * Pulls the <em>raster</em> images embedded in an uploaded document so they can be handed to the
 * vision model for OCR. Tika (used by {@link RagService}) only extracts a document's text layer, so
 * any wording that lives inside a picture — a scanned page, a chart, a screenshot of a table — is
 * invisible to RAG. This extractor recovers those pixels; {@link VisionOcrService} turns them back
 * into text.
 *
 * <p>Best-effort by design: a parse failure or an undecodable image is logged and skipped, never
 * thrown, so a single bad picture can't fail the whole ingest.
 */
@Slf4j
@Component
public class DocumentImageExtractor {

    /**
     * Images smaller than this on their shortest side are skipped. Logos, bullets, header rules and
     * icons carry no useful prose, and every image sent to the vision model costs a request against
     * the daily quota — so filtering them out is both cleaner (less OCR noise) and cheaper.
     */
    private static final int MIN_DIMENSION_PX = 200;

    /**
     * Longest side an image is downscaled to before being sent to the vision model. This is a
     * legibility-vs-safety cap, not a cost cap: a full 300-DPI A4 scan (~3500px) keeps small-font
     * text readable around here, while still bounding the inline request so a huge image can't blow
     * the API size limit (which would drop the OCR entirely). Raise it if dense scans read poorly.
     */
    private static final int MAX_DIMENSION_PX = 3072;

    /** Recursion guard: a malformed PDF can nest (or cycle) form XObjects — stop before it overflows. */
    private static final int MAX_FORM_DEPTH = 8;

    /**
     * Render whole PDF pages (instead of only lifting embedded raster images) for pages whose text
     * layer is sparse. Extracting embedded images alone misses content <em>drawn</em> as PDF vector
     * graphics — charts, diagrams, complex table rules — because those are not images. Rendering the
     * page hands the vision model the full visual, layout included. Text-rich pages are left to Tika
     * (embedded figures still lifted) so clean text is not needlessly re-OCR'd into near-duplicates.
     */
    @Value("${ai.rag.vision-ocr.page-render.enabled:true}")
    private boolean pageRenderEnabled;

    /** A page with fewer than this many characters of extractable text is treated as sparse → rendered. */
    @Value("${ai.rag.vision-ocr.page-render.text-threshold:100}")
    private int pageRenderThreshold;

    /** Rasterisation resolution for a rendered page. 200 balances small-font legibility and size. */
    @Value("${ai.rag.vision-ocr.page-render.dpi:200}")
    private int pageRenderDpi;

    /**
     * Returns each embedded image re-encoded as PNG bytes, capped at {@code max}. Only PDF and DOCX
     * carry embedded raster images in this pipeline; TXT/MD have none, and legacy binary DOC is not
     * mined here (rare, and its image model differs).
     */
    public List<byte[]> extractPngImages(String fileName, byte[] content, int max) {
        String lower = fileName == null ? "" : fileName.toLowerCase();
        if (max <= 0) {
            return List.of();
        }
        if (lower.endsWith(".pdf")) {
            return fromPdf(content, max);
        }
        if (lower.endsWith(".docx")) {
            return fromDocx(content, max);
        }
        return List.of();
    }

    private List<byte[]> fromPdf(byte[] content, int max) {
        List<byte[]> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        try (PDDocument doc = Loader.loadPDF(content)) {
            PDFRenderer renderer = new PDFRenderer(doc);
            PDFTextStripper stripper = pageRenderEnabled ? new PDFTextStripper() : null;
            int pages = doc.getNumberOfPages();
            for (int i = 0; i < pages && out.size() < max; i++) {
                try {
                    if (pageRenderEnabled && isSparsePage(stripper, doc, i)) {
                        // Sparse (scanned / vector-heavy) page → render it whole so the model sees everything.
                        addPng(renderer.renderImageWithDPI(i, pageRenderDpi, ImageType.RGB), out, seen);
                    } else {
                        // Text-rich page → only lift its embedded raster figures; Tika already has the text.
                        collectFromResources(doc.getPage(i).getResources(), out, seen, max, 0);
                    }
                } catch (Exception pageEx) {
                    log.debug("Skipping PDF page {}: {}", i, pageEx.getMessage());
                }
            }
        } catch (Exception ex) {
            log.warn("PDF image extraction failed: {}", ex.getMessage());
        }
        return out;
    }

    /** True when page {@code i} (0-based) has almost no extractable text — the render-the-page signal. */
    private boolean isSparsePage(PDFTextStripper stripper, PDDocument doc, int i) throws IOException {
        stripper.setStartPage(i + 1);
        stripper.setEndPage(i + 1);
        return stripper.getText(doc).strip().length() < pageRenderThreshold;
    }

    /** Walks a page's XObjects, recursing into form XObjects, collecting every image it finds. */
    private void collectFromResources(PDResources res, List<byte[]> out, Set<String> seen, int max, int depth) {
        if (res == null || depth > MAX_FORM_DEPTH) {
            return;
        }
        for (var name : res.getXObjectNames()) {
            if (out.size() >= max) {
                return;
            }
            try {
                PDXObject xobj = res.getXObject(name);
                if (xobj instanceof PDImageXObject img) {
                    addPng(img.getImage(), out, seen);
                } else if (xobj instanceof PDFormXObject form) {
                    collectFromResources(form.getResources(), out, seen, max, depth + 1);
                }
            } catch (Exception ex) {
                log.debug("Skipping one PDF XObject: {}", ex.getMessage());
            }
        }
    }

    private List<byte[]> fromDocx(byte[] content, int max) {
        List<byte[]> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(content))) {
            for (XWPFPictureData pic : doc.getAllPictures()) {
                if (out.size() >= max) {
                    break;
                }
                // ImageIO decodes PNG/JPEG/GIF/BMP; EMF/WMF (vector) return null and are skipped.
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(pic.getData()));
                addPng(img, out, seen);
            }
        } catch (Exception ex) {
            log.warn("DOCX image extraction failed: {}", ex.getMessage());
        }
        return out;
    }

    /**
     * Filters by size, downscales, flattens transparency onto white, re-encodes to PNG, and adds it
     * <em>only if not already collected</em>. The de-dup matters for cost: a header/logo repeated on
     * every page of a PDF would otherwise be OCR'd once per page, each a request against the daily
     * quota — de-dup by content hash collapses those to a single vision call.
     */
    private void addPng(BufferedImage img, List<byte[]> out, Set<String> seen) {
        if (img == null || Math.min(img.getWidth(), img.getHeight()) < MIN_DIMENSION_PX) {
            return;
        }
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(normalize(img), "png", baos);
            byte[] png = baos.toByteArray();
            if (seen.add(sha256(png))) { // add() is false when the hash was already present → duplicate
                out.add(png);
            }
        } catch (Exception ex) {
            log.debug("Could not re-encode an image to PNG: {}", ex.getMessage());
        }
    }

    /** Downscales to {@link #MAX_DIMENSION_PX} on the longest side and flattens to opaque RGB in one pass. */
    private static BufferedImage normalize(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        double scale = Math.max(w, h) > MAX_DIMENSION_PX ? (double) MAX_DIMENSION_PX / Math.max(w, h) : 1.0;
        int nw = Math.max(1, (int) Math.round(w * scale));
        int nh = Math.max(1, (int) Math.round(h * scale));
        // TYPE_INT_RGB drops any alpha channel, painting transparency onto the white background.
        BufferedImage rgb = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        var g = rgb.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img, 0, 0, nw, nh, Color.WHITE, null);
        g.dispose();
        return rgb;
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (Exception e) {
            return Integer.toHexString(Arrays.hashCode(data)); // SHA-256 is always present; belt-and-braces
        }
    }
}
