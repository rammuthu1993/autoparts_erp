package com.autoparts.erp.ai.rag.citation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.autoparts.erp.ai.rag.model.RetrievedChunk;

/**
 * Validates that citations generated in AI responses strictly reference genuinely retrieved chunks.
 *
 * <p>Enforces zero-trust citation verification:
 * <ul>
 *   <li>Exact document code or exact normalized document title match (no substring matching).</li>
 *   <li>Exact document version match.</li>
 *   <li>Exact section title match against retrieved chunk metadata when present.</li>
 *   <li>Rejection of malformed citations, missing sections, and fabricated source references.</li>
 * </ul>
 * </p>
 */
@Component
public class CitationValidator {

    // Matches complete bracketed citation or incomplete citation prefix up to line/sentence end
    private static final Pattern ANY_SOURCE_TAG = Pattern.compile(
            "\\[Source:[^\\]\\[\\r\\n]*\\]|\\[Source:[^\\]\\[\\r\\n]*?(?=[.!?](?:\\s+[A-Z]|\\s*$)|\\r|\\n|\\[|$)",
            Pattern.CASE_INSENSITIVE
    );

    // Matches strict well-formed citation: [Source: <Title/Code> v<Version>, §<Section>] or [Source: <Title/Code> v<Version>]
    private static final Pattern STRICT_CITATION_PATTERN = Pattern.compile(
            "^\\[Source:\\s*([^,\\]]+?)\\s+v(\\d+)(?:,\\s*(?:§\\s*)?([^\\]]+))?\\]$",
            Pattern.CASE_INSENSITIVE
    );

    public record CitationValidationResult(
            boolean valid,
            List<String> matchedCitations,
            List<String> fabricatedCitations,
            String normalizedResponse
    ) {}

    /**
     * Validates citations in response against retrieved chunks.
     *
     * @param response model response text
     * @param retrievedChunks chunks that were provided to the model
     * @return validation result containing verified and fabricated citation lists
     */
    public CitationValidationResult validateCitations(String response, List<RetrievedChunk> retrievedChunks) {
        if (!StringUtils.hasText(response)) {
            return new CitationValidationResult(true, List.of(), List.of(), "");
        }

        Matcher tagMatcher = ANY_SOURCE_TAG.matcher(response);
        List<String> matched = new ArrayList<>();
        List<String> fabricated = new ArrayList<>();

        while (tagMatcher.find()) {
            String fullCitation = tagMatcher.group(0).trim();
            if (fullCitation.isEmpty()) {
                continue;
            }

            if (retrievedChunks == null || retrievedChunks.isEmpty()) {
                fabricated.add(fullCitation);
                continue;
            }

            // Incomplete tags without closing bracket are immediately invalid
            if (!fullCitation.endsWith("]")) {
                fabricated.add(fullCitation);
                continue;
            }

            Matcher strictMatcher = STRICT_CITATION_PATTERN.matcher(fullCitation);
            if (!strictMatcher.matches()) {
                // Malformed citation (missing version, bad delimiter, etc.)
                fabricated.add(fullCitation);
                continue;
            }

            String titleOrCode = normalizeText(strictMatcher.group(1));
            int citedVersion;
            try {
                citedVersion = Integer.parseInt(strictMatcher.group(2).trim());
            } catch (NumberFormatException e) {
                fabricated.add(fullCitation);
                continue;
            }
            String citedSection = strictMatcher.group(3) != null
                    ? normalizeSection(strictMatcher.group(3))
                    : "";

            boolean matchesAnyChunk = retrievedChunks.stream().anyMatch(chunk -> {
                String docTitle = normalizeText(chunk.documentTitle());
                String docCode = normalizeText(chunk.documentCode());

                // 1. Exact document identity: must match documentCode or exact documentTitle (no substring!)
                boolean identityMatch = docCode.equalsIgnoreCase(titleOrCode)
                        || docTitle.equalsIgnoreCase(titleOrCode);
                if (!identityMatch) {
                    return false;
                }

                // 2. Exact version match
                if (chunk.version() != citedVersion) {
                    return false;
                }

                // 3. Section validation
                String chunkSection = normalizeSection(chunk.sectionTitle());
                if (!citedSection.isEmpty()) {
                    // Cited specific section -> chunk must have exact matching section
                    return chunkSection.equalsIgnoreCase(citedSection);
                } else {
                    // Citation omitted section -> only valid if chunk has no specific section (empty or "General")
                    return chunkSection.isEmpty() || chunkSection.equalsIgnoreCase("general")
                            || chunkSection.equalsIgnoreCase("overview");
                }
            });

            if (matchesAnyChunk) {
                matched.add(fullCitation);
            } else {
                fabricated.add(fullCitation);
            }
        }

        boolean isValid = fabricated.isEmpty();
        String normalized = response;
        if (!isValid) {
            for (String fab : fabricated) {
                normalized = normalized.replace(fab, "");
            }
            normalized = normalized.replaceAll("\\s+([.,;:!?])", "$1");
            normalized = normalized.replaceAll("([.!?])\\s*\\1+", "$1");
            normalized = normalized.replaceAll("[ \\t]+", " ").trim();
        }
        return new CitationValidationResult(isValid, matched, fabricated, normalized);
    }

    /**
     * Sanitizes untrusted document text before injecting into prompt delimiters.
     * Neutralizes fake closing/opening XML tags and strips control sequences.
     */
    public static String sanitizeKnowledgeContent(String text) {
        if (text == null) {
            return "";
        }

        // Strip non-printable ASCII control characters except \t and \n
        String stripped = text.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");

        // Escape XML tags to prevent delimiter escape
        return stripped
                .replace("</KNOWLEDGE_CONTEXT>", "&lt;/KNOWLEDGE_CONTEXT&gt;")
                .replace("<KNOWLEDGE_CONTEXT>", "&lt;KNOWLEDGE_CONTEXT&gt;")
                .replace("</CONTEXT>", "&lt;/CONTEXT&gt;")
                .replace("<CONTEXT>", "&lt;<CONTEXT&gt;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static String normalizeText(String input) {
        if (input == null) {
            return "";
        }
        return input.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeSection(String section) {
        if (section == null) {
            return "";
        }
        // Strip leading section symbol '§', whitespace, and punctuation
        String cleaned = section.trim();
        while (cleaned.startsWith("§") || cleaned.startsWith("#")) {
            cleaned = cleaned.substring(1).trim();
        }
        return cleaned.toLowerCase(Locale.ROOT);
    }
}
