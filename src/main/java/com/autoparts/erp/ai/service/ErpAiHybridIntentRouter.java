package com.autoparts.erp.ai.service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.autoparts.erp.ai.enums.ClassificationSource;
import com.autoparts.erp.ai.security.ErpAiExecutionContext;

/**
 * Hybrid Intent Router coordinating deterministic fast-path classification,
 * multi-turn clarification resolution across separate HTTP requests,
 * and structured LLM fallback.
 *
 * <p><strong>Key Principles:</strong>
 * <ul>
 *   <li><strong>Deterministic Gate 0:</strong> Mutation requests (single or compound) are rejected
 *       immediately as {@code UNSUPPORTED} before any LLM can be invoked.</li>
 *   <li><strong>Security-Isolated Multi-Turn Clarification:</strong> Pending clarification slots are
 *       verified against the authenticated {@code userSubject} and {@code conversationId}.</li>
 *   <li><strong>Advisory Confidence:</strong> LLM confidence is strictly advisory. Java-level whitelist
 *       validation, parameter plausibility, and domain ambiguity checks are mandatory.</li>
 *   <li><strong>Fail-Closed Domain Safety:</strong> An LLM failure for an ERP domain question fails closed
 *       to {@code CLARIFICATION}, never falling back to {@code GENERAL} conversational generation.</li>
 * </ul>
 * </p>
 */
@Component
public class ErpAiHybridIntentRouter {

    private static final Logger log =
            LoggerFactory.getLogger(ErpAiHybridIntentRouter.class);

    private static final Pattern ISO_DATE =
            Pattern.compile("\\b\\d{4}-\\d{2}-\\d{2}\\b");

