package com.novax.leadora.infrastructure.integration.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Embedding-based <b>semantic</b> chunker for RAG ingestion.
 *
 * <p>Instead of cutting documents at a fixed token count (which can slice a sentence or a coherent
 * idea in half, hurting retrieval), this splits on <em>meaning</em>: it breaks the text into
 * sentences, embeds each one (with a small neighbour window so a single short sentence doesn't
 * skew its vector), measures the semantic distance between consecutive sentences, and starts a new
 * chunk wherever that distance spikes above an adaptive (percentile) threshold — i.e. where the
 * topic shifts. The result is chunks whose content is internally coherent.
 *
 * <p><b>Cost / latency:</b> this embeds every sentence at upload time (on top of embedding the final
 * chunks for storage), so ingestion is heavier than token splitting. That is an acceptable trade —
 * uploads are infrequent and retrieval quality matters more. Any failure (embedding API down,
 * pathological input) falls back to {@link TokenTextSplitter} so ingestion never breaks.
 */
@Slf4j
@Component
public class SemanticChunker {

    private final EmbeddingModel embeddingModel;

    /** Master switch — set {@code ai.rag.semantic-chunking.enabled=false} to use plain token splitting. */
    @Value("${ai.rag.semantic-chunking.enabled:true}")
    private boolean enabled;

    /**
     * Distance percentile (0–100) above which a sentence boundary becomes a chunk break. Higher →
     * fewer, larger chunks. 90 is a sensible default (top ~10% topic shifts become breaks).
     */
    @Value("${ai.rag.semantic-chunking.breakpoint-percentile:90}")
    private double breakpointPercentile;

    /** Neighbour window: each sentence is embedded together with this many sentences on each side. */
    @Value("${ai.rag.semantic-chunking.buffer-size:1}")
    private int bufferSize;

    /** Hard cap on a chunk's length; an over-long coherent run is split further so vectors stay focused. */
    @Value("${ai.rag.semantic-chunking.max-chars:1800}")
    private int maxChars;

    /** Chunks shorter than this are merged into the previous one to avoid tiny, low-signal fragments. */
    @Value("${ai.rag.semantic-chunking.min-chars:160}")
    private int minChars;

    /** Above this sentence count, semantic chunking is skipped (too many embed calls) — token split instead. */
    @Value("${ai.rag.semantic-chunking.max-sentences:1500}")
    private int maxSentences;

    /** Sub-batch size for the sentence embedding calls (keeps each request within provider limits). */
    private static final int EMBED_BATCH = 64;

    private final TokenTextSplitter fallback = new TokenTextSplitter();

    public SemanticChunker(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    /**
     * Splits the parsed documents into semantically coherent chunks, stamping each chunk's metadata
     * with the parent document's original metadata plus {@code baseMetadata} (documentId/title/file).
     */
    public List<Document> chunk(List<Document> rawDocs, Map<String, Object> baseMetadata) {
        List<Document> out = new ArrayList<>();
        for (Document doc : rawDocs) {
            String text = doc.getText();
            if (text == null || text.isBlank()) {
                continue;
            }
            List<String> pieces = splitText(text);
            for (String piece : pieces) {
                if (piece == null || piece.isBlank()) {
                    continue;
                }
                Map<String, Object> md = new HashMap<>(doc.getMetadata());
                if (baseMetadata != null) {
                    md.putAll(baseMetadata);
                }
                out.add(Document.builder().text(piece).metadata(md).build());
            }
        }
        return out;
    }

    /** Splits a single text into semantic chunks, falling back to token splitting on any problem. */
    List<String> splitText(String text) {
        try {
            if (!enabled) {
                return tokenSplit(text);
            }
            List<String> sentences = splitIntoSentences(text);
            if (sentences.size() < 3 || sentences.size() > maxSentences) {
                // Too small to benefit, or too large to embed economically.
                return enforceSizeBounds(sentences.isEmpty() ? tokenSplit(text) : groupAll(sentences));
            }

            List<String> windowed = applyBuffer(sentences, Math.max(0, bufferSize));
            List<float[]> embeddings = embedInBatches(windowed);
            if (embeddings.size() != sentences.size()) {
                log.warn("Semantic chunk embedding count mismatch ({} vs {}); falling back to token split",
                        embeddings.size(), sentences.size());
                return tokenSplit(text);
            }

            List<Double> distances = new ArrayList<>();
            for (int i = 0; i < embeddings.size() - 1; i++) {
                distances.add(1.0 - cosine(embeddings.get(i), embeddings.get(i + 1)));
            }
            double threshold = percentile(distances, breakpointPercentile);

            // Build chunks: break after sentence i when the i→i+1 distance exceeds the threshold.
            List<String> chunks = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            for (int i = 0; i < sentences.size(); i++) {
                if (current.length() > 0) {
                    current.append(' ');
                }
                current.append(sentences.get(i).trim());
                boolean lastSentence = i == sentences.size() - 1;
                boolean breakHere = !lastSentence && distances.get(i) > threshold;
                if (breakHere || lastSentence) {
                    chunks.add(current.toString().trim());
                    current.setLength(0);
                }
            }
            return enforceSizeBounds(chunks);
        } catch (Exception ex) {
            log.warn("Semantic chunking failed ({}); falling back to token split", ex.getMessage());
            return tokenSplit(text);
        }
    }

    /** Splits text into sentences on terminators (., !, ?, …) and hard line breaks. */
    private List<String> splitIntoSentences(String text) {
        String[] parts = text.split("(?<=[.!?…])\\s+|\\R+");
        List<String> sentences = new ArrayList<>();
        for (String p : parts) {
            String s = p.trim();
            if (!s.isEmpty()) {
                sentences.add(s);
            }
        }
        return sentences;
    }

    /** Builds a windowed view: each entry is sentence i concatenated with its neighbours (±buffer). */
    private List<String> applyBuffer(List<String> sentences, int buffer) {
        if (buffer == 0) {
            return sentences;
        }
        List<String> windowed = new ArrayList<>(sentences.size());
        for (int i = 0; i < sentences.size(); i++) {
            int from = Math.max(0, i - buffer);
            int to = Math.min(sentences.size() - 1, i + buffer);
            StringBuilder sb = new StringBuilder();
            for (int j = from; j <= to; j++) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(sentences.get(j));
            }
            windowed.add(sb.toString());
        }
        return windowed;
    }

