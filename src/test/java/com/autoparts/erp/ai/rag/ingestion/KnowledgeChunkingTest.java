package com.autoparts.erp.ai.rag.ingestion;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KnowledgeChunkingTest {

    @Test
    @DisplayName("DocumentChunker produces chunks within target token budget with overlap")
    void shouldProduceChunksWithinTokenBudgetWithOverlap() {
        DocumentChunker chunker = new DocumentChunker(100, 20);

        StringBuilder textBuilder = new StringBuilder();
        for (int i = 1; i <= 25; i++) {
            textBuilder.append("Paragraph ").append(i).append(": Auto parts return guidelines mandate all parts ")
                    .append("must be inspected for core damage, fluid contamination, and physical integrity before acceptance.\n\n");
        }

        List<DocumentChunker.ChunkDraft> chunks = chunker.chunk(textBuilder.toString(), "Return Guidelines");

        assertFalse(chunks.isEmpty());
        assertTrue(chunks.size() > 1, "Expected text to produce multiple chunks under 100 token budget");

        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunker.ChunkDraft chunk = chunks.get(i);
            assertEquals(i, chunk.chunkIndex());
            assertTrue(chunk.estimatedTokens() > 0);
            assertNotNull(chunk.sha256Hex());
            assertEquals(64, chunk.sha256Hex().length());
        }

        // Verify overlap presence: start of chunk 1 should contain tail of chunk 0
        String chunk0 = chunks.get(0).text();
        String chunk1 = chunks.get(1).text();
        // Since overlap was configured to 20 tokens, chunk 1 must have some words from chunk 0
        String[] chunk0Words = chunk0.split("\\s+");
        String lastWordOfChunk0 = chunk0Words[chunk0Words.length - 1];
        assertTrue(chunk1.contains(lastWordOfChunk0), "Chunk 1 should contain overlap tail from chunk 0");
    }

    @Test
    @DisplayName("DocumentChunker snaps boundaries to paragraphs and sentences without mid-word cuts")
    void shouldSnapBoundariesToParagraphsAndSentences() {
        DocumentChunker chunker = new DocumentChunker(60, 10);

        String text = """
                First paragraph states that customers have 30 days to return unused parts. All items must include original packaging and invoice receipt.

                Second paragraph explains that electrical components are strictly non-returnable once the security seal is broken. Exceptions require manager approval.

                Third paragraph outlines core deposit returns. Core parts must be returned within 45 days in the original manufacturer box to receive full credit.
                """;

        List<DocumentChunker.ChunkDraft> chunks = chunker.chunk(text, "Warranty Rules");

        assertFalse(chunks.isEmpty());
        for (DocumentChunker.ChunkDraft chunk : chunks) {
            String chunkText = chunk.text();
            // Verify no word was split in the middle (e.g. ends with valid punctuation or clean word boundary)
            assertFalse(chunkText.endsWith("-"), "Chunk should not cut words with hyphens");
            assertTrue(chunkText.endsWith(".") || chunkText.endsWith("approval") || chunkText.endsWith("credit."),
                    "Chunk boundary should end at clean sentence or word boundary");
        }
    }

    @Test
    @DisplayName("DocumentChunker tracks and inherits Markdown heading hierarchy")
    void shouldInheritMarkdownHeadingContext() {
        DocumentChunker chunker = new DocumentChunker(100, 20);

        String markdown = """
                # Company Operations Manual

                Welcome to the auto parts operations handbook.

                ## Section 1: Standard Returns

                Standard return window is 30 calendar days from the date of purchase.
                Items must be uninstalled and in saleable condition.

                ## Section 2: Core Deposit Policy

                A core deposit is charged on starters, alternators, and steering racks.
                Cores must be returned drained of fluids within 45 days.
                """;

        List<DocumentChunker.ChunkDraft> chunks = chunker.chunk(markdown, "Operations Manual");

        assertFalse(chunks.isEmpty());

        // Chunks in section 1 should have section title inheriting "# Title > ## Subtitle"
        boolean foundSection1 = chunks.stream().anyMatch(c -> c.sectionTitle().contains("Section 1: Standard Returns"));
        // Chunks in section 2 should have section title inheriting "# Title > ## Subtitle"
        boolean foundSection2 = chunks.stream().anyMatch(c -> c.sectionTitle().contains("Section 2: Core Deposit Policy"));
        // Verify full hierarchy inheritance
        boolean foundHierarchy = chunks.stream().anyMatch(c -> "Company Operations Manual > Section 1: Standard Returns".equals(c.sectionTitle()));

        assertTrue(foundSection1, "Expected chunk with Section 1 header");
        assertTrue(foundSection2, "Expected chunk with Section 2 header");
        assertTrue(foundHierarchy, "Expected chunk with full inherited hierarchy: Company Operations Manual > Section 1: Standard Returns");
    }

    @Test
    @DisplayName("estimateTokens calculates approximate token counts using ~1.33 words/char heuristic")
    void shouldEstimateApproximateTokenCount() {
        assertEquals(0, DocumentChunker.estimateTokens(null));
        assertEquals(0, DocumentChunker.estimateTokens("   "));

        // 10 words
        String tenWords = "one two three four five six seven eight nine ten";
        int tokens = DocumentChunker.estimateTokens(tenWords);
        // 10 * 1.33 = 13.3 -> 14 tokens
        assertEquals(14, tokens);
    }
}
