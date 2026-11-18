package com.autoparts.erp.ai.rag.ingestion;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.DoubleSummaryStatistics;
import java.util.IntSummaryStatistics;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Empirical benchmark and evaluation suite for Stage 3C Phase 2.
 *
 * <p>Measures baseline behavior of {@link DocumentChunker} (500 target tokens, 50 overlap tokens)
 * across the 4 representative corpus documents (Markdown, TXT, PDF).
 * Evaluates token estimation heuristic vs actual word/character counts, heading hierarchy inheritance,
 * paragraph boundary preservation against raw sources, sentence boundary heuristics,
 * exact suffix-to-prefix chunk overlap, and identifies sizing anomalies (undersized/oversized).</p>
 */
class ChunkingBenchmarkTest {

    private final PdfDocumentParser pdfParser = new PdfDocumentParser();
    private final TextDocumentParser textParser = new TextDocumentParser();
    private final DocumentChunker chunker = new DocumentChunker(500, 50);

    @BeforeAll
    static void ensureFixturesExist() throws IOException {
        Path pdfPath = Path.of("src/test/resources/knowledge/POL-RET-001.pdf");
        if (!Files.exists(pdfPath)) {
            Files.createDirectories(pdfPath.getParent());
            byte[] pdfBytes = GeneratePdfCorpusFixture.createPdfBytes();
            Files.write(pdfPath, pdfBytes);
        }
    }

