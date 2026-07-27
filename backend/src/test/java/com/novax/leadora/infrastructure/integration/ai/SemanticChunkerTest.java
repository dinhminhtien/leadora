package com.novax.leadora.infrastructure.integration.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the line-unwrapping step that runs before sentence splitting.
 *
 * <p>This is the cheapest place to get RAG ingestion badly wrong: the sentence splitter treats
 * every hard line break as a sentence end, and a PDF arrives from Tika broken at the visual right
 * margin, so without unwrapping one paragraph becomes many "sentences" — several times the
 * embedding calls, and chunk boundaries landing mid-sentence.
 */
class SemanticChunkerTest {

    @Test
    @DisplayName("rejoins a paragraph broken at the page margin")
    void joinsWrappedParagraphLines() {
        String pdfText = """
                Nhân viên kinh doanh có trách nhiệm liên hệ với khách hàng tiềm năng
                trong vòng 24 giờ kể từ khi lead được phân công, và ghi nhận kết quả
                vào hệ thống.""";

        String unwrapped = SemanticChunker.unwrapLines(pdfText);

        assertThat(unwrapped).doesNotContain("\n");
        assertThat(unwrapped).contains("tiềm năng trong vòng 24 giờ");
        assertThat(unwrapped).contains("kết quả vào hệ thống");
    }

    @Test
    @DisplayName("keeps the break after a completed sentence")
    void keepsBreakAfterSentenceEnd() {
        String text = "Điều 1. Phạm vi áp dụng.\nQuy định này áp dụng cho toàn bộ nhân viên.";
        assertThat(SemanticChunker.unwrapLines(text)).isEqualTo(text);
    }

    @Test
    @DisplayName("keeps structural breaks: headings, bullets and numbered clauses")
    void keepsStructuralBreaks() {
        String text = """
                QUY TRÌNH ĐẶT PHÒNG
                - Bước một: tiếp nhận yêu cầu
                - Bước hai: kiểm tra phòng trống
                2. Xác nhận với khách""";

        String unwrapped = SemanticChunker.unwrapLines(text);

        assertThat(unwrapped.lines().count()).isEqualTo(4);
    }

    @Test
    @DisplayName("a colon introduces a list, so its break is kept")
    void keepsBreakAfterColon() {
        String text = "Các bước thực hiện:\nliên hệ khách hàng";
        assertThat(SemanticChunker.unwrapLines(text)).contains("\n");
    }

    @Test
    @DisplayName("blank lines survive as paragraph separators")
    void preservesBlankLines() {
        String text = "Đoạn thứ nhất kết thúc ở đây.\n\nĐoạn thứ hai bắt đầu ở đây.";
        assertThat(SemanticChunker.unwrapLines(text)).contains("\n\n");
    }

    @Test
    @DisplayName("handles empty and single-line input without throwing")
    void handlesEdgeCases() {
        assertThat(SemanticChunker.unwrapLines("")).isEmpty();
        assertThat(SemanticChunker.unwrapLines("một dòng duy nhất")).isEqualTo("một dòng duy nhất");
    }
}
