package com.autoparts.erp.ai.rag.citation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.autoparts.erp.ai.rag.model.RetrievedChunk;

/**
 * Validates model citations against the exact authorized chunks supplied to the current request.
 * Invalid or uncited factual sentences are removed rather than leaving unsupported prose behind.
 */
@Component
public class CitationValidator {

    private static final Pattern SOURCE_TAG = Pattern.compile(
            "\\[Source:[^\\]\\r\\n]*(?:\\]|$)", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final Pattern STRICT_CITATION = Pattern.compile(
            "^\\[Source:\\s*([^,\\]]+?)\\s+v(\\d+)(?:,\\s*(?:§\\s*)?([^\\]]+))?\\]$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SENTENCE_BOUNDARY = Pattern.compile(
            "(?<=[!?])\\s+|(?<=\\.)\\s+(?=[A-Z0-9])|(?<=\\])\\s+(?=[A-Z0-9])|\\R+");

    public record CitationValidationResult(
            boolean valid,
            List<String> matchedCitations,
            List<String> fabricatedCitations,
            String normalizedResponse,
            List<String> unsupportedStatements
    ) {
        public CitationValidationResult(boolean valid, List<String> matchedCitations,
                List<String> fabricatedCitations, String normalizedResponse) {
            this(valid, matchedCitations, fabricatedCitations, normalizedResponse, List.of());
        }
    }

    /**
     * Validates every response sentence against citations from this request's retrieved chunks.
     * A sentence must contain at least one valid source tag, unless it is an explicit abstention.
     */
    public CitationValidationResult validateCitations(String response, List<RetrievedChunk> retrievedChunks) {
        if (!StringUtils.hasText(response)) {
            return new CitationValidationResult(true, List.of(), List.of(), "", List.of());
        }

        List<RetrievedChunk> allowedChunks = retrievedChunks == null ? List.of() : List.copyOf(retrievedChunks);
        Set<String> matched = new LinkedHashSet<>();
        Set<String> fabricated = new LinkedHashSet<>();
        List<String> unsupported = new ArrayList<>();
        List<String> accepted = new ArrayList<>();

        for (String rawSegment : SENTENCE_BOUNDARY.split(response.trim())) {
            String segment = rawSegment.trim();
            if (segment.isEmpty()) {
                continue;
            }
            if (isExplicitAbstention(segment)) {
                accepted.add(segment);
                continue;
            }

            List<String> tags = findSourceTags(segment);
            if (tags.isEmpty()) {
                unsupported.add(segment);
                continue;
            }

            boolean segmentValid = true;
            Set<String> segmentMatched = new LinkedHashSet<>();
            for (String tag : tags) {
                if (isValidCitation(tag, allowedChunks)) {
                    segmentMatched.add(tag);
                } else {
                    fabricated.add(tag);
                    segmentValid = false;
                }
            }

            // A claim with one invalid source is rejected as a whole, even if another source is valid.
            if (segmentValid && !segmentMatched.isEmpty()) {
                matched.addAll(segmentMatched);
                accepted.add(removeDuplicateTags(segment, segmentMatched));
            } else {
                unsupported.add(segment);
            }
        }

        String normalized = String.join(" ", accepted).replaceAll("\\s+", " ").trim();
        if (normalized.isBlank()) {
            normalized = "I do not have sufficient authorized documentation to answer this question.";
        }

        boolean valid = fabricated.isEmpty() && unsupported.isEmpty();
        return new CitationValidationResult(valid, List.copyOf(matched), List.copyOf(fabricated),
                normalized, List.copyOf(unsupported));
    }

    private static List<String> findSourceTags(String text) {
        List<String> tags = new ArrayList<>();
        Matcher matcher = SOURCE_TAG.matcher(text);
        while (matcher.find()) {
            tags.add(matcher.group().trim());
        }
        return tags;
    }

    private static boolean isValidCitation(String citation, List<RetrievedChunk> chunks) {
        if (!citation.endsWith("]")) {
            return false;
        }
        Matcher matcher = STRICT_CITATION.matcher(citation);
        if (!matcher.matches()) {
            return false;
        }

        String identity = normalize(matcher.group(1));
        int version;
        try {
            version = Integer.parseInt(matcher.group(2));
        } catch (NumberFormatException exception) {
            return false;
        }
        String section = normalizeSection(matcher.group(3));

        return chunks.stream().anyMatch(chunk -> {
            boolean identityMatches = identity.equals(normalize(chunk.documentCode()))
                    || identity.equals(normalize(chunk.documentTitle()));
            if (!identityMatches || chunk.version() != version) {
                return false;
            }
            String retrievedSection = normalizeSection(chunk.sectionTitle());
            if (!section.isBlank()) {
                return section.equals(retrievedSection);
            }
            return retrievedSection.isBlank() || retrievedSection.equals("general")
                    || retrievedSection.equals("overview");
        });
    }

    private static String removeDuplicateTags(String sentence, Set<String> retainedTags) {
        Set<String> seen = new LinkedHashSet<>();
        Matcher matcher = SOURCE_TAG.matcher(sentence);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String tag = matcher.group().trim();
            if (retainedTags.contains(tag) && seen.add(tag)) {
                matcher.appendReplacement(result, Matcher.quoteReplacement(tag));
            } else {
                matcher.appendReplacement(result, "");
            }
        }
        matcher.appendTail(result);
        return result.toString().replaceAll("\\s+([.,;:!?])", "$1").replaceAll("\\s{2,}", " ").trim();
    }

    private static boolean isExplicitAbstention(String segment) {
        String normalized = normalize(segment);
        return normalized.startsWith("i do not have sufficient authorized documentation")
                || normalized.startsWith("i do not have documentation covering")
                || normalized.startsWith("i cannot determine from the available documentation")
                || normalized.startsWith("the available documentation does not specify")
                || normalized.startsWith("the retrieved documents do not contain");
    }

    private static String normalize(String input) {
        return input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeSection(String section) {
        if (section == null) {
            return "";
        }
        String cleaned = section.trim();
        while (cleaned.startsWith("§") || cleaned.startsWith("#")) {
            cleaned = cleaned.substring(1).trim();
        }
        return cleaned.toLowerCase(Locale.ROOT);
    }

    /** Sanitizes untrusted document text before inserting it into prompt delimiters. */
    public static String sanitizeKnowledgeContent(String text) {
        if (text == null) {
            return "";
        }
        String stripped = text.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
        return stripped.replace("<", "&lt;").replace(">", "&gt;");
    }
}