    private List<float[]> embedInBatches(List<String> texts) {
        List<float[]> all = new ArrayList<>(texts.size());
        for (int from = 0; from < texts.size(); from += EMBED_BATCH) {
            int to = Math.min(texts.size(), from + EMBED_BATCH);
            all.addAll(embeddingModel.embed(texts.subList(from, to)));
        }
        return all;
    }

    /** Merge under-sized chunks forward and hard-split over-sized ones, so chunk sizes stay sensible. */
    private List<String> enforceSizeBounds(List<String> chunks) {
        // Split anything longer than maxChars (on sentence boundaries, greedily).
        List<String> sized = new ArrayList<>();
        for (String c : chunks) {
            if (c.length() <= maxChars) {
                sized.add(c);
            } else {
                sized.addAll(hardSplit(c));
            }
        }
        // Merge tiny trailing/leading fragments into their neighbour.
        List<String> merged = new ArrayList<>();
        for (String c : sized) {
            if (!merged.isEmpty() && (c.length() < minChars
                    || merged.get(merged.size() - 1).length() < minChars)) {
                String combined = merged.get(merged.size() - 1) + " " + c;
                if (combined.length() <= maxChars) {
                    merged.set(merged.size() - 1, combined);
                    continue;
                }
            }
            merged.add(c);
        }
        return merged;
    }

    private List<String> hardSplit(String text) {
        List<String> parts = new ArrayList<>();
        String[] sentences = text.split("(?<=[.!?…])\\s+");
        StringBuilder cur = new StringBuilder();
        for (String s : sentences) {
            if (cur.length() + s.length() + 1 > maxChars && cur.length() > 0) {
                parts.add(cur.toString().trim());
                cur.setLength(0);
            }
            if (s.length() > maxChars) {
                // A single monster "sentence" (e.g. a table row) — chop by characters.
                for (int i = 0; i < s.length(); i += maxChars) {
                    parts.add(s.substring(i, Math.min(s.length(), i + maxChars)));
                }
            } else {
                if (cur.length() > 0) {
                    cur.append(' ');
                }
                cur.append(s);
            }
        }
        if (cur.length() > 0) {
            parts.add(cur.toString().trim());
        }
        return parts;
    }

    private List<String> groupAll(List<String> sentences) {
        return List.of(String.join(" ", sentences));
    }

    /** Token-splitter fallback: returns the chunk texts produced by Spring AI's TokenTextSplitter. */
    private List<String> tokenSplit(String text) {
        return fallback.apply(List.of(new Document(text)))
                .stream().map(Document::getText).toList();
    }

    private static double cosine(float[] a, float[] b) {
        double dot = 0, na = 0, nb = 0;
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0) {
            return 0;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private static double percentile(List<Double> values, double pct) {
        if (values.isEmpty()) {
            return Double.MAX_VALUE; // no distances → no breakpoints
        }
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(Double::compareTo);
        int idx = (int) Math.ceil(pct / 100.0 * sorted.size()) - 1;
        idx = Math.max(0, Math.min(sorted.size() - 1, idx));
        return sorted.get(idx);
    }
}