    private byte[] loadFixtureBytes(String resourceName) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("knowledge/" + resourceName)) {
            if (in != null) {
                return in.readAllBytes();
            }
        }
        Path path = Path.of("src/test/resources/knowledge", resourceName);
        if (Files.exists(path)) {
            return Files.readAllBytes(path);
        }
        throw new IOException("Resource not found: knowledge/" + resourceName);
    }

    public record DocumentEvaluation(
            String docCode,
            String format,
            String docTitle,
            int rawCharCount,
            int rawWordCount,
            int estimatedRawTokens,
            List<DocumentChunker.ChunkDraft> chunks,
            List<ChunkMetrics> metrics
    ) {}

    public record ChunkMetrics(
            int chunkIndex,
            String sectionTitle,
            int charCount,
            int wordCount,
            int estimatedTokens,
            double charsPerToken,
            double wordsPerToken,
            boolean endsWithTerminalPunctuation,
            boolean isOrphanHeader,
            boolean isListOrTableEnd,
            boolean isUndersized, // < 100 tokens
            boolean isOversized   // > 500 tokens
    ) {}

    public record OverlapMeasurement(
            int fromChunk,
            int toChunk,
            int overlappingWords,
            int estimatedOverlapTokens,
            String overlapSnippet
    ) {}

    private DocumentEvaluation evaluateDocument(String docCode, String format, String filename, String title) throws IOException {
        byte[] bytes = loadFixtureBytes(filename);
        String text;
        if ("PDF".equalsIgnoreCase(format)) {
            text = pdfParser.extractText(bytes);
        } else {
            text = textParser.extractText(bytes);
        }

        int rawChars = text.length();
        int rawWords = text.trim().split("\\s+").length;
        int rawEstTokens = DocumentChunker.estimateTokens(text);

        List<DocumentChunker.ChunkDraft> drafts = chunker.chunk(text, title);
        List<ChunkMetrics> metricsList = new ArrayList<>();

        for (DocumentChunker.ChunkDraft draft : drafts) {
            String chunkText = draft.text();
            int chars = chunkText.length();
            int words = chunkText.trim().split("\\s+").length;
            int tokens = draft.estimatedTokens();
            double cpt = tokens > 0 ? (double) chars / tokens : 0.0;
            double wpt = tokens > 0 ? (double) words / tokens : 0.0;

            String trimmed = chunkText.trim();
            boolean endsPunctuation = trimmed.endsWith(".") || trimmed.endsWith("?") || trimmed.endsWith("!")
                    || trimmed.endsWith(".\"") || trimmed.endsWith("?\"") || trimmed.endsWith("!\"");

            boolean orphanHeader = trimmed.startsWith("#") && !trimmed.contains("\n\n");
            boolean listOrTableEnd = !endsPunctuation && !orphanHeader;
            boolean undersized = tokens < 100;
            boolean oversized = tokens > 500;

            metricsList.add(new ChunkMetrics(
                    draft.chunkIndex(),
                    draft.sectionTitle(),
                    chars,
                    words,
                    tokens,
                    cpt,
                    wpt,
                    endsPunctuation,
                    orphanHeader,
                    listOrTableEnd,
                    undersized,
                    oversized
            ));
        }

        return new DocumentEvaluation(docCode, format, title, rawChars, rawWords, rawEstTokens, drafts, metricsList);
    }

    static OverlapMeasurement measureChunkOverlap(String chunkA, String chunkB, int fromIdx, int toIdx) {
        String[] wordsA = chunkA.trim().split("\\s+");
        String[] wordsB = chunkB.trim().split("\\s+");

        int maxCheck = Math.min(wordsA.length, wordsB.length);
        int matchedWords = 0;

        for (int k = maxCheck; k >= 1; k--) {
            boolean matches = true;
            for (int i = 0; i < k; i++) {
                if (!wordsA[wordsA.length - k + i].equals(wordsB[i])) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                matchedWords = k;
                break;
            }
        }

        if (matchedWords == 0) {
            return new OverlapMeasurement(fromIdx, toIdx, 0, 0, "");
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < matchedWords; i++) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(wordsB[i]);
        }
        String snippet = sb.toString();
        int estTokens = DocumentChunker.estimateTokens(snippet);
        return new OverlapMeasurement(fromIdx, toIdx, matchedWords, estTokens, snippet);
    }

    @Test
    @DisplayName("Execute full empirical chunking benchmark on the 4 corpus fixtures")
    void shouldBenchmarkExistingChunkingBehaviorAcrossCorpus() throws IOException {
        List<DocumentEvaluation> evaluations = List.of(
                evaluateDocument("POL-WAR-001", "MARKDOWN", "POL-WAR-001.md", "Auto Parts Warranty Policy"),
                evaluateDocument("SOP-CSH-001", "TEXT", "SOP-CSH-001.txt", "Daily Cash Register Closing & Reconciliation SOP"),
                evaluateDocument("POL-RET-001", "PDF", "POL-RET-001.pdf", "Return and Core Exchange Policy"),
                evaluateDocument("FAQ-GST-001", "MARKDOWN", "FAQ-GST-001.md", "ERP Billing and GST Clarification FAQ")
        );

        System.out.println("================================================================================");
        System.out.println("STAGE 3C PHASE 2: EMPIRICAL CHUNKING BENCHMARK REPORT");
        System.out.println("Configuration: Target Tokens = 500, Overlap Tokens = 50");
        System.out.println("================================================================================");

        int totalChunks = 0;
        int totalUndersized = 0;
        int totalOversized = 0;
        int totalPunctuationTerminated = 0;

        List<ChunkMetrics> allMetrics = new ArrayList<>();

        for (DocumentEvaluation eval : evaluations) {
            System.out.println("\n--------------------------------------------------------------------------------");
            System.out.println("DOCUMENT: " + eval.docCode() + " [" + eval.format() + "] - " + eval.docTitle());
            System.out.println("Raw Stats: Chars=" + eval.rawCharCount() + ", Words=" + eval.rawWordCount() + ", EstTokens=" + eval.estimatedRawTokens());
            System.out.println("Chunks Produced: " + eval.chunks().size());
            System.out.println("--------------------------------------------------------------------------------");

            IntSummaryStatistics tokenStats = eval.metrics().stream().mapToInt(ChunkMetrics::estimatedTokens).summaryStatistics();
            IntSummaryStatistics wordStats = eval.metrics().stream().mapToInt(ChunkMetrics::wordCount).summaryStatistics();
            IntSummaryStatistics charStats = eval.metrics().stream().mapToInt(ChunkMetrics::charCount).summaryStatistics();
            DoubleSummaryStatistics cptStats = eval.metrics().stream().mapToDouble(ChunkMetrics::charsPerToken).summaryStatistics();

            System.out.printf("Tokens  -> Min: %d, Max: %d, Avg: %.1f, Sum: %d%n",
                    tokenStats.getMin(), tokenStats.getMax(), tokenStats.getAverage(), tokenStats.getSum());
            System.out.printf("Words   -> Min: %d, Max: %d, Avg: %.1f, Sum: %d%n",
                    wordStats.getMin(), wordStats.getMax(), wordStats.getAverage(), wordStats.getSum());
            System.out.printf("Chars   -> Min: %d, Max: %d, Avg: %.1f, Sum: %d%n",
                    charStats.getMin(), charStats.getMax(), charStats.getAverage(), charStats.getSum());
            System.out.printf("Chars/EstimatedToken Heuristic Ratio: Avg=%.2f, Min=%.2f, Max=%.2f%n",
                    cptStats.getAverage(), cptStats.getMin(), cptStats.getMax());

            System.out.println("\nChunk Detail Table:");
            System.out.printf("%-5s | %-6s | %-6s | %-6s | %-10s | %-10s | %s%n",
                    "Idx", "Tokens", "Words", "Chars", "Undersized", "EndsPunct", "Section Title");
            System.out.println("------+--------+--------+--------+------------+------------+-----------------------------------");

            for (ChunkMetrics m : eval.metrics()) {
                totalChunks++;
                allMetrics.add(m);
                if (m.isUndersized()) totalUndersized++;
                if (m.isOversized()) totalOversized++;
                if (m.endsWithTerminalPunctuation()) totalPunctuationTerminated++;

                System.out.printf("%-5d | %-6d | %-6d | %-6d | %-10s | %-10s | %s%n",
                        m.chunkIndex(),
                        m.estimatedTokens(),
                        m.wordCount(),
                        m.charCount(),
                        m.isUndersized() ? "YES (<100)" : "NO",
                        m.endsWithTerminalPunctuation() ? "YES" : "NO",
                        m.sectionTitle()
                );
            }
        }

        // 1. Sum ChunkMetrics.charCount() across all 33 chunks
        int totalChunkCharacters = allMetrics.stream().mapToInt(ChunkMetrics::charCount).sum();

        // 2. Sum ChunkMetrics.estimatedTokens() across all 33 chunks
        int totalEstimatedChunkTokens = allMetrics.stream().mapToInt(ChunkMetrics::estimatedTokens).sum();

        // 3. Calculate aggregate characters-per-estimated-token ratio using totalChunkCharacters / totalEstimatedChunkTokens
        double aggregateCharsPerEstimatedToken = (double) totalChunkCharacters / totalEstimatedChunkTokens;

        // True weighted overall average across all 33 chunks
        IntSummaryStatistics overallTokenStats = allMetrics.stream().mapToInt(ChunkMetrics::estimatedTokens).summaryStatistics();
        double overallWeightedAvgTokens = overallTokenStats.getAverage();

        System.out.println("\n================================================================================");
        System.out.println("OVERALL BENCHMARK SUMMARY (4 CORPUS FIXTURES)");
        System.out.println("================================================================================");
        System.out.println("Total Documents: " + evaluations.size());
        System.out.println("Total Chunks Produced: " + totalChunks);
        System.out.printf("Overall Weighted Token Average: %.1f tokens/chunk (Sum=%d / %d chunks)%n",
                overallWeightedAvgTokens, overallTokenStats.getSum(), totalChunks);
        System.out.printf("Overall Aggregate Chars/Estimated-Token Ratio: %.2f (Chars=%d / Tokens=%d)%n",
                aggregateCharsPerEstimatedToken, totalChunkCharacters, totalEstimatedChunkTokens);
        System.out.println("Undersized Chunks (<100 tokens): " + totalUndersized + " (" + (totalUndersized * 100.0 / totalChunks) + "%)");
        System.out.println("Oversized Chunks (>500 tokens): " + totalOversized + " (" + (totalOversized * 100.0 / totalChunks) + "%)");
        System.out.println("Heuristic Punctuation Termination: " + totalPunctuationTerminated + "/" + totalChunks + " (" + (totalPunctuationTerminated * 100.0 / totalChunks) + "%)");
        System.out.println("================================================================================");

        // Assertions verifying baseline metrics, weighted average, and aggregate ratio calculations
        assertEquals(33, totalChunks, "Total corpus chunk count must be exactly 33");
        assertEquals(6667, overallTokenStats.getSum(), "Total sum of estimated chunk tokens across corpus must be 6,667");
        assertEquals(6667, totalEstimatedChunkTokens, "Total sum of estimated chunk tokens across corpus must be 6,667");
        assertEquals(26611, totalChunkCharacters, "Total sum of chunk characters across corpus must be 26,611");
        assertEquals(202.0, overallWeightedAvgTokens, 0.5,
                "Overall weighted average chunk size must be ~202.0 tokens (not unweighted 258.2)");
        assertEquals(26611.0 / 6667.0, aggregateCharsPerEstimatedToken, 0.001,
                "Overall aggregate chars-per-estimated-token ratio must match 26,611 / 6,667 (~3.9915)");
        assertEquals(3.99, aggregateCharsPerEstimatedToken, 0.01,
                "Overall aggregate chars-per-estimated-token ratio must be ~3.99 (heuristic ratio, not tokenizer accuracy)");
        assertEquals(0, totalOversized, "Existing chunker must not produce oversized chunks (>500 tokens)");
        assertEquals(8, totalUndersized, "Exactly 8 chunks must be undersized (<100 tokens)");
        assertEquals(27, totalPunctuationTerminated, "Exactly 27 chunks must terminate with punctuation");
    }

    @Test
    @DisplayName("Verify heading hierarchy depth and inheritance patterns across formats")
    void shouldAnalyzeHeadingInheritanceAcrossFormats() throws IOException {
        DocumentEvaluation warEval = evaluateDocument("POL-WAR-001", "MARKDOWN", "POL-WAR-001.md", "Auto Parts Warranty Policy");
        DocumentEvaluation sopEval = evaluateDocument("SOP-CSH-001", "TEXT", "SOP-CSH-001.txt", "Daily Cash Register Closing & Reconciliation SOP");
        DocumentEvaluation retEval = evaluateDocument("POL-RET-001", "PDF", "POL-RET-001.pdf", "Return and Core Exchange Policy");
        DocumentEvaluation gstEval = evaluateDocument("FAQ-GST-001", "MARKDOWN", "FAQ-GST-001.md", "ERP Billing and GST Clarification FAQ");

        // 1. Markdown Warranty Policy: inherits 3 levels (# > ## > ###)
        List<String> warSections = warEval.chunks().stream().map(DocumentChunker.ChunkDraft::sectionTitle).toList();
        assertTrue(warSections.stream().anyMatch(s -> s.split(" > ").length == 3),
                "Markdown warranty policy must produce 3-level section paths (# > ## > ###)");

        // 2. Markdown GST FAQ: inherits 2 levels (# > ##)
        List<String> gstSections = gstEval.chunks().stream().map(DocumentChunker.ChunkDraft::sectionTitle).toList();
        assertTrue(gstSections.stream().anyMatch(s -> s.split(" > ").length == 2),
                "Markdown GST FAQ must produce 2-level section paths (# > ##)");

        // 3. Plain Text SOP: lacks Markdown headers, retains fallback default title
        List<String> sopSections = sopEval.chunks().stream().map(DocumentChunker.ChunkDraft::sectionTitle).toList();
        assertTrue(sopSections.stream().allMatch(s -> s.equals("Daily Cash Register Closing & Reconciliation SOP")),
                "Plain text SOP without # headers must retain default document title");

        // 4. PDF Return Policy: extracted text lacks # headers, retains fallback default title
        List<String> retSections = retEval.chunks().stream().map(DocumentChunker.ChunkDraft::sectionTitle).toList();
        assertTrue(retSections.stream().allMatch(s -> s.equals("Return and Core Exchange Policy")),
                "PDF extracted text without # headers must retain default document title");
    }

    @Test
    @DisplayName("Empirically measure consecutive chunk overlap across continuous text and Markdown headers")
    void shouldMeasureConsecutiveChunkOverlap() throws IOException {
        // 1. Plain Text SOP (continuous text): 6 chunks -> 5 transitions
        DocumentEvaluation sopEval = evaluateDocument("SOP-CSH-001", "TEXT", "SOP-CSH-001.txt", "Daily Cash Register Closing & Reconciliation SOP");
        List<DocumentChunker.ChunkDraft> sopChunks = sopEval.chunks();
        assertEquals(6, sopChunks.size());

        for (int i = 0; i < sopChunks.size() - 1; i++) {
            OverlapMeasurement m = measureChunkOverlap(sopChunks.get(i).text(), sopChunks.get(i + 1).text(), i, i + 1);
            assertEquals(37, m.overlappingWords(), "Continuous text transition " + i + "->" + (i + 1) + " must overlap by exactly 37 words (50 * 0.75)");
            assertTrue(m.estimatedOverlapTokens() >= 50 && m.estimatedOverlapTokens() <= 110,
                    "Estimated overlap tokens reflects word and character heuristic (found " + m.estimatedOverlapTokens() + ")");
            assertFalse(m.overlapSnippet().isBlank(), "Overlap snippet must not be blank");
        }

        // 2. PDF Return Policy (continuous text): 3 chunks -> 2 transitions
        DocumentEvaluation retEval = evaluateDocument("POL-RET-001", "PDF", "POL-RET-001.pdf", "Return and Core Exchange Policy");
        List<DocumentChunker.ChunkDraft> retChunks = retEval.chunks();
        assertEquals(3, retChunks.size());

        for (int i = 0; i < retChunks.size() - 1; i++) {
            OverlapMeasurement m = measureChunkOverlap(retChunks.get(i).text(), retChunks.get(i + 1).text(), i, i + 1);
            System.out.printf("RET transition %d->%d: words=%d, estTokens=%d%n", i, i + 1, m.overlappingWords(), m.estimatedOverlapTokens());
            assertEquals(37, m.overlappingWords(), "PDF continuous text transition " + i + "->" + (i + 1) + " must overlap by exactly 37 words");
            assertTrue(m.estimatedOverlapTokens() >= 45 && m.estimatedOverlapTokens() <= 75,
                    "Estimated overlap tokens reflects word and character heuristic (found " + m.estimatedOverlapTokens() + ")");
        }

        // 3. Markdown Warranty Policy (header transitions): 10 chunks -> 9 transitions
        DocumentEvaluation warEval = evaluateDocument("POL-WAR-001", "MARKDOWN", "POL-WAR-001.md", "Auto Parts Warranty Policy");
        List<DocumentChunker.ChunkDraft> warChunks = warEval.chunks();
        assertEquals(10, warChunks.size());

        for (int i = 0; i < warChunks.size() - 1; i++) {
            OverlapMeasurement m = measureChunkOverlap(warChunks.get(i).text(), warChunks.get(i + 1).text(), i, i + 1);
            assertEquals(0, m.overlappingWords(), "Markdown header transition " + i + "->" + (i + 1) + " must have zero overlap");
            assertEquals(0, m.estimatedOverlapTokens());
        }

        // 4. Markdown GST FAQ (header transitions): 14 chunks -> 13 transitions
        DocumentEvaluation gstEval = evaluateDocument("FAQ-GST-001", "MARKDOWN", "FAQ-GST-001.md", "ERP Billing and GST Clarification FAQ");
        List<DocumentChunker.ChunkDraft> gstChunks = gstEval.chunks();
        assertEquals(14, gstChunks.size());

        for (int i = 0; i < gstChunks.size() - 1; i++) {
            OverlapMeasurement m = measureChunkOverlap(gstChunks.get(i).text(), gstChunks.get(i + 1).text(), i, i + 1);
            assertEquals(0, m.overlappingWords(), "Markdown FAQ header transition " + i + "->" + (i + 1) + " must have zero overlap");
            assertEquals(0, m.estimatedOverlapTokens());
        }
    }

    @Test
    @DisplayName("Empirically verify paragraph preservation across Markdown/Text and sentence fallback in PDF")
    void shouldMeasureParagraphPreservationAgainstSourceDocument() throws IOException {
        List<DocumentEvaluation> textEvaluations = List.of(
                evaluateDocument("POL-WAR-001", "MARKDOWN", "POL-WAR-001.md", "Auto Parts Warranty Policy"),
                evaluateDocument("SOP-CSH-001", "TEXT", "SOP-CSH-001.txt", "Daily Cash Register Closing & Reconciliation SOP"),
                evaluateDocument("FAQ-GST-001", "MARKDOWN", "FAQ-GST-001.md", "ERP Billing and GST Clarification FAQ")
        );

        int totalTextSourceParagraphs = 0;
        int fullyPreservedTextParagraphs = 0;
        int splitTextParagraphs = 0;

        for (DocumentEvaluation eval : textEvaluations) {
            byte[] bytes = loadFixtureBytes(eval.docCode() + ("TEXT".equals(eval.format()) ? ".txt" : ".md"));
            String rawText = textParser.extractText(bytes);
            String normalized = rawText.replace("\r\n", "\n").replace("\r", "\n").trim();
            String[] rawParagraphs = normalized.split("\n\\s*\n");

            int docSourceParas = 0;
            int docPreserved = 0;
            int docSplit = 0;

            for (String p : rawParagraphs) {
                String trimmedP = p.trim();
                if (trimmedP.isEmpty()) {
                    continue;
                }
                docSourceParas++;
                totalTextSourceParagraphs++;

                // Verify whether this exact source paragraph appears intact within at least one chunk
                boolean preservedIntact = false;
                for (DocumentChunker.ChunkDraft chunk : eval.chunks()) {
                    if (chunk.text().contains(trimmedP)) {
                        preservedIntact = true;
                        break;
                    }
                }

                if (preservedIntact) {
                    docPreserved++;
                    fullyPreservedTextParagraphs++;
                } else {
                    docSplit++;
                    splitTextParagraphs++;
                }
            }

            assertTrue(docPreserved > 0, "Document " + eval.docCode() + " must have preserved paragraphs");
            assertEquals(0, docSplit, "Document " + eval.docCode() + " must have zero split paragraphs when paragraphs are < 500 tokens");
            assertEquals(docSourceParas, docPreserved);
        }

        assertEquals(52, totalTextSourceParagraphs, "Markdown and Text documents must have exactly 52 source paragraphs");
        assertEquals(52, fullyPreservedTextParagraphs, "All 52 Markdown and Text source paragraphs must be 100% preserved intact within chunks");
        assertEquals(0, splitTextParagraphs, "Zero Markdown or Text paragraphs split across chunks");

        // PDF Evaluation: PDFBox extracts continuous text without double-newlines, creating a single 967-token block
        DocumentEvaluation pdfEval = evaluateDocument("POL-RET-001", "PDF", "POL-RET-001.pdf", "Return and Core Exchange Policy");
        byte[] pdfBytes = loadFixtureBytes("POL-RET-001.pdf");
        String pdfText = pdfParser.extractText(pdfBytes);
        String normalizedPdf = pdfText.replace("\r\n", "\n").replace("\r", "\n").trim();
        String[] pdfParagraphs = normalizedPdf.split("\n\\s*\n");

        assertEquals(1, pdfParagraphs.length, "PDF extracted text lacks double newlines, resulting in 1 continuous block");
        assertTrue(DocumentChunker.estimateTokens(pdfParagraphs[0]) > 500, "Continuous PDF block exceeds 500 tokens (triggers sentence splitting)");
        assertEquals(3, pdfEval.chunks().size(), "Oversized continuous PDF block is split into 3 chunks by sentence boundaries");
    }

    @Test
    @DisplayName("Classify non-punctuation terminating chunks into orphan headers vs table/list endings")
    void shouldClassifyHeuristicPunctuationTerminatingChunks() throws IOException {
        List<DocumentEvaluation> evaluations = List.of(
                evaluateDocument("POL-WAR-001", "MARKDOWN", "POL-WAR-001.md", "Auto Parts Warranty Policy"),
                evaluateDocument("SOP-CSH-001", "TEXT", "SOP-CSH-001.txt", "Daily Cash Register Closing & Reconciliation SOP"),
                evaluateDocument("POL-RET-001", "PDF", "POL-RET-001.pdf", "Return and Core Exchange Policy"),
                evaluateDocument("FAQ-GST-001", "MARKDOWN", "FAQ-GST-001.md", "ERP Billing and GST Clarification FAQ")
        );

        List<ChunkMetrics> nonPunctuationChunks = new ArrayList<>();
        int orphanHeaderCount = 0;
        int listOrTableEndCount = 0;

        for (DocumentEvaluation eval : evaluations) {
            for (ChunkMetrics m : eval.metrics()) {
                if (!m.endsWithTerminalPunctuation()) {
                    nonPunctuationChunks.add(m);
                    if (m.isOrphanHeader()) {
                        orphanHeaderCount++;
                    } else if (m.isListOrTableEnd()) {
                        listOrTableEndCount++;
                    }
                }
            }
        }

        assertEquals(6, nonPunctuationChunks.size(), "Exactly 6 chunks across corpus do not end with terminal punctuation");
        assertEquals(5, orphanHeaderCount, "Exactly 5 non-punctuation chunks must be orphan Markdown headers");
        assertEquals(1, listOrTableEndCount, "Exactly 1 non-punctuation chunk must be a tabulated cash-count list line");
    }
}
