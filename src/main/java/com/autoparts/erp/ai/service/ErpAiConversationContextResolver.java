package com.autoparts.erp.ai.service;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves explicit references such as "its stock" against the most recent
 * unambiguous product mentioned by the user in the same conversation.
 *
 * This resolver is deliberately deterministic: it never asks the LLM to
 * invent or guess a product identifier.
 */
public final class ErpAiConversationContextResolver {

    private static final Pattern PRODUCT_IDENTIFIER = Pattern.compile(
            "\\b(?=[A-Z0-9-]*[A-Z])(?=[A-Z0-9-]*\\d)[A-Z0-9]+(?:-[A-Z0-9]+)*\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PRODUCT_REFERENCE = Pattern.compile(
            "\\b(?:it|its|this|that|same|those|them)\\b"
                    + "|\\b(?:the|this|that|same)\\s+(?:product|item|one)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PRODUCT_INFORMATION_REQUEST = Pattern.compile(
            "\\b(?:stock|inventory|quantity|availability|details?|information|price|"
                    + "description|specifications?|part\\s+number|movement|movements|"
                    + "transaction\\s+history|adjustment\\s+history)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern GLOBAL_REQUEST = Pattern.compile(
            "\\b(?:low\\s+stock|below\\s+(?:the\\s+)?minimum|restock(?:ing)?|"
                    + "out\\s+of\\s+stock|all\\s+products|every\\s+product|"
                    + "all\\s+inventory\\s+movements|recent\\s+movements|"
                    + "all\\s+(?:inventory\\s+)?adjustments)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern CONTEXT_RESET = Pattern.compile(
            "\\b(?:new\\s+topic|different\\s+product|another\\s+product|"
                    + "switch\\s+to|forget\\s+(?:that|the\\s+previous))\\b",
            Pattern.CASE_INSENSITIVE
    );

    private ErpAiConversationContextResolver() {
    }

    public static Resolution resolve(
            String currentMessage,
            List<Map<String, String>> history,
            ErpAiIntentResult initialClassification
    ) {
        if (currentMessage == null || currentMessage.isBlank()
                || initialClassification == null) {
            return Resolution.unchanged(currentMessage);
        }

        // An explicit identifier in the current message always wins.
        if (!extractIdentifiers(currentMessage).isEmpty()) {
            return Resolution.unchanged(currentMessage);
        }

        if (!requiresProductContext(currentMessage, initialClassification)) {
            return Resolution.unchanged(currentMessage);
        }

        ContextCandidate candidate = findLatestProductContext(currentMessage, history);
        if (candidate.ambiguous()) {
            return Resolution.clarification(
                    "I found more than one product in the recent context. "
                            + "Please specify the product code or part number."
            );
        }

        if (candidate.productCode().isEmpty()) {
            return Resolution.clarification(
                    "Which product are you referring to? Please provide its product code or part number."
            );
        }

        return Resolution.resolved(
                currentMessage + " (product code: " + candidate.productCode() + ")"
        );
    }

    private static boolean requiresProductContext(
            String message,
            ErpAiIntentResult classification
    ) {
        if (GLOBAL_REQUEST.matcher(message).find()) {
            return false;
        }

        boolean hasProductReference = PRODUCT_REFERENCE.matcher(message).find();
        boolean hasProductInformationCue = PRODUCT_INFORMATION_REQUEST.matcher(message).find();

        boolean productScopedIntent = switch (classification.intent()) {
            case "PRODUCT_SEARCH", "PRODUCT_DETAILS", "PRODUCT_STOCK", "PRODUCT_MOVEMENT_HISTORY",
                    "PRODUCT_MOVEMENT_SUMMARY", "PRODUCT_MOVEMENTS_BY_DATE_RANGE",
                    "INVENTORY_ADJUSTMENTS" -> true;
            case "CLARIFICATION" -> hasProductInformationCue;
            default -> false;
        };

        return productScopedIntent && (hasProductReference || hasProductInformationCue);
    }

    private static ContextCandidate findLatestProductContext(
            String currentMessage,
            List<Map<String, String>> history
    ) {
        if (history == null || history.isEmpty()) {
            return ContextCandidate.none();
        }

        int lastUserMessageIndex = -1;
        for (int i = history.size() - 1; i >= 0; i--) {
            Map<String, String> entry = history.get(i);
            if (entry != null && "USER".equalsIgnoreCase(entry.get("role"))) {
                lastUserMessageIndex = i;
                break;
            }
        }

        int startIndex = lastUserMessageIndex;
        if (lastUserMessageIndex >= 0) {
            String lastUserMessage = history.get(lastUserMessageIndex).get("content");
            if (currentMessage.equals(lastUserMessage)) {
                startIndex--;
            }
        }

        for (int i = startIndex; i >= 0; i--) {
            Map<String, String> entry = history.get(i);
            if (entry == null || !"USER".equalsIgnoreCase(entry.get("role"))) {
                continue;
            }

            String content = entry.get("content");
            if (content == null || content.isBlank()) {
                continue;
            }

            if (CONTEXT_RESET.matcher(content).find()) {
                return ContextCandidate.none();
            }

            List<String> identifiers = extractIdentifiers(content);
            if (identifiers.size() > 1) {
                return ContextCandidate.ambiguousCandidate();
            }
            if (identifiers.size() == 1) {
                return ContextCandidate.of(identifiers.get(0));
            }
        }

        return ContextCandidate.none();
    }

    private static List<String> extractIdentifiers(String message) {
        java.util.ArrayList<String> identifiers = new java.util.ArrayList<>();
        Matcher matcher = PRODUCT_IDENTIFIER.matcher(message);
        while (matcher.find()) {
            String identifier = matcher.group().trim().toUpperCase(java.util.Locale.ROOT);
            if (!identifiers.contains(identifier)) {
                identifiers.add(identifier);
            }
        }
        return List.copyOf(identifiers);
    }

    private record ContextCandidate(String productCode, boolean ambiguous) {
        private static ContextCandidate of(String productCode) {
            return new ContextCandidate(productCode, false);
        }

        private static ContextCandidate none() {
            return new ContextCandidate("", false);
        }

        private static ContextCandidate ambiguousCandidate() {
            return new ContextCandidate("", true);
        }
    }

    public record Resolution(
            String resolvedMessage,
            boolean contextApplied,
            boolean needsClarification,
            String clarificationMessage
    ) {
        private static Resolution unchanged(String message) {
            return new Resolution(message == null ? "" : message, false, false, "");
        }

        private static Resolution resolved(String message) {
            return new Resolution(message, true, false, "");
        }

        private static Resolution clarification(String message) {
            return new Resolution("", false, true, message);
        }
    }
}
