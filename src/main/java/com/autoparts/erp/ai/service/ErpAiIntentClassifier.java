package com.autoparts.erp.ai.service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ErpAiIntentClassifier {

    private static final Pattern PRODUCT_IDENTIFIER = Pattern.compile(
            "\\b(?=[A-Z0-9-]*[A-Z])(?=[A-Z0-9-]*\\d)[A-Z0-9]+(?:-[A-Z0-9]+)*\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PRODUCT_COUNT_REQUEST = Pattern.compile(
            "\\b(?:how\\s+many|count(?:\\s+of)?|number\\s+of|total(?:\\s+number\\s+of)?)\\b"
                    + ".*\\bproducts?\\b"
                    + "|\\bproducts?\\b.*\\b(?:count|total|number)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern ACTIVE_PRODUCTS_AVAILABLE = Pattern.compile(
            "\\bactive\\s+products?\\s+(?:are\\s+)?available\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern LOW_STOCK_REQUEST = Pattern.compile(
            "\\b(?:low\\s+stock|stock\\s+(?:is\\s+)?low|below\\s+(?:the\\s+)?minimum"
                    + "|under\\s+(?:the\\s+)?minimum|running\\s+low|need(?:s)?\\s+restock(?:ing)?"
                    + "|restock(?:ing)?|replenish(?:ment)?|out\\s+of\\s+stock)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PRODUCT_STOCK_REQUEST = Pattern.compile(
            "\\b(?:stock|inventory|quantity|availability)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PRODUCT_DETAILS_REQUEST = Pattern.compile(
            "\\b(?:details?|information|price|description|specifications?|part\\s+number)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PRODUCT_SEARCH_REQUEST = Pattern.compile(
            "\\b(?:find|search|look\\s*up|lookup|show|display|list|get|tell\\s+me\\s+about)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern GENERIC_STOCK_REQUEST = Pattern.compile(
            "^(?:current\\s+)?(?:stock|inventory|quantity|availability)$",
            Pattern.CASE_INSENSITIVE
    );

    private static final List<Pattern> SEARCH_PREFIXES = List.of(
            Pattern.compile("^(?:(?:please|kindly)\\s+)+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^(?:(?:can|could|would)\\s+you\\s+)+", Pattern.CASE_INSENSITIVE),
            Pattern.compile(
                    "^(?:find|search(?:\\s+for)?|look\\s*up|lookup|show(?:\\s+me)?|display"
                            + "|list|get|check|tell\\s+me\\s+about|give\\s+me)\\s+",
                    Pattern.CASE_INSENSITIVE
            ),
            Pattern.compile(
                    "^(?:what(?:\\s+is|\\s+are|'s)|how\\s+much(?:\\s+stock)?"
                            + "|where\\s+can\\s+i\\s+find)\\s+",
                    Pattern.CASE_INSENSITIVE
            ),
            Pattern.compile(
                    "^(?:the\\s+)?(?:stock(?:\\s+level|\\s+quantity)?|inventory"
                            + "|(?:available\\s+)?quantity|details?|information|price"
                            + "|description|specifications?)\\b(?:\\s+(?:of|for|on|about))?\\s*",
                    Pattern.CASE_INSENSITIVE
            ),
            Pattern.compile(
                    "^(?:the\\s+)?(?:products?|items?)(?:\\s+(?:named|called|by\\s+name))?\\b\\s*",
                    Pattern.CASE_INSENSITIVE
            ),
            Pattern.compile("^(?:for|about|of|a|an|the)\\b\\s*", Pattern.CASE_INSENSITIVE)
    );

    public ErpAiIntentResult classify(String message) {
        if (!StringUtils.hasText(message)) {
            return new ErpAiIntentResult("CLARIFICATION", "");
        }

        String normalized = message.toLowerCase(Locale.ROOT);
        String searchText = extractSearchText(message);

        if (isUnsupportedRequest(normalized)) {
            return new ErpAiIntentResult("UNSUPPORTED", searchText);
        }

        if (LOW_STOCK_REQUEST.matcher(normalized).find()) {
            return new ErpAiIntentResult("LOW_STOCK_PRODUCTS", searchText);
        }

        if (isProductCountRequest(normalized)) {
            return new ErpAiIntentResult("COUNT_PRODUCTS", searchText);
        }

        boolean hasSearchText = StringUtils.hasText(searchText);

        if (PRODUCT_STOCK_REQUEST.matcher(normalized).find()) {
            return new ErpAiIntentResult(
                    hasSearchText && !GENERIC_STOCK_REQUEST.matcher(searchText).matches()
                            ? "PRODUCT_STOCK"
                            : "CLARIFICATION",
                    searchText
            );
        }

        if (PRODUCT_DETAILS_REQUEST.matcher(normalized).find()) {
            return new ErpAiIntentResult(
                    hasSearchText ? "PRODUCT_DETAILS" : "CLARIFICATION",
                    searchText
            );
        }

        if (PRODUCT_SEARCH_REQUEST.matcher(normalized).find()
                || PRODUCT_IDENTIFIER.matcher(message).find()) {
            return new ErpAiIntentResult(
                    hasSearchText ? "PRODUCT_SEARCH" : "CLARIFICATION",
                    searchText
            );
        }

        return new ErpAiIntentResult("GENERAL", searchText);
    }

    private boolean isProductCountRequest(String message) {
        return PRODUCT_COUNT_REQUEST.matcher(message).find()
                || ACTIVE_PRODUCTS_AVAILABLE.matcher(message).find();
    }

    private boolean isUnsupportedRequest(String message) {
        return message.contains("supplier")
                || containsAny(
                        message,
                        "create purchase",
                        "create sales",
                        "create invoice",
                        "generate invoice",
                        "make purchase",
                        "make sales",
                        "record purchase",
                        "record sales"
                );
    }

    private String extractSearchText(String message) {
        var identifierMatcher = PRODUCT_IDENTIFIER.matcher(message);
        if (identifierMatcher.find()) {
            return identifierMatcher.group().trim();
        }

        String cleaned = message.trim();
        boolean changed;
        do {
            String previous = cleaned;
            for (Pattern prefix : SEARCH_PREFIXES) {
                cleaned = prefix.matcher(cleaned).replaceFirst("");
            }
            changed = !cleaned.equals(previous);
        } while (changed);

        cleaned = cleaned.replaceAll("[?!.;,]+$", "")
                .replaceAll("\\s+(?:please|thanks?)$", "")
                .replaceAll("^[\\p{Punct}\\s]+|[\\p{Punct}\\s]+$", "")
                .trim();

        return cleaned;
    }

    private boolean containsAny(String message, String... values) {
        for (String value : values) {
            if (message.contains(value)) {
                return true;
            }
        }
        return false;
    }
}
