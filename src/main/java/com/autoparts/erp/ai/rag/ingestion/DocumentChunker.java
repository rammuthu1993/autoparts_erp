package com.autoparts.erp.ai.rag.ingestion;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.autoparts.erp.ai.rag.model.KnowledgeChunk;

/**
 * Paragraph- and sentence-boundary aware document chunker.
 *
 * <p>Splits extracted document text into configurable token-budgeted chunks with overlap,
 * preserving Markdown heading context and guaranteeing words/sentences are not abruptly truncated.</p>
 */
public class DocumentChunker {

    private static final Pattern MARKDOWN_HEADER = Pattern.compile("^(#{1,6})\\s+(.+)$", Pattern.MULTILINE);
    private static final Pattern SENTENCE_SPLIT = Pattern.compile("(?<=[.!?])\\s+");

    private final int targetChunkTokens;
    private final int overlapTokens;

    public DocumentChunker() {
        this(500, 50);
    }

    public DocumentChunker(int targetChunkTokens, int overlapTokens) {
        if (targetChunkTokens <= 50) {
            throw new IllegalArgumentException("targetChunkTokens must be greater than 50");
        }
        if (overlapTokens < 0 || overlapTokens >= targetChunkTokens) {
            throw new IllegalArgumentException("overlapTokens must be >= 0 and < targetChunkTokens");
        }
        this.targetChunkTokens = targetChunkTokens;
        this.overlapTokens = overlapTokens;
    }

    public record ChunkDraft(
            int chunkIndex,
            String sectionTitle,
            String text,
            int estimatedTokens,
            String sha256Hex
    ) {}

    /**
     * Chunks extracted document text into structured drafts.
     *
     * @param fullText extracted text content
     * @param defaultSectionTitle fallback section title (e.g. document title or "Overview")
     * @return list of chunk drafts
     */
    public List<ChunkDraft> chunk(String fullText, String defaultSectionTitle) {
        if (fullText == null || fullText.isBlank()) {
            return List.of();
        }

        String normalized = fullText.replace("\r\n", "\n").replace("\r", "\n").trim();
        List<String> paragraphs = splitIntoParagraphs(normalized);
        List<ChunkDraft> result = new ArrayList<>();

        String currentSection = (defaultSectionTitle != null && !defaultSectionTitle.isBlank())
                ? defaultSectionTitle.trim()
                : "General";

        java.util.TreeMap<Integer, String> headingLevels = new java.util.TreeMap<>();

        StringBuilder currentChunk = new StringBuilder();
        int currentChunkTokens = 0;
        int chunkIndex = 0;

        for (String paragraph : paragraphs) {
            String trimmedPara = paragraph.trim();
            if (trimmedPara.isEmpty()) {
                continue;
            }

            // Check if paragraph is or starts with a Markdown header
            Matcher headerMatcher = MARKDOWN_HEADER.matcher(trimmedPara);
            if (headerMatcher.find()) {
                int level = headerMatcher.group(1).length();
                String headingTitle = headerMatcher.group(2).trim();

                // If current chunk has content, flush it when a new header section begins
                if (currentChunk.length() > 0) {
                    result.add(createDraft(chunkIndex++, currentSection, currentChunk.toString().trim()));
                    currentChunk = new StringBuilder();
                    currentChunkTokens = 0;
                }

                // Update heading hierarchy: remove any levels >= current level and append current
                headingLevels.entrySet().removeIf(e -> e.getKey() >= level);
                headingLevels.put(level, headingTitle);
                currentSection = String.join(" > ", headingLevels.values());
            }

            int paraTokens = estimateTokens(trimmedPara);

            // If a single paragraph exceeds the target chunk size, split it by sentence
            if (paraTokens > targetChunkTokens) {
                List<String> sentences = splitIntoSentences(trimmedPara);
                for (String sentence : sentences) {
                    int sentTokens = estimateTokens(sentence);
                    if (currentChunkTokens + sentTokens > targetChunkTokens && currentChunk.length() > 0) {
                        result.add(createDraft(chunkIndex++, currentSection, currentChunk.toString().trim()));
                        // Seed next chunk with overlap text from end of current chunk
                        String overlapText = extractOverlapTail(currentChunk.toString(), overlapTokens);
                        currentChunk = new StringBuilder(overlapText);
                        if (!overlapText.isEmpty()) {
                            currentChunk.append("\n\n");
                        }
                        currentChunkTokens = estimateTokens(currentChunk.toString());
                    }
                    if (currentChunk.length() > 0 && !currentChunk.toString().endsWith("\n\n")) {
                        currentChunk.append(" ");
                    }
                    currentChunk.append(sentence);
                    currentChunkTokens += sentTokens;
                }
            } else {
                if (currentChunkTokens + paraTokens > targetChunkTokens && currentChunk.length() > 0) {
                    result.add(createDraft(chunkIndex++, currentSection, currentChunk.toString().trim()));
                    String overlapText = extractOverlapTail(currentChunk.toString(), overlapTokens);
                    currentChunk = new StringBuilder(overlapText);
                    if (!overlapText.isEmpty()) {
                        currentChunk.append("\n\n");
                    }
                    currentChunkTokens = estimateTokens(currentChunk.toString());
                }
                if (currentChunk.length() > 0 && !currentChunk.toString().endsWith("\n\n")) {
                    currentChunk.append("\n\n");
                }
                currentChunk.append(trimmedPara);
                currentChunkTokens += paraTokens;
            }
        }

        if (currentChunk.length() > 0 && !currentChunk.toString().isBlank()) {
            result.add(createDraft(chunkIndex++, currentSection, currentChunk.toString().trim()));
        }

        return result;
    }

    /**
     * Coarse token estimation heuristic: ~1.33 tokens per whitespace-delimited word,
     * or characters / 4 for dense non-spaced text. Labeled explicitly as an approximate count.
     */
    public static int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        String[] words = text.trim().split("\\s+");
        int wordBased = (int) Math.ceil(words.length * 1.33);
        int charBased = (int) Math.ceil(text.length() / 4.0);
        return Math.max(1, Math.max(wordBased, charBased));
    }

    private ChunkDraft createDraft(int index, String section, String text) {
        return new ChunkDraft(
                index,
                section,
                text,
                estimateTokens(text),
                computeSha256(text)
        );
    }

    private List<String> splitIntoParagraphs(String text) {
        String[] split = text.split("\n\\s*\n");
        List<String> list = new ArrayList<>();
        for (String s : split) {
            if (!s.isBlank()) {
                list.add(s.trim());
            }
        }
        return list;
    }

    private List<String> splitIntoSentences(String text) {
        String[] split = SENTENCE_SPLIT.split(text);
        List<String> list = new ArrayList<>();
        for (String s : split) {
            if (!s.isBlank()) {
                list.add(s.trim());
            }
        }
        return list;
    }

    private String extractOverlapTail(String text, int targetOverlapTokens) {
        if (targetOverlapTokens <= 0 || text.isBlank()) {
            return "";
        }
        String[] words = text.trim().split("\\s+");
        int wordsToTake = Math.min(words.length, (int) (targetOverlapTokens * 0.75));
        if (wordsToTake <= 0) {
            return "";
        }
        int startIndex = words.length - wordsToTake;
        StringBuilder sb = new StringBuilder();
        for (int i = startIndex; i < words.length; i++) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append(words[i]);
        }
        return sb.toString();
    }

    public static String computeSha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }

    public static String computeSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }
}