    private static final Pattern INVOICE_ID =
            Pattern.compile("\\bINV-\\d+(?:-\\d+)*\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern PURCHASE_ID =
            Pattern.compile("\\bPUR-\\d+\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern PRODUCT_ID = Pattern.compile(
            "\\b(?=[A-Z0-9-]*[A-Z])(?=[A-Z0-9-]*\\d)[A-Z0-9]+(?:-[A-Z0-9]+)*\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern MUTATION_PATTERN = Pattern.compile(
            "\\b(?:create|make|record|add|update|delete|generate|register|process|open|close|post)\\s+"
                    + "(?:a\\s+)?(?:new\\s+)?(?:purchase|sale|sales|invoice|customer|supplier|vendor|return|refund|credit|expense|register|session|closing|product|stock)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern DOMAIN_KEYWORD_PATTERN = Pattern.compile(
            "\\b(?:stock|inventory|product|purchase|purchases|procurement|sale|sales|invoice|invoices|customer|customers|"
                    + "supplier|suppliers|vendor|vendors|return|returns|refund|refunds|credit|credits|register|registers|"
                    + "closing|closings|kpi|kpis|abc|rfm|expense|expenses|audit|reconciliation|ledger|balance|balances)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern NON_PRODUCT_DOMAIN_PATTERN = Pattern.compile(
            "\\b(?:purchase|purchases|procurement|sale|sales|invoice|invoices|customer|customers|"
                    + "supplier|suppliers|vendor|vendors|return|returns|refund|refunds|credit|credits|register|registers|"
                    + "closing|closings|kpi|kpis|abc|rfm|expense|expenses|audit|reconciliation|ledger|balance|balances)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern GENERIC_STOCK_INQUIRY_PATTERN = Pattern.compile(
            "^(?:(?:explain\\s+)?what\\s+is\\s+(?:the\\s+)?|show\\s+(?:me\\s+)?(?:the\\s+)?|check\\s+(?:the\\s+)?|get\\s+(?:the\\s+)?)?(?:current\\s+)?(?:stock|inventory|quantity|availability)[?!.]*$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern EDUCATIONAL_QUESTION_PATTERN = Pattern.compile(
            "^(?:explain\\s+how\\s+[a-z\\s]+(?:work|works|operate|operates|function|functions)"
                    + "|explain\\s+(?:the\\s+concept\\s+of|the\\s+difference\\s+between)"
                    + "|explain\\s+what\\s+(?:an?|the\\s+concept\\s+of)\\s+[a-z\\s]+"
                    + "|explain\\s+what\\s+[a-z\\s]+(?:is|are|means?|stands\\s+for)"
                    + "|what\\s+(?:is|are)\\s+(?:an?|the\\s+concept\\s+of|the\\s+difference\\s+between)\\s+[a-z\\s]+"
                    + "|how\\s+(?:does|do)\\s+[a-z\\s]+(?:work|function|operate)"
                    + "|help\\s+me\\s+understand\\s+(?:the\\s+concept\\s+of|the\\s+difference\\s+between|how\\s+[a-z\\s]+(?:works?|operates?)))\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern LIVE_DATA_INDICATOR = Pattern.compile(
            "\\b(?:current|today|yesterday|this\\s+(?:week|month|quarter|year)|last\\s+(?:week|month|quarter|year)"
                    + "|recent|latest|now|our|we|my|look|performance\\s+of|status\\s+of|balance|total|figures|numbers|turnover|variance)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern ENTITY_REFERENCE_PATTERN = Pattern.compile(
            "\\b(?:of|for|from|by)\\s+[A-Z][a-z]+");

    private final ErpAiIntentClassifier deterministicClassifier;
    private final ErpAiPendingClarificationStore clarificationStore;
    private final ErpAiLlmClassifier llmClassifier;

    @Autowired
    public ErpAiHybridIntentRouter(
            ErpAiIntentClassifier deterministicClassifier,
            ErpAiPendingClarificationStore clarificationStore,
            ErpAiLlmClassifier llmClassifier
    ) {
        this.deterministicClassifier = deterministicClassifier;
        this.clarificationStore = clarificationStore != null
                ? clarificationStore
                : new ErpAiPendingClarificationStore();
        this.llmClassifier = llmClassifier;
    }

    /** Constructor for deterministic unit testing without LLM. */
    public ErpAiHybridIntentRouter(
            ErpAiIntentClassifier deterministicClassifier,
            ErpAiPendingClarificationStore clarificationStore
    ) {
        this(deterministicClassifier, clarificationStore, null);
    }

    public ErpAiIntentResult route(
            String currentMessage,
            List<Map<String, String>> history,
            UUID conversationId,
            ErpAiExecutionContext executionContext
    ) {
        if (!StringUtils.hasText(currentMessage)) {
            return ErpAiIntentResult.clarification("Please tell me what ERP information you need.");
        }

        String userSubject = executionContext != null ? executionContext.userSubject() : null;

        // -------------------------------------------------------------
        // GATE 0: DETERMINISTIC MUTATION & COMPOUND MUTATION CHECK
        // -------------------------------------------------------------
        if (isProhibitedMutationRequest(currentMessage)) {
            log.info("Prohibited mutation operation detected deterministically. Rejecting as UNSUPPORTED.");
            return new ErpAiIntentResult("UNSUPPORTED", extractSearchText(currentMessage));
        }

        // -------------------------------------------------------------
        // GATE 1: MULTI-TURN PENDING CLARIFICATION RESOLUTION
        // -------------------------------------------------------------
        if (conversationId != null && userSubject != null) {
            Optional<ErpAiPendingClarificationStore.PendingSlot> pendingOpt =
                    clarificationStore.findAndValidate(conversationId, userSubject);

            if (pendingOpt.isPresent()) {
                ErpAiPendingClarificationStore.PendingSlot pending = pendingOpt.get();
                Optional<ErpAiIntentResult> resolved = tryResolvePendingSlot(currentMessage, pending);
                if (resolved.isPresent()) {
                    clarificationStore.remove(conversationId, userSubject);
                    log.info("Resolved pending clarification for conversation '{}', intent '{}'",
                            conversationId, resolved.get().intent());
                    return resolved.get();
                }
            }
        }

        // -------------------------------------------------------------
        // GATE 2: DETERMINISTIC AMBIGUITY PRE-CHECKS
        // -------------------------------------------------------------
        // Check for ambiguous invoice query without invoice number or customer/supplier distinction
        if (isAmbiguousInvoiceRequest(currentMessage)) {
            log.info("Ambiguous invoice request without invoice number or customer/supplier designation: '{}'. Clarifying.", currentMessage);
            return ErpAiIntentResult.clarification(
                    "Please provide the invoice number (e.g., INV-0001) or specify whether you are looking for a customer sales invoice or a supplier invoice."
            );
        }

        // Check for compound read queries mentioning conflicting primary domains (e.g. purchases AND sales)
        if (hasConflictingDomainCues(currentMessage)) {
            log.info("Conflicting domain cues detected in query: '{}'. Overriding to CLARIFICATION.", currentMessage);
            return ErpAiIntentResult.clarification(
                    "Your request asks for multiple distinct ERP areas (such as purchases and sales). Please clarify whether you would like to view purchases or sales invoices first."
            );
        }

        // -------------------------------------------------------------
        // GATE 3: DETERMINISTIC FAST-PATH CLASSIFICATION
        // -------------------------------------------------------------
        ErpAiIntentResult deterministic = deterministicClassifier.classify(currentMessage);

        // Apply same-conversation anaphora/pronoun context resolution
        ErpAiConversationContextResolver.Resolution resolution =
                ErpAiConversationContextResolver.resolve(currentMessage, history, deterministic);
        if (resolution.needsClarification()) {
            return ErpAiIntentResult.clarification(resolution.clarificationMessage());
        } else if (resolution.contextApplied()) {
            deterministic = deterministicClassifier.classify(resolution.resolvedMessage());
        }

        // If deterministic classifier matched a domain intent or UNSUPPORTED, return immediately
        if (!"GENERAL".equals(deterministic.intent())) {
            // Check if generic stock inquiry lacked product code (e.g., "Explain what is the stock?")
            if ("PRODUCT_STOCK".equals(deterministic.intent())
                    && !PRODUCT_ID.matcher(currentMessage).find()
                    && GENERIC_STOCK_INQUIRY_PATTERN.matcher(currentMessage.trim()).matches()) {
                log.info("Generic stock inquiry without product code: '{}'. Clarifying.", currentMessage);
                return ErpAiIntentResult.clarification("Please provide the product code or part number to check current stock.");
            }

            // Check if PRODUCT_SEARCH or PRODUCT_STOCK falsely matched a non-product domain query (e.g., "Explain what is our inventory balance?")
            if (("PRODUCT_SEARCH".equals(deterministic.intent()) || "PRODUCT_STOCK".equals(deterministic.intent()))
                    && !PRODUCT_ID.matcher(currentMessage).find()
                    && NON_PRODUCT_DOMAIN_PATTERN.matcher(currentMessage).find()) {
                log.info("Product intent falsely matched non-product domain query: '{}'. Diverting to LLM fallback.", currentMessage);
                deterministic = new ErpAiIntentResult("GENERAL", currentMessage);
            } else {
                // If the deterministic result is a clarification for a missing slot, register it in the store
                if ("CLARIFICATION".equals(deterministic.intent()) && conversationId != null && userSubject != null) {
                    registerSlotIfApplicable(currentMessage, deterministic, conversationId, userSubject);
                }
                return deterministic;
            }
        }

        // -------------------------------------------------------------
        // GATE 4: LLM FALLBACK (Only when deterministic produced GENERAL)
        // -------------------------------------------------------------
        if (llmClassifier == null) {
            return handleLlmAbsenceOrFailure(currentMessage, deterministic);
        }

        Optional<ErpAiIntentResult> llmResultOpt = llmClassifier.classify(currentMessage, history);
        if (llmResultOpt.isEmpty()) {
            return handleLlmAbsenceOrFailure(currentMessage, deterministic);
        }

        // -------------------------------------------------------------
        // GATE 5: ADVISORY CONFIDENCE & JAVA AMBIGUITY VALIDATION
        // -------------------------------------------------------------
        ErpAiIntentResult candidate = llmResultOpt.get();

        // 1. Prohibited mutation check on candidate
        if ("UNSUPPORTED".equals(candidate.intent()) || isProhibitedMutationRequest(candidate.searchText())) {
            return new ErpAiIntentResult("UNSUPPORTED", extractSearchText(currentMessage));
        }

        // 2. Intent whitelist validation
        if (!ErpAiIntentResult.isSupported(candidate.intent())) {
            log.warn("LLM suggested intent outside whitelist: '{}'. Rejecting.", candidate.intent());
            return handleLlmAbsenceOrFailure(currentMessage, deterministic);
        }

        // 3. Do not allow ERP-domain queries to be classified as GENERAL
        if ("GENERAL".equals(candidate.intent())
                && !isEducationalQuestion(currentMessage)
                && DOMAIN_KEYWORD_PATTERN.matcher(currentMessage).find()) {
            log.info("Query contains ERP domain keywords but LLM proposed GENERAL. Failing closed to CLARIFICATION.");
            return ErpAiIntentResult.clarification(
                    "I couldn't identify the exact ERP records you're looking for. Please specify the product code, invoice number, customer name, or date range."
            );
        }

        // 4. Missing required slot check for date-dependent intents
        if ("DAILY_CLOSING".equals(candidate.intent()) && !StringUtils.hasText(candidate.startDate())) {
            if (conversationId != null && userSubject != null) {
                clarificationStore.register(conversationId, userSubject, "DAILY_CLOSING", "startDate", Map.of());
            }
            return ErpAiIntentResult.clarification(
                    "Please provide the business closing date in YYYY-MM-DD format.", "startDate"
            );
        }

        // 5. Advisory confidence threshold check
        if (candidate.confidence() < 0.70) {
            log.info("Advisory confidence ({}) below 0.70 threshold for query: '{}'. Overriding to CLARIFICATION.",
                    candidate.confidence(), currentMessage);
            return ErpAiIntentResult.clarification(
                    "Could you please provide more details or an identifier (such as a part number or invoice number) for your request?"
            );
        }

        // 6. Compound query handling
        if (candidate.isMultiIntent() && StringUtils.hasText(candidate.secondaryIntent())) {
            if (isProhibitedMutationRequest(candidate.secondaryIntent())) {
                log.info("Compound query contains a prohibited mutation in secondary request. Rejecting entire query.");
                return new ErpAiIntentResult("UNSUPPORTED", extractSearchText(currentMessage));
            }
        }

        return candidate;
    }

    public void clearPendingClarification(UUID conversationId, String userSubject) {
        if (conversationId != null && userSubject != null) {
            clarificationStore.remove(conversationId, userSubject);
        }
    }

    private boolean isProhibitedMutationRequest(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        if (MUTATION_PATTERN.matcher(text).find()) {
            return true;
        }
        return deterministicClassifier.classify(text).intent().equals("UNSUPPORTED");
    }

    private boolean isAmbiguousInvoiceRequest(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        // If it specifies INV- or PUR- it is not ambiguous
        if (INVOICE_ID.matcher(message).find() || PURCHASE_ID.matcher(message).find()) {
            return false;
        }
        // If it mentions invoice, but does NOT specify "sales invoice" or "customer", and mentions a party name
        if (lower.contains("invoice") && !lower.contains("sales invoice") && !lower.contains("customer invoice") && !lower.contains("supplier invoice")) {
            if (lower.contains("for ") || lower.contains("of ") || lower.contains("from ")) {
                return true;
            }
        }
        return false;
    }

    private boolean hasConflictingDomainCues(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        boolean hasSales = lower.contains("sale") || lower.contains("sales") || lower.contains("invoice");
        boolean hasPurchases = lower.contains("purchase") || lower.contains("purchases") || lower.contains("procurement") || lower.contains("supplier order");
        boolean hasCustomer = lower.contains("customer") || lower.contains("client");
        boolean hasSupplier = lower.contains("supplier") || lower.contains("vendor");

        // If it mentions both sales and purchases without an explicit INV- or PUR- prefix, flag conflict
        if (hasSales && hasPurchases && !INVOICE_ID.matcher(message).find() && !PURCHASE_ID.matcher(message).find()) {
            return true;
        }
        // If it mentions both customer and supplier without an explicit CUST- code, flag conflict
        if (hasCustomer && hasSupplier && !lower.contains("cust-")) {
            return true;
        }
        return false;
    }

    private ErpAiIntentResult handleLlmAbsenceOrFailure(String message, ErpAiIntentResult deterministicFallback) {
        if (isEducationalQuestion(message)) {
            return deterministicFallback;
        }
        if (DOMAIN_KEYWORD_PATTERN.matcher(message).find()) {
            log.info("Domain keywords detected in query where classification was uncertain. Failing closed to CLARIFICATION.");
            return ErpAiIntentResult.clarification(
                    "I couldn't identify the exact ERP records you're looking for. Please specify the product code, invoice number, or report name."
            );
        }
        return deterministicFallback;
    }

    private boolean isEducationalQuestion(String message) {
        if (!StringUtils.hasText(message)) {
            return false;
        }
        String trimmed = message.trim();
        // Exclude queries with explicit product, invoice, or purchase IDs
        if (PRODUCT_ID.matcher(trimmed).find() || INVOICE_ID.matcher(trimmed).find() || PURCHASE_ID.matcher(trimmed).find()) {
            return false;
        }
        // Exclude queries containing live data indicators or temporal qualifiers
        if (LIVE_DATA_INDICATOR.matcher(trimmed).find()) {
            return false;
        }
        // Exclude queries with entity references (e.g., "of Bosch", "for Acme")
        if (ENTITY_REFERENCE_PATTERN.matcher(trimmed).find()) {
            return false;
        }
        // Exclude inquiries asking for live values with "what is (the|our|my|current)"
        if (Pattern.compile("\\bwhat\\s+is\\s+(?:the|our|my|current)\\b", Pattern.CASE_INSENSITIVE).matcher(trimmed).find()) {
            return false;
        }
        // Must match educational phrasing pattern
        return EDUCATIONAL_QUESTION_PATTERN.matcher(trimmed).find();
    }

    private Optional<ErpAiIntentResult> tryResolvePendingSlot(
            String message,
            ErpAiPendingClarificationStore.PendingSlot pending
    ) {
        String slotName = pending.pendingSlotName();
        String targetIntent = pending.pendingIntent();

        if ("startDate".equalsIgnoreCase(slotName) || "closingDate".equalsIgnoreCase(slotName) || "date".equalsIgnoreCase(slotName)) {
            Matcher matcher = ISO_DATE.matcher(message);
            if (matcher.find()) {
                String date = matcher.group();
                try {
                    LocalDate.parse(date);
                    if ("DAILY_CLOSING".equals(targetIntent)) {
                        return Optional.of(new ErpAiIntentResult(
                                "DAILY_CLOSING", date, date, "", 50, "", List.of(),
                                1.0, ClassificationSource.CONTEXT_RESOLVED, false, "", ""
                        ));
                    }
                    return Optional.of(new ErpAiIntentResult(
                            targetIntent, date, date, "", 50, "", List.of(),
                            1.0, ClassificationSource.CONTEXT_RESOLVED, false, "", ""
                    ));
                } catch (DateTimeParseException ignored) {
                }
            }
        }

        if ("invoiceNumber".equalsIgnoreCase(slotName)) {
            Matcher matcher = INVOICE_ID.matcher(message);
            if (matcher.find()) {
                String invoiceId = matcher.group().trim().toUpperCase(Locale.ROOT);
                return Optional.of(new ErpAiIntentResult(
                        targetIntent, invoiceId, "", "", 50, "", List.of(),
                        1.0, ClassificationSource.CONTEXT_RESOLVED, false, "", ""
                ));
            }
        }

        if ("purchaseNumber".equalsIgnoreCase(slotName)) {
            Matcher matcher = PURCHASE_ID.matcher(message);
            if (matcher.find()) {
                String purchaseId = matcher.group().trim().toUpperCase(Locale.ROOT);
                return Optional.of(new ErpAiIntentResult(
                        targetIntent, purchaseId, "", "", 50, "", List.of(),
                        1.0, ClassificationSource.CONTEXT_RESOLVED, false, "", ""
                ));
            }
        }

        if ("productCode".equalsIgnoreCase(slotName)) {
            Matcher matcher = PRODUCT_ID.matcher(message);
            if (matcher.find()) {
                String productId = matcher.group().trim().toUpperCase(Locale.ROOT);
                return Optional.of(new ErpAiIntentResult(
                        targetIntent, productId, "", "", 50, "", List.of(),
                        1.0, ClassificationSource.CONTEXT_RESOLVED, false, "", ""
                ));
            }
        }

        return Optional.empty();
    }

    private void registerSlotIfApplicable(
            String message,
            ErpAiIntentResult result,
            UUID conversationId,
            String userSubject
    ) {
        String msg = result.clarificationMessage().toLowerCase(Locale.ROOT);
        if (msg.contains("closing date") || msg.contains("yyyy-mm-dd")) {
            clarificationStore.register(conversationId, userSubject, "DAILY_CLOSING", "startDate", Map.of());
        } else if (msg.contains("invoice number")) {
            clarificationStore.register(conversationId, userSubject, "SALE_DETAILS", "invoiceNumber", Map.of());
        } else if (msg.contains("purchase number")) {
            clarificationStore.register(conversationId, userSubject, "PURCHASE_DETAILS", "purchaseNumber", Map.of());
        } else if (msg.contains("product code")) {
            clarificationStore.register(conversationId, userSubject, "PRODUCT_STOCK", "productCode", Map.of());
        }
    }

    private String extractSearchText(String message) {
        return message.replaceAll("^[\\p{Punct}\\s]+|[\\p{Punct}\\s]+$", "").trim();
    }
}
