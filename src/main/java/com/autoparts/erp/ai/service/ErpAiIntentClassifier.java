package com.autoparts.erp.ai.service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ErpAiIntentClassifier {

    private static final Pattern PRODUCT_IDENTIFIER = Pattern.compile(
            "\\b(?=[A-Z0-9-]*[A-Z])(?=[A-Z0-9-]*\\d)[A-Z0-9]+(?:-[A-Z0-9]+)*\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern PURCHASE_NUMBER = Pattern.compile(
            "\\bPUR-[A-Z0-9-]+\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern SUPPLIER_INVOICE_NUMBER = Pattern.compile(
            "\\b(?:supplier\\s+)?invoice(?:\\s+(?:number|no\\.?|#))?\\s*[:#-]?\\s*([A-Z0-9][A-Z0-9/-]*)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern PURCHASE_MENTION = Pattern.compile(
            "\\b(?:purchases?|purchase\\s+invoices?|supplier\\s+invoices?)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern PURCHASE_LOOKUP_CUE = Pattern.compile(
            "\\b(?:show|find|search|list|get|view|recent|latest|all|details?|information|status|"
                    + "amount|total|history|invoice|number|from|for|by)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern PURCHASE_DETAIL_CUE = Pattern.compile(
            "\\b(?:details?|information|status|amount|total|invoice\\s+(?:number|no\\.?|#))\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern PRODUCT_COMPARISON_REQUEST = Pattern.compile(
            "\\b(?:compare|comparison|differences?\\s+between|versus|vs\\.?)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern ISO_DATE =
            Pattern.compile("\\b\\d{4}-\\d{2}-\\d{2}\\b");

    private static final Pattern RESULT_LIMIT =
            Pattern.compile("\\b(?:last|latest|top|recent)\\s+(\\d+)\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern RECENT_MOVEMENTS = Pattern.compile(
            "\\b(?:recent|latest|last\\s+\\d+|show\\s+\\d+\\s+latest)\\b"
                    + ".*\\b(?:inventory\\s+)?(?:stock\\s+)?movements?\\b"
                    + "|\\b(?:recent|latest)\\s+(?:inventory\\s+)?activity\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern ADJUSTMENT_REQUEST = Pattern.compile(
            "\\b(?:inventory\\s+adjustments?|stock\\s+adjustments?|adjustment\\s+(?:history|records?|logs?)|"
                    + "show\\s+adjustments?|list\\s+adjustments?)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern MOVEMENT_SUMMARY_REQUEST = Pattern.compile(
            "\\b(?:movement\\s+summary|summary\\s+of\\s+(?:inventory\\s+)?movements?|"
                    + "movement\\s+totals?|stock\\s+movement\\s+summary|"
                    + "how many (?:units|items) (?:were )?(?:purchased|sold|adjusted))\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern MOVEMENT_HISTORY_REQUEST = Pattern.compile(
            "\\b(?:movement\\s+history|movements?|stock\\s+movements?|inventory\\s+history|"
                    + "transaction\\s+(?:history|logs?)|stock\\s+(?:in|out)|inward|outward|"
                    + "in\\s*/\\s*out\\s+movements?|movement\\s+logs?)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern PRODUCT_COUNT_REQUEST = Pattern.compile(
            "\\b(?:how\\s+many|count(?:\\s+of)?|number\\s+of|total(?:\\s+number\\s+of)?)\\b"
                    + ".*\\bproducts?\\b|\\bproducts?\\b.*\\b(?:count|total|number)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern ACTIVE_PRODUCTS_AVAILABLE = Pattern.compile(
            "\\bactive\\s+products?\\s+(?:are\\s+)?available\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern LOW_STOCK_REQUEST = Pattern.compile(
            "\\b(?:low\\s+stock|stock\\s+(?:is\\s+)?low|below\\s+(?:the\\s+)?minimum|"
                    + "under\\s+(?:the\\s+)?minimum|running\\s+low|need(?:s)?\\s+restock(?:ing)?|"
                    + "restock(?:ing)?|replenish(?:ment)?|out\\s+of\\s+stock)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern PRODUCT_STOCK_REQUEST =
            Pattern.compile("\\b(?:stock|inventory|quantity|availability)\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern PRODUCT_DETAILS_REQUEST =
            Pattern.compile("\\b(?:details?|information|price|description|specifications?|part\\s+number)\\b",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern PRODUCT_SEARCH_REQUEST =
            Pattern.compile("\\b(?:find|search|look\\s*up|lookup|show|display|list|get|tell\\s+me\\s+about)\\b",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern GENERIC_STOCK_REQUEST =
            Pattern.compile("^(?:current\\s+)?(?:stock|inventory|quantity|availability)$",
                    Pattern.CASE_INSENSITIVE);

    private static final List<Pattern> SEARCH_PREFIXES = List.of(
            Pattern.compile("^(?:(?:please|kindly)\\s+)+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^(?:(?:can|could|would)\\s+you\\s+)+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^(?:find|search(?:\\s+for)?|look\\s*up|lookup|show(?:\\s+me)?|display|list|get|check|tell\\s+me\\s+about|give\\s+me)\\s+",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("^(?:what(?:\\s+is|\\s+are|'s)|how\\s+much(?:\\s+stock)?|where\\s+can\\s+i\\s+find)\\s+",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("^(?:the\\s+)?(?:stock(?:\\s+level|\\s+quantity)?|inventory|(?:available\\s+)?quantity|details?|information|price|description|specifications?)\\b(?:\\s+(?:of|for|on|about))?\\s*",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("^(?:the\\s+)?(?:products?|items?)(?:\\s+(?:named|called|by\\s+name))?\\b\\s*",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("^(?:for|about|of|a|an|the)\\b\\s*", Pattern.CASE_INSENSITIVE));

    public ErpAiIntentResult classify(String message) {
        if (!StringUtils.hasText(message)) {
            return ErpAiIntentResult.clarification("Please tell me what product or inventory information you need.");
        }

        String normalized = message.toLowerCase(Locale.ROOT);
        String identifier = extractIdentifier(message);

        if (isUnsupportedRequest(normalized)) {
            return new ErpAiIntentResult("UNSUPPORTED", extractSearchText(message));
        }

        if (isPurchaseReadRequest(message)) {
            String purchaseIdentifier = extractPurchaseIdentifier(message);
            boolean details = !purchaseIdentifier.isEmpty()
                    || PURCHASE_DETAIL_CUE.matcher(message).find();
            if (details) {
                if (purchaseIdentifier.isEmpty()) {
                    return ErpAiIntentResult.clarification(
                            "Please provide the purchase number or supplier invoice number to retrieve purchase details.");
                }
                return new ErpAiIntentResult("PURCHASE_DETAILS", purchaseIdentifier);
            }
            return new ErpAiIntentResult(
                    "PURCHASE_SEARCH", extractPurchaseSearchText(message), "", "", extractLimit(message), "");
        }

        if (PRODUCT_COMPARISON_REQUEST.matcher(message).find()) {
            List<String> identifiers = extractIdentifiers(message);
            if (identifiers.size() < 2) {
                return ErpAiIntentResult.clarification(
                        "Please provide at least two product codes or part numbers to compare.");
            }
            if (identifiers.size() > 5) {
                return ErpAiIntentResult.clarification(
                        "Please compare no more than five products at a time.");
            }
            return new ErpAiIntentResult(
                    "PRODUCT_COMPARISON", identifiers.get(0), "", "", null, "", identifiers);
        }

        if (RECENT_MOVEMENTS.matcher(message).find()) {
            int limit = extractLimit(message);
            return new ErpAiIntentResult("RECENT_INVENTORY_MOVEMENTS", "", "", "", limit, "");
        }

        if (ADJUSTMENT_REQUEST.matcher(message).find()) {
            return new ErpAiIntentResult("INVENTORY_ADJUSTMENTS", identifier, "", "", 50, "");
        }

        if (MOVEMENT_SUMMARY_REQUEST.matcher(message).find()) {
            if (identifier.isEmpty()) {
                return ErpAiIntentResult.clarification(
                        "Please provide the product code or part number to retrieve its movement summary.");
            }
            return new ErpAiIntentResult("PRODUCT_MOVEMENT_SUMMARY", identifier);
        }

        if (MOVEMENT_HISTORY_REQUEST.matcher(message).find()) {
            Matcher dates = ISO_DATE.matcher(message);
            String firstDate = dates.find() ? dates.group() : "";
            String secondDate = dates.find() ? dates.group() : "";

            if (!firstDate.isEmpty() || !secondDate.isEmpty()
                    || normalized.contains(" from ") || normalized.contains(" between ")) {
                if (identifier.isEmpty()) {
                    return ErpAiIntentResult.clarification(
                            "Please provide the product code or part number for the date-range movement search.");
                }
                if (firstDate.isEmpty() || secondDate.isEmpty()) {
                    return ErpAiIntentResult.clarification(
                            "Please provide both start and end dates in YYYY-MM-DD format.");
                }
                try {
                    LocalDate from = LocalDate.parse(firstDate);
                    LocalDate to = LocalDate.parse(secondDate);
                    if (to.isBefore(from)) {
                        return ErpAiIntentResult.clarification(
                                "The end date must be on or after the start date.");
                    }
                } catch (DateTimeParseException ex) {
                    return ErpAiIntentResult.clarification(
                            "Please provide valid dates in YYYY-MM-DD format.");
                }
                return new ErpAiIntentResult(
                        "PRODUCT_MOVEMENTS_BY_DATE_RANGE", identifier, firstDate, secondDate, 50, "");
            }

            if (identifier.isEmpty()) {
                return ErpAiIntentResult.clarification(
                        "Please provide the product code or part number to retrieve movement history.");
            }
            return new ErpAiIntentResult("PRODUCT_MOVEMENT_HISTORY", identifier);
        }

        String searchText = extractSearchText(message);
        boolean hasSearchText = StringUtils.hasText(searchText);

        if (LOW_STOCK_REQUEST.matcher(normalized).find()) {
            return new ErpAiIntentResult("LOW_STOCK_PRODUCTS", "");
        }
        if (isProductCountRequest(normalized)) {
            return new ErpAiIntentResult("COUNT_PRODUCTS", "");
        }
        if (PRODUCT_STOCK_REQUEST.matcher(normalized).find()) {
            return hasSearchText && !GENERIC_STOCK_REQUEST.matcher(searchText).matches()
                    ? new ErpAiIntentResult("PRODUCT_STOCK", searchText)
                    : ErpAiIntentResult.clarification(
                            "Please provide the product code or part number to check current stock.");
        }
        if (PRODUCT_DETAILS_REQUEST.matcher(normalized).find()) {
            return hasSearchText
                    ? new ErpAiIntentResult("PRODUCT_DETAILS", searchText)
                    : ErpAiIntentResult.clarification(
                            "Please provide the product code or part number to retrieve product details.");
        }
        if (PRODUCT_SEARCH_REQUEST.matcher(normalized).find()
                || PRODUCT_IDENTIFIER.matcher(message).find()) {
            return hasSearchText
                    ? new ErpAiIntentResult("PRODUCT_SEARCH", searchText)
                    : ErpAiIntentResult.clarification("Please provide a product name, product code, or part number.");
        }
        return new ErpAiIntentResult("GENERAL", searchText);
    }

    private int extractLimit(String message) {
        Matcher matcher = RESULT_LIMIT.matcher(message);
        if (!matcher.find()) {
            return 10;
        }
        try {
            return Math.max(1, Math.min(50, Integer.parseInt(matcher.group(1))));
        } catch (NumberFormatException ex) {
            return 10;
        }
    }

    private boolean isProductCountRequest(String message) {
        return PRODUCT_COUNT_REQUEST.matcher(message).find()
                || ACTIVE_PRODUCTS_AVAILABLE.matcher(message).find();
    }

    private boolean isUnsupportedRequest(String message) {
        boolean purchaseOrSalesWrite = containsAny(message,
                "create purchase", "create sales", "create invoice",
                "generate invoice", "make purchase", "make sales",
                "record purchase", "record sales", "receive purchase",
                "approve purchase", "cancel purchase", "pay purchase");
        return purchaseOrSalesWrite
                || (message.contains("supplier") && !isPurchaseReadRequest(message));
    }

    private boolean isPurchaseReadRequest(String message) {
        boolean hasPurchaseMention = PURCHASE_MENTION.matcher(message).find();
        boolean hasPurchaseNumber = PURCHASE_NUMBER.matcher(message).find();
        return hasPurchaseNumber
                || (hasPurchaseMention && PURCHASE_LOOKUP_CUE.matcher(message).find());
    }

    private String extractPurchaseIdentifier(String message) {
        Matcher purchaseNumber = PURCHASE_NUMBER.matcher(message);
        if (purchaseNumber.find()) {
            return purchaseNumber.group().trim();
        }
        Matcher invoiceNumber = SUPPLIER_INVOICE_NUMBER.matcher(message);
        if (invoiceNumber.find()) {
            return invoiceNumber.group(1).trim();
        }
        return "";
    }

    private String extractPurchaseSearchText(String message) {
        String cleaned = message.trim();
        cleaned = cleaned.replaceFirst("(?i)^(?:please\\s+)?(?:can|could|would)\\s+you\\s+", "");
        cleaned = cleaned.replaceFirst("(?i)^(?:please\\s+)?(?:show|find|search(?:\\s+for)?|list|get|view)\\s+", "");
        cleaned = cleaned.replaceFirst("(?i)^(?:the\\s+)?(?:recent|latest(?:\\s+\\d+)?|all|last\\s+\\d+|top\\s+\\d+)\\s+", "");
        cleaned = cleaned.replaceFirst("(?i)^(?:purchase\\s+invoices?|purchases?|supplier\\s+invoices?|purchase\\s+(?:records?|list))\\b", "");
        cleaned = cleaned.replaceFirst("(?i)^\\s*(?:from|for|by|of)\\s+(?:supplier\\s+)?", "");
        cleaned = cleaned.replaceFirst("(?i)^\\s*(?:supplier\\s+)", "");
        return cleaned.replaceAll("(?i)\\s+(?:last|top|latest)\\s+\\d+$", "")
                .replaceAll("[?!.;,]+$", "")
                .replaceAll("(?i)\\s+(?:please|thanks?)$", "")
                .trim();
    }

    private List<String> extractIdentifiers(String message) {
        java.util.ArrayList<String> identifiers = new java.util.ArrayList<>();
        Matcher matcher = PRODUCT_IDENTIFIER.matcher(message);
        while (matcher.find()) {
            String identifier = matcher.group().trim().toUpperCase(Locale.ROOT);
            if (!identifiers.contains(identifier)) {
                identifiers.add(identifier);
            }
        }
        return List.copyOf(identifiers);
    }

    private String extractIdentifier(String message) {
        Matcher matcher = PRODUCT_IDENTIFIER.matcher(message);
        return matcher.find() ? matcher.group().trim() : "";
    }

    private String extractSearchText(String message) {
        String identifier = extractIdentifier(message);
        if (!identifier.isEmpty()) {
            return identifier;
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
        return cleaned.replaceAll("[?!.;,]+$", "")
                .replaceAll("\\s+(?:please|thanks?)$", "")
                .replaceAll("^[\\p{Punct}\\s]+|[\\p{Punct}\\s]+$", "")
                .trim();
    }

    private boolean containsAny(String message, String... values) {
        for (String value : values) {
            if (message.contains(value)) return true;
        }
        return false;
    }
}
