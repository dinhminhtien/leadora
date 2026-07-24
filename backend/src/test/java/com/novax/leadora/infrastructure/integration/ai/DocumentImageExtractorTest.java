package com.novax.leadora.infrastructure.integration.ai;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the real image-extraction path (no model calls): a DOCX/PDF is built in-memory with an
 * embedded picture and the extractor must recover exactly that picture, while filtering out
 * icon-sized images and formats that carry none.
 */
class DocumentImageExtractorTest {

    private final DocumentImageExtractor extractor = new DocumentImageExtractor();

    private static byte[] png(int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics g = img.getGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setColor(Color.BLACK);
        g.drawString("SENTINEL", 20, h / 2);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private static byte[] docxWithImage(int w, int h) throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph p = doc.createParagraph();
            XWPFRun run = p.createRun();
            run.addPicture(new ByteArrayInputStream(png(w, h)), Document.PICTURE_TYPE_PNG,
                    "img.png", Units.toEMU(w), Units.toEMU(h));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            return out.toByteArray();
        }
    }

    private static byte[] pdfWithImage(int w, int h) throws Exception {
        return pdfWithSameImageOnPages(1, w, h);
    }

    /** One image object drawn on {@code pages} pages — mimics a header/logo repeated across a scan. */
    private static byte[] pdfWithSameImageOnPages(int pages, int w, int h) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDImageXObject image = LosslessFactory.createFromImage(doc,
                    ImageIO.read(new ByteArrayInputStream(png(w, h))));
            for (int i = 0; i < pages; i++) {
                PDPage page = new PDPage();
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.drawImage(image, 40, 40, w, h);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    @Test
    @DisplayName("extracts an embedded image from a DOCX")
    void extractsFromDocx() throws Exception {
        List<byte[]> images = extractor.extractPngImages("doc.docx", docxWithImage(400, 300), 5);

        assertThat(images).hasSize(1);
        assertThat(ImageIO.read(new ByteArrayInputStream(images.get(0)))).isNotNull();
    }

    @Test
    @DisplayName("extracts an embedded image from a PDF")
    void extractsFromPdf() throws Exception {
        List<byte[]> images = extractor.extractPngImages("doc.pdf", pdfWithImage(400, 300), 5);

        assertThat(images).hasSize(1);
        assertThat(ImageIO.read(new ByteArrayInputStream(images.get(0)))).isNotNull();
    }

    @Test
    @DisplayName("skips icon-sized images (below the minimum dimension)")
    void skipsTinyImages() throws Exception {
        assertThat(extractor.extractPngImages("doc.docx", docxWithImage(120, 90), 5)).isEmpty();
    }

    @Test
    @DisplayName("honours the max-images cap")
    void honoursMaxCap() throws Exception {
        assertThat(extractor.extractPngImages("doc.pdf", pdfWithImage(400, 300), 0)).isEmpty();
    }

    @Test
    @DisplayName("returns nothing for formats with no embedded raster images (txt/md)")
    void ignoresTextFormats() {
        assertThat(extractor.extractPngImages("note.txt", "hello".getBytes(), 5)).isEmpty();
        assertThat(extractor.extractPngImages("readme.md", "# hi".getBytes(), 5)).isEmpty();
    }

    @Test
    @DisplayName("de-duplicates the same image repeated across pages (one OCR, not one per page)")
    void deduplicatesRepeatedImage() throws Exception {
        List<byte[]> images = extractor.extractPngImages("scan.pdf", pdfWithSameImageOnPages(4, 400, 300), 5);

        assertThat(images).hasSize(1);
    }

    /** Extractor with page rendering switched on (production default; off in a bare unit-test instance). */
    private static DocumentImageExtractor withPageRender() {
        DocumentImageExtractor e = new DocumentImageExtractor();
        ReflectionTestUtils.setField(e, "pageRenderEnabled", true);
        ReflectionTestUtils.setField(e, "pageRenderThreshold", 100);
        ReflectionTestUtils.setField(e, "pageRenderDpi", 150);
        return e;
    }

    @Test
    @DisplayName("renders the whole page (not just the embedded image) for a text-sparse PDF page")
    void rendersSparsePage() throws Exception {
        List<byte[]> images = withPageRender().extractPngImages("scan.pdf", pdfWithImage(400, 300), 5);

        assertThat(images).hasSize(1);
        BufferedImage got = ImageIO.read(new ByteArrayInputStream(images.get(0)));
        // A US-Letter page at 150 DPI is ~1275px wide — far larger than the 400px embedded image,
        // proving the page was rendered rather than the raster image merely lifted.
        assertThat(got.getWidth()).isGreaterThan(600);
    }

    @Test
    @DisplayName("downscales an oversized image below the max dimension before returning it")
    void downscalesOversizedImage() throws Exception {
        List<byte[]> images = extractor.extractPngImages("big.pdf", pdfWithImage(4200, 2800), 5);

        assertThat(images).hasSize(1);
        BufferedImage got = ImageIO.read(new ByteArrayInputStream(images.get(0)));
        assertThat(Math.max(got.getWidth(), got.getHeight())).isLessThanOrEqualTo(3072);
    }
}
