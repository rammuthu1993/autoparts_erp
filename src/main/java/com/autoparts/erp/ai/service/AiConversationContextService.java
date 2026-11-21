package com.autoparts.erp.ai.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.autoparts.erp.ai.model.AiEntityReference;

/**
 * Resolves entity references, multi-turn follow-ups, and prevents response repetition
 * by tracking validated entities across conversation history.
 */
@Service
public class AiConversationContextService {

    private static final Pattern PRODUCT_IDENTIFIER = Pattern.compile(
            "\\b(?=[A-Z0-9-]*[A-Z])(?=[A-Z0-9-]*\\d)[A-Z0-9]+(?:-[A-Z0-9]+)*\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern PRODUCT_NAME_LINE = Pattern.compile(
            "Product:\\s*([^\\r\\n]+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern PRODUCT_CODE_LINE = Pattern.compile(
            "Product\\s+code:\\s*([A-Z0-9-]+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern PART_NUMBER_LINE = Pattern.compile(
            "Part\\s+number:\\s*([A-Z0-9-]+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern PRONOUN_REFERENCE = Pattern.compile(
            "\\b(?:it|its|this|that|same|those|them)\\b"
                    + "|\\b(?:the|this|that|same)\\s+(?:product|item|part|one)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern ATTRIBUTE_QUERY = Pattern.compile(
            "\\b(?:minimum\\s+stock(?:\\s+level)?|min\\s+stock|available\\s+quantity|available\\s+qty|"
                    + "available\\s+stock|stock|inventory|quantity|part\\s+number|part\\s+no|"
                    + "details?|price|low\\s+in\\s+stock|is\\s+it\\s+low)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern CONTEXT_RESET = Pattern.compile(
            "\\b(?:new\\s+topic|different\\s+product|another\\s+product|switch\\s+to|forget\\s+(?:that|the\\s+previous))\\b",
            Pattern.CASE_INSENSITIVE);

    public record ResolvedContext(
            boolean resolved,
            boolean ambiguous,
            boolean requiresClarification,
            String clarificationMessage,
            AiEntityReference entityReference,
            String resolvedMessage
    ) {
        public static ResolvedContext unchanged(String message) {
            return new ResolvedContext(false, false, false, null, null, message);
        }

        public static ResolvedContext resolved(AiEntityReference ref, String message) {
            return new ResolvedContext(true, false, false, null, ref, message);
        }

        public static ResolvedContext ambiguous(String msg) {
            return new ResolvedContext(false, true, true, msg, null, null);
        }

        public static ResolvedContext missing(String msg) {
            return new ResolvedContext(false, false, true, msg, null, null);
        }
    }

    public ResolvedContext resolveContext(String currentMessage, List<Map<String, String>> history) {
        if (!StringUtils.hasText(currentMessage)) {
            return ResolvedContext.unchanged(currentMessage);
        }

        // An explicit product code in the current message always wins.
        List<String> explicitCodes = extractCodes(currentMessage);
        if (!explicitCodes.isEmpty()) {
            return ResolvedContext.unchanged(currentMessage);
        }

        boolean hasPronoun = PRONOUN_REFERENCE.matcher(currentMessage).find();
        boolean hasAttribute = ATTRIBUTE_QUERY.matcher(currentMessage).find();

        if (!hasPronoun && !hasAttribute) {
            return ResolvedContext.unchanged(currentMessage);
        }

        // If history is empty and an attribute is requested without an entity:
        if (history == null || history.isEmpty()) {
            if (hasPronoun || isFollowUpAttribute(currentMessage)) {
                return ResolvedContext.missing(
                        "Which product are you referring to? Please provide its product code or part number."
                );
            }
            return ResolvedContext.unchanged(currentMessage);
        }

        // Search history backwards for latest product context
        List<AiEntityReference> candidates = findProductCandidatesInHistory(history);

        if (candidates.isEmpty()) {
            if (hasPronoun || isFollowUpAttribute(currentMessage)) {
                return ResolvedContext.missing(
                        "Which product are you referring to? Please provide its product code or part number."
                );
            }
            return ResolvedContext.unchanged(currentMessage);
        }

        if (candidates.size() > 1) {
            return ResolvedContext.ambiguous(
                    "I found more than one product in recent context. Please specify which product code or part number."
            );
        }

        AiEntityReference candidate = candidates.get(0);
        String augmented = currentMessage + " (product code: " + candidate.identifier() + ")";
        return ResolvedContext.resolved(candidate, augmented);
    }

    private boolean isFollowUpAttribute(String message) {
        String trimmed = message.trim().toLowerCase(Locale.ROOT);
        return trimmed.equals("what is minimum stock")
                || trimmed.equals("minimum stock")
                || trimmed.equals("what is the minimum stock")
                || trimmed.equals("what is available quantity")
                || trimmed.equals("what is the available quantity")
                || trimmed.equals("available quantity")
                || trimmed.equals("how many available quantity")
                || trimmed.equals("how many available")
                || trimmed.equals("what is its part number")
                || trimmed.equals("part number")
                || trimmed.equals("is it low in stock")
                || trimmed.equals("show its product details")
                || trimmed.equals("show its stock")
                || trimmed.equals("show stock")
                || trimmed.equals("what is the stock")
                || trimmed.equals("stock");
    }

    private List<AiEntityReference> findProductCandidatesInHistory(List<Map<String, String>> history) {
        // Iterate backwards from newest to oldest message
        for (int i = history.size() - 1; i >= 0; i--) {
            Map<String, String> entry = history.get(i);
            if (entry == null) continue;
            String content = entry.get("content");
            if (!StringUtils.hasText(content)) continue;

            if (CONTEXT_RESET.matcher(content).find()) {
                return List.of();
            }

            // Extract structured product lines from assistant message if present
            String name = extractRegex(content, PRODUCT_NAME_LINE);
            String code = extractRegex(content, PRODUCT_CODE_LINE);
            String part = extractRegex(content, PART_NUMBER_LINE);

            if (code != null) {
                return List.of(AiEntityReference.ofProduct(code, name, part));
            }

            // Extract explicit codes from user or assistant message
            List<String> codes = extractCodes(content);
            if (codes.size() == 1) {
                return List.of(AiEntityReference.ofProduct(codes.get(0), name, part));
            } else if (codes.size() > 1) {
                List<AiEntityReference> refs = new ArrayList<>();
                for (String c : codes) {
                    refs.add(AiEntityReference.ofProduct(c));
                }
                return refs;
            }
        }
        return List.of();
    }

    private static String extractRegex(String text, Pattern pattern) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }

    private static List<String> extractCodes(String text) {
        List<String> codes = new ArrayList<>();
        Matcher matcher = PRODUCT_IDENTIFIER.matcher(text);
        while (matcher.find()) {
            String code = matcher.group().trim().toUpperCase(Locale.ROOT);
            if (!code.startsWith("PUR-") && !code.startsWith("INV-") && !code.startsWith("CUS") && !code.startsWith("SUP")) {
                if (!codes.contains(code)) {
                    codes.add(code);
                }
            }
        }
        return codes;
    }
}
