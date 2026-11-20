package com.autoparts.erp.ai.rag.citation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.autoparts.erp.ai.rag.model.RetrievedChunk;

/**
 * Validates model citations against the exact authorized chunks supplied to the current request.
 * Invalid or uncited factual sentences are removed rather than leaving unsupported prose behind.
 *
 * <p>When hybrid ERP facts are present, every transaction-specific claim (identifier, date,
 * amount, supplier, payment status) is validated against the exact purchase record that the
 * claim refers to — not against a global union of all retrieved records. Policy facts (amounts,
 * dates) supported by cited RAG documents are separated from ERP transaction facts, and multi-transaction
 * sentences are attributed per-transaction or failed closed if ambiguous.</p>
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

    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile(
            "\\b(?:PUR|INV|GAP|PO|SO|SOP|POL)-\\d+(?:-[A-Za-z0-9]+)*\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern PURCHASE_ID_PATTERN = Pattern.compile(
            "\\bPUR-\\d+\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern INVOICE_ID_PATTERN = Pattern.compile(
            "\\b(?:INV-\\d+(?:-[A-Za-z0-9]+)*|SUP-INV-[A-Za-z0-9]+)\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern DATE_PATTERN = Pattern.compile(
            "\\b\\d{4}-\\d{2}-\\d{2}\\b");

    // Currency-prefixed amounts (₹, $, USD, INR, Rs.) OR standalone decimal amounts (N.NN)
    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
            "(?:(?:\\$|\u20b9|USD\\s*|INR\\s*|Rs\\.?\\s*)\\s*\\d+(?:,\\d{3})*(?:\\.\\d{2})?|\\b\\d+(?:,\\d{3})*(?:\\.\\d{2})\\b)");

    private static final Pattern POLICY_CLAIM_PATTERN = Pattern.compile(
            "\\b(?:policy\\s+(?:states|specifies|allows|prohibits|mandates)|under\\s+(?:company|store|the)\\s+policy|"
                    + "according\\s+to\\s+(?:policy|procedure|sop|the\\s+manual)|return\\s+policy|warranty\\s+policy|"
                    + "restocking\\s+fee|warranty\\s+coverage|warranty\\s+period|eligible\\s+for\\s+return)\\b",
            Pattern.CASE_INSENSITIVE);

    /** Recognizes phrases indicating a supplier/vendor name claim, capturing until punctuation or clause end. */
    private static final Pattern SUPPLIER_CLAIM_PATTERN = Pattern.compile(
            "\\b(?:(?:from\\s+)?(?:supplier|vendor)(?:\\s*:\\s*|\\s+(?:is|was)\\s+|\\s+)|(?:supplied|provided)\\s+by\\s+)([^,.;!?\\r\\n]+)",
            Pattern.CASE_INSENSITIVE);

    /** Generic terms following "supplier" or "vendor" that describe financial concepts rather than supplier names. */
    private static final Set<String> GENERIC_SUPPLIER_STOPWORDS = Set.of(
            "payment", "payments", "invoice", "invoices", "account", "accounts",
            "balance", "balances", "bill", "bills", "order", "orders",
            "credit", "credits", "portal", "status", "record", "records",
            "contract", "terms", "name", "names", "id", "information", "details"
    );

    /** Clause boundaries or prepositions that delimit the end of a supplier name phrase. */
    private static final Set<String> SUPPLIER_TRAILING_DELIMITERS = Set.of(
            "on", "under", "for", "with", "at", "in", "to", "by", "is", "was", "has", "had",
            "supplied", "provided", "received", "incurred", "shipped", "invoiced", "dated"
    );

    /**
     * Splits local claim phrases at clause delimiters without splitting legal supplier
     * names such as "ABC Automotive Components and Spares Pvt Ltd".
     */
    private static final Pattern PHRASE_DELIMITER_PATTERN = Pattern.compile(
            "(?i)(?:,[\\s]|;|\\b(?:but|while|whereas|although|though|however|plus|with|under)\\b)"
                    + "|\\b[Aa]nd\\b(?!\\s+(?-i:[A-Z][a-z]+))");

    // -------------------------------------------------------------------------
    // Public result record
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Public entry points (overloads for backward compatibility)
    // -------------------------------------------------------------------------

    /** RAG-only validation. No ERP transaction facts. */
    public CitationValidationResult validateCitations(String response, List<RetrievedChunk> retrievedChunks) {
        return validateCitations(response, retrievedChunks, false, null);
    }

    public CitationValidationResult validateCitations(
            String response,
            List<RetrievedChunk> retrievedChunks,
            boolean allowErpTransactionFacts
    ) {
        return validateCitations(response, retrievedChunks, allowErpTransactionFacts, null);
    }

    /**
     * Five-argument overload: queryContext is intentionally ignored.
     * Only structured ERP toolResult may establish transaction facts.
     */
    public CitationValidationResult validateCitations(
            String response,
            List<RetrievedChunk> retrievedChunks,
            boolean allowErpTransactionFacts,
            Object toolResult,
            String queryContext
    ) {
        return validateCitations(response, retrievedChunks, allowErpTransactionFacts, toolResult);
    }

    public CitationValidationResult validateCitations(
            String response,
            List<RetrievedChunk> retrievedChunks,
            boolean allowErpTransactionFacts,
            Object toolResult
    ) {
        if (!StringUtils.hasText(response)) {
            return new CitationValidationResult(true, List.of(), List.of(), "", List.of());
        }

        List<RetrievedChunk> allowedChunks = retrievedChunks == null ? List.of() : List.copyOf(retrievedChunks);
        StructuredErpEvidence evidence = StructuredErpEvidence.from(toolResult);

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
                if (allowErpTransactionFacts) {
                    if (verifyErpTransactionClaims(segment, evidence, false, List.of())) {
                        accepted.add(segment);
                    } else {
                        unsupported.add(segment);
                    }
                } else {
                    unsupported.add(segment);
                }
                continue;
            }

            boolean segmentValid = true;
            Set<String> segmentMatched = new LinkedHashSet<>();
            List<RetrievedChunk> segmentMatchedChunks = new ArrayList<>();
            for (String tag : tags) {
                Optional<RetrievedChunk> chunk = findMatchingChunk(tag, allowedChunks);
                if (chunk.isPresent()) {
                    segmentMatched.add(tag);
                    segmentMatchedChunks.add(chunk.get());
                } else {
                    fabricated.add(tag);
                    segmentValid = false;
                }
            }

            // In hybrid mode, verify co-located transaction facts against structured ERP evidence,
            // while allowing legitimate cited RAG facts (amounts/dates) from matching policy chunks.
            if (segmentValid && allowErpTransactionFacts) {
                if (!verifyErpTransactionClaims(segment, evidence, true, segmentMatchedChunks)) {
                    segmentValid = false;
                }
            }

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

    // -------------------------------------------------------------------------
    // Core ERP transaction claim verifier
    // -------------------------------------------------------------------------

    private static boolean verifyErpTransactionClaims(
            String segment,
            StructuredErpEvidence evidence,
            boolean isCited,
            List<RetrievedChunk> citedChunks
    ) {
        // 1. Uncited segments must not state ungrounded policy rules
        if (!isCited && POLICY_CLAIM_PATTERN.matcher(segment).find()) {
            return false;
        }

        // Strip citation tags, percentages, and durations from the working copy
        String clean = segment.replaceAll("\\[Source:[^\\]]+\\]", " ");
        clean = clean.replaceAll("\\b\\d+(?:\\.\\d+)?\\s*%", " ");
        clean = clean.replaceAll("(?i)\\b\\d+\\s*(?:days?|weeks?|months?|years?|hours?)\\b", " ");

        // Check if multiple distinct purchase IDs are referenced in this segment
        Matcher purScan = PURCHASE_ID_PATTERN.matcher(clean);
        Set<String> distinctPurIds = new LinkedHashSet<>();
        while (purScan.find()) {
            distinctPurIds.add(purScan.group().toLowerCase(Locale.ROOT));
        }

        if (distinctPurIds.size() > 1) {
            // Multi-transaction sentence: split into per-transaction clauses
            List<String> clauses = splitMultiTransactionClauses(clean);
            if (clauses.isEmpty() || clauses.size() != distinctPurIds.size()) {
                return false; // Ambiguous attribution across multiple transactions -> fail closed
            }
            for (String clause : clauses) {
                if (!verifySingleTransactionClaims(clause, evidence, isCited, citedChunks)) {
                    return false;
                }
            }
            return true;
        }

        return verifySingleTransactionClaims(clean, evidence, isCited, citedChunks);
    }

    private static boolean verifySingleTransactionClaims(
            String clean,
            StructuredErpEvidence evidence,
            boolean isCited,
            List<RetrievedChunk> citedChunks
    ) {
        // Identify the purchase number mentioned in this clause (if any)
        Matcher purScan = PURCHASE_ID_PATTERN.matcher(clean);
        String referencedPurId = null;
        if (purScan.find()) {
            referencedPurId = purScan.group().toLowerCase(Locale.ROOT);
        }

        // Resolve the specific purchase record when a purchase ID is present,
        // or resolve via an invoice identifier, or use the single unambiguous record.
        Optional<StructuredErpEvidence.PurchaseRecord> specificRecord = Optional.empty();
        if (referencedPurId != null) {
            specificRecord = evidence.recordFor(referencedPurId);
        }

        // Check and resolve all invoice identifiers in this clause
        Matcher invScan = INVOICE_ID_PATTERN.matcher(clean);
        Set<String> invIds = new LinkedHashSet<>();
        while (invScan.find()) {
            invIds.add(invScan.group().toLowerCase(Locale.ROOT));
        }

        if (!invIds.isEmpty()) {
            for (String invId : invIds) {
                if (!evidence.hasData()
                        || (!evidence.invoiceNumbers().contains(invId) && !evidence.purchaseNumbers().contains(invId))) {
                    return false; // Fabricated or unverified invoice identifier
                }
                Optional<StructuredErpEvidence.PurchaseRecord> byInv = evidence.recordForInvoice(invId);
                if (byInv.isEmpty()) {
                    return false; // Ambiguous invoice ownership or unresolvable -> fail closed
                }
                if (specificRecord.isPresent() && !specificRecord.get().purchaseNumber().equals(byInv.get().purchaseNumber())) {
                    return false; // Invoices resolve to different purchases -> fail closed
                }
                specificRecord = byInv;
            }
        }

        // If still unresolved and exactly one transaction record exists overall, use that record unambiguously
        if (specificRecord.isEmpty() && evidence.recordCount() == 1) {
            specificRecord = Optional.of(evidence.allRecords().get(0));
        }

        // --- 2. Validate purchase identifiers ---
        Matcher purMatcher = PURCHASE_ID_PATTERN.matcher(clean);
        while (purMatcher.find()) {
            String purId = purMatcher.group().toLowerCase(Locale.ROOT);
            if (!evidence.hasData() || !evidence.purchaseNumbers().contains(purId)) {
                return false; // Fabricated or unverified purchase identifier
            }
        }

        // --- 3. Validate invoice identifiers ---
        Matcher invMatcher = INVOICE_ID_PATTERN.matcher(clean);
        while (invMatcher.find()) {
            String invId = invMatcher.group().toLowerCase(Locale.ROOT);
            if (!evidence.hasData() || (!evidence.invoiceNumbers().contains(invId) && !evidence.purchaseNumbers().contains(invId))) {
                return false; // Fabricated or unverified invoice identifier
            }
        }

        // --- 4. Validate ISO dates with claim-level attribution ---
        Matcher dateMatcher = DATE_PATTERN.matcher(clean);
        while (dateMatcher.find()) {
            String date = dateMatcher.group();
            int start = dateMatcher.start();
            int end = dateMatcher.end();
            String phrase = extractLocalPhrase(clean, start, end);

            boolean isPolicyContext = isPolicyContext(phrase) || phrase.contains("effective");
            boolean isTransactionContext = phrase.contains("purchas") || phrase.contains("ordered")
                    || phrase.contains("bought") || phrase.contains("dated") || phrase.contains("invoiced")
                    || phrase.contains("received") || phrase.contains("delivered")
                    || phrase.contains("pur-") || phrase.contains("inv-")
                    || (phrase.contains("on") && (phrase.contains("pur-") || phrase.contains("inv-")));

            if (isTransactionContext) {
                // Explicit ERP transaction claim (e.g. "was dated 2025-06-01", "purchased on 2026-11-12", "bought on")
                // Every date attributed to a transaction MUST match the ERP record.
                // A cited RAG policy chunk date can NEVER authorize a transaction date mismatch!
                if (specificRecord.isEmpty()) {
                    return false;
                }
                StructuredErpEvidence.PurchaseRecord rec = specificRecord.get();

                boolean isInvoiceDateClaim = phrase.contains("invoice date") || phrase.contains("invoiced on")
                        || phrase.contains("billing date") || phrase.contains("billed on")
                        || (phrase.contains("inv-") && (phrase.contains("dated") || phrase.contains("date")));
                boolean isReceivedDateClaim = phrase.contains("received on") || phrase.contains("delivery date")
                        || phrase.contains("delivered on") || phrase.contains("receipt date");
                boolean isPaymentDateClaim = (phrase.contains("paid on") || phrase.contains("payment date") || phrase.contains("settled on"))
                        && !phrase.contains("unpaid") && !phrase.contains("pending") && !phrase.contains("awaiting");
                boolean isPurchaseDateClaim = phrase.contains("purchased") || phrase.contains("purchase date")
                        || phrase.contains("ordered") || phrase.contains("order date")
                        || phrase.contains("bought") || (phrase.contains("pur-") && phrase.contains("dated"));

                if (isInvoiceDateClaim && !isPurchaseDateClaim) {
                    if (rec.hasInvoiceDateConflict() || rec.invoiceDates().isEmpty() || !rec.containsInvoiceDate(date)) {
                        return false;
                    }
                } else if (isReceivedDateClaim && !isPurchaseDateClaim) {
                    if (rec.hasReceivedDateConflict() || rec.receivedDates().isEmpty() || !rec.containsReceivedDate(date)) {
                        return false;
                    }
                } else if (isPaymentDateClaim && !isPurchaseDateClaim) {
                    if (rec.hasPaymentDateConflict() || rec.paymentDates().isEmpty() || !rec.containsPaymentDate(date)) {
                        return false;
                    }
                } else {
                    // Default transaction date (purchase date / order date / completed on / dated)
                    if (rec.hasPurchaseDateConflict() || rec.purchaseDates().isEmpty() || !rec.containsPurchaseDate(date)) {
                        return false;
                    }
                }

                if (isPolicyContext) {
                    boolean isRagSupportedDate = citedChunks.stream()
                            .anyMatch(chunk -> chunk.chunkText() != null && chunk.chunkText().contains(date));
                    if (!isRagSupportedDate) {
                        return false;
                    }
                }
            } else if (isPolicyContext) {
                // Explicit policy claim without transaction attribution (e.g. "under policy effective 2025-06-01")
                boolean isRagSupportedDate = citedChunks.stream()
                        .anyMatch(chunk -> chunk.chunkText() != null && chunk.chunkText().contains(date));
                if (!isRagSupportedDate) {
                    return false;
                }
            } else {
                // Neutral context: no explicit transaction-date or policy keywords in local phrase.
                // Fail closed unless an independently valid RAG citation supports a non-transaction
                // policy/general date claim. Never fall back to the union of ERP date categories.
                String lowerClean = clean.toLowerCase(Locale.ROOT);
                boolean isRagSupportedDate = isCited
                        && !lowerClean.contains("pur-")
                        && !lowerClean.contains("inv-")
                        && citedChunks.stream()
                                .anyMatch(chunk -> chunk.chunkText() != null && chunk.chunkText().contains(date));
                if (!isRagSupportedDate) {
                    return false;
                }
            }
        }

        // --- 5. Validate monetary amounts with claim-level attribution ---
        Matcher amtMatcher = AMOUNT_PATTERN.matcher(clean);
        while (amtMatcher.find()) {
            String raw = amtMatcher.group()
                    .replaceAll("[$,\u20b9]", "")
                    .replaceAll("(?i)(?:USD|INR|Rs\\.?)\\s*", "")
                    .trim();
            if (raw.isEmpty()) continue;
            try {
                double val = Double.parseDouble(raw);
                int start = amtMatcher.start();
                int end = amtMatcher.end();
                String phrase = extractLocalPhrase(clean, start, end);

                boolean isPolicy = isPolicyFeeContext(phrase);
                boolean isTransaction = isTransactionAmountContext(phrase);

                if (isTransaction) {
                    // Explicit ERP transaction claim (e.g. "purchased for ₹100", "total was ₹450", "cost", "price", "amount", "worth")
                    // Every amount attributed to a transaction MUST match the ERP record.
                    // A cited RAG policy chunk amount can NEVER authorize a transaction amount!
                    if (specificRecord.isEmpty()) {
                        return false;
                    }
                    StructuredErpEvidence.PurchaseRecord rec = specificRecord.get();

                    boolean isRefundClaim = phrase.contains("refund");
                    boolean isPaidClaim = (phrase.contains("paid") || phrase.contains("payment of") || phrase.contains("settled"))
                            && !phrase.contains("unpaid");
                    boolean isBalanceClaim = phrase.contains("balance") || phrase.contains("due") || phrase.contains("outstanding") || phrase.contains("remaining");
                    boolean isSubtotalClaim = phrase.contains("subtotal") || phrase.contains("sub-total") || phrase.contains("net");

                    if (isRefundClaim) {
                        if (rec.hasRefundConflict() || rec.refundAmounts().isEmpty() || !rec.containsRefund(val)) {
                            return false;
                        }
                    } else if (isPaidClaim) {
                        if (rec.hasPaidAmountConflict() || rec.paidAmounts().isEmpty() || !rec.containsPaidAmount(val)) {
                            return false;
                        }
                    } else if (isBalanceClaim) {
                        if (rec.hasBalanceConflict() || rec.balances().isEmpty() || !rec.containsBalance(val)) {
                            return false;
                        }
                    } else if (isSubtotalClaim) {
                        if (rec.hasSubtotalConflict() || rec.subtotals().isEmpty() || !rec.containsSubtotal(val)) {
                            return false;
                        }
                    } else {
                        // Grand total / total / cost / price / worth / generic transaction total
                        if (rec.hasGrandTotalConflict() || rec.grandTotals().isEmpty() || !rec.containsGrandTotal(val)) {
                            return false;
                        }
                    }

                    // If the same local phrase also claims this amount is a policy fee/charge,
                    // it must ALSO be supported by the cited policy chunk.
                    if (isPolicy) {
                        boolean isRagSupported = citedChunks.stream()
                                .anyMatch(chunk -> chunkContainsAmount(chunk.chunkText(), val));
                        if (!isRagSupported) {
                            return false;
                        }
                    }
                } else if (isPolicy) {
                    // Explicit policy claim without transaction attribution (e.g. "restocking fee of ₹100")
                    boolean isRagSupported = citedChunks.stream()
                            .anyMatch(chunk -> chunkContainsAmount(chunk.chunkText(), val));
                    if (!isRagSupported) {
                        return false; // Fabricated policy fee not in cited chunk
                    }
                } else {
                    // Neutral context: no explicit transaction-amount or policy keywords in local phrase.
                    // Fail closed unless an independently valid RAG citation supports a non-transaction
                    // policy/general amount claim. Never fall back to the union of ERP monetary categories.
                    String lowerClean = clean.toLowerCase(Locale.ROOT);
                    boolean isRagSupported = isCited
                            && !lowerClean.contains("pur-")
                            && !lowerClean.contains("inv-")
                            && citedChunks.stream()
                                    .anyMatch(chunk -> chunkContainsAmount(chunk.chunkText(), val));
                    if (!isRagSupported) {
                        return false;
                    }
                }
            } catch (NumberFormatException ignored) {}
        }

        // --- 6. Validate supplier claims (exact normalized entity matching) ---
        List<String> claimedSuppliers = extractClaimedSupplierNames(clean);
        for (String claimedSupplier : claimedSuppliers) {
            if (specificRecord.isPresent()) {
                StructuredErpEvidence.PurchaseRecord rec = specificRecord.get();
                if (rec.supplierNames().isEmpty() || !rec.containsSupplier(claimedSupplier)) {
                    return false; // Record has no supplier evidence, claimed supplier does not match, or conflict
                }
            } else {
                // Ambiguous transaction with multiple or zero records -> fail closed
                return false;
            }
        }

        // --- 7. Validate payment claims with negation and ambiguity handling ---
        PaymentClaimAnalysis paymentAnalysis = analyzePaymentClaims(clean);
        if (paymentAnalysis.isContradictory()) {
            return false; // Contradictory payment claims in the same sentence fail closed
        }

        if (paymentAnalysis.hasAnyPaymentClaim()) {
            if (specificRecord.isEmpty()) {
                return false; // Ambiguous transaction -> fail closed
            }
            StructuredErpEvidence.PurchaseRecord rec = specificRecord.get();
            if (rec.hasPaymentStatusConflict()) {
                return false; // Conflicting payment evidence in DB -> fail closed
            }
            PaymentStatus actualStatus = rec.paymentStatus();

            // Unknown or missing payment status cannot authorize any payment claim (positive or negative)
            if (actualStatus == PaymentStatus.UNKNOWN || actualStatus == PaymentStatus.MISSING) {
                return false; // Fail closed: no explicit known payment evidence
            }

            // 1. Positive claims: actualStatus MUST match the positive claim
            for (PaymentStatus positive : paymentAnalysis.positiveClaims()) {
                if (actualStatus != positive) {
                    return false;
                }
            }

            // 2. Negated claims: requires known status proving the negation, and actualStatus must not match the negated state
            for (PaymentStatus negated : paymentAnalysis.negatedClaims()) {
                if (actualStatus == negated) {
                    return false; // e.g. claimed "not settled", but actual is PAID!
                }
                if (!statusEstablishesNegation(actualStatus, negated)) {
                    return false;
                }
            }
        }

        return true;
    }

    private static boolean statusEstablishesNegation(PaymentStatus actual, PaymentStatus negated) {
        if (actual == PaymentStatus.UNKNOWN || actual == PaymentStatus.MISSING) {
            return false;
        }
        return switch (negated) {
            case PAID -> actual == PaymentStatus.UNPAID
                    || actual == PaymentStatus.PENDING
                    || actual == PaymentStatus.PARTIALLY_PAID
                    || actual == PaymentStatus.OVERDUE;
            case UNPAID -> actual == PaymentStatus.PAID
                    || actual == PaymentStatus.PARTIALLY_PAID;
            case OVERDUE -> actual == PaymentStatus.PAID;
            case PENDING -> actual == PaymentStatus.PAID
                    || actual == PaymentStatus.OVERDUE;
            case PARTIALLY_PAID -> actual == PaymentStatus.PAID
                    || actual == PaymentStatus.UNPAID;
            default -> false;
        };
    }

    private static String extractLocalPhrase(String text, int start, int end) {
        int left = 0;
        String before = text.substring(0, start);
        Matcher leftMatcher = PHRASE_DELIMITER_PATTERN.matcher(before);
        while (leftMatcher.find()) {
            left = leftMatcher.end();
        }

        int right = text.length();
        String after = text.substring(end);
        Matcher rightMatcher = PHRASE_DELIMITER_PATTERN.matcher(after);
        if (rightMatcher.find()) {
            right = end + rightMatcher.start();
        }

        return text.substring(left, right).toLowerCase(Locale.ROOT);
    }

    private static boolean isPolicyFeeContext(String phrase) {
        return phrase.contains("fee") || phrase.contains("restocking")
                || phrase.contains("penalty") || phrase.contains("deduction")
                || phrase.contains("policy");
    }

    private static boolean isPolicyContext(String phrase) {
        return isPolicyFeeContext(phrase) || phrase.contains("charge")
                || phrase.contains("discount") || phrase.contains("warranty")
                || phrase.contains("coverage");
    }

    private static boolean isTransactionAmountContext(String phrase) {
        return phrase.contains("purchased") || phrase.contains("ordered")
                || phrase.contains("grand total") || phrase.contains("subtotal")
                || phrase.contains("total") || phrase.contains("cost")
                || phrase.contains("price") || phrase.contains("invoice")
                || phrase.contains("bought") || phrase.contains("paid")
                || phrase.contains("balance") || phrase.contains("due")
                || phrase.contains("outstanding") || phrase.contains("refund")
                || phrase.contains("amount") || phrase.contains("worth")
                || phrase.contains("pur-") || phrase.contains("inv-");
    }

    static List<String> splitMultiTransactionClauses(String clean) {
        Matcher m = PURCHASE_ID_PATTERN.matcher(clean);
        List<Integer> startIndices = new ArrayList<>();
        while (m.find()) {
            startIndices.add(m.start());
        }
        if (startIndices.size() <= 1) {
            return List.of(clean);
        }

        // If there is preamble before the first purchase ID containing any transaction fact,
        // it cannot be unambiguously attributed to the first vs later purchases -> fail closed
        int firstStart = startIndices.get(0);
        String preamble = clean.substring(0, firstStart).trim();
        if (containsAnyTransactionFactClaim(preamble)) {
            return List.of();
        }

        List<String> clauses = new ArrayList<>();
        for (int i = 0; i < startIndices.size(); i++) {
            int start = startIndices.get(i);
            int end = (i + 1 < startIndices.size()) ? startIndices.get(i + 1) : clean.length();
            String clause = clean.substring(start, end).trim();
            clauses.add(clause);
        }
        return clauses;
    }

    static boolean containsAnyTransactionFactClaim(String text) {
        if (text.isBlank()) return false;
        if (AMOUNT_PATTERN.matcher(text).find()) return true;
        if (DATE_PATTERN.matcher(text).find()) return true;
        if (analyzePaymentClaims(text).hasAnyPaymentClaim()) return true;
        return !extractClaimedSupplierNames(text).isEmpty();
    }

    /** Helper to check whether a policy chunk text contains a specific monetary amount. */
    private static boolean chunkContainsAmount(String chunkText, double targetVal) {
        if (chunkText == null) return false;
        Matcher m = AMOUNT_PATTERN.matcher(chunkText);
        while (m.find()) {
            String raw = m.group()
                    .replaceAll("[$,\u20b9]", "")
                    .replaceAll("(?i)(?:USD|INR|Rs\\.?)\\s*", "")
                    .trim();
            if (raw.isEmpty()) continue;
            try {
                double v = Double.parseDouble(raw);
                if (Math.abs(v - targetVal) < 0.01) {
                    return true;
                }
            } catch (NumberFormatException ignored) {}
        }
        return false;
    }

    /**
     * Extracts claimed supplier/vendor names from text, discarding generic financial
     * phrases like "supplier payment", "supplier invoice", "vendor balance", etc.
     * Supports long legal names and stops at punctuation or clause delimiters.
     */
    static List<String> extractClaimedSupplierNames(String text) {
        List<String> suppliers = new ArrayList<>();
        Matcher matcher = SUPPLIER_CLAIM_PATTERN.matcher(text);
        while (matcher.find()) {
            String rawRemainder = matcher.group(1).trim();
            String[] tokens = rawRemainder.split("\\s+");
            if (tokens.length == 0 || tokens[0].isBlank()) {
                continue;
            }
            String firstWord = tokens[0].toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
            if (GENERIC_SUPPLIER_STOPWORDS.contains(firstWord)) {
                continue; // Generic phrase like "supplier payment", not an actual entity name
            }
            List<String> validTokens = new ArrayList<>();
            for (String token : tokens) {
                String cleanToken = token.replaceAll("[^A-Za-z0-9&]", "");
                if (cleanToken.isBlank()) continue;
                String lower = cleanToken.toLowerCase(Locale.ROOT);
                if (SUPPLIER_TRAILING_DELIMITERS.contains(lower)) {
                    break;
                }
                validTokens.add(cleanToken);
            }
            while (!validTokens.isEmpty()) {
                String last = validTokens.get(validTokens.size() - 1).toLowerCase(Locale.ROOT);
                if (last.equals("and") || last.equals("&")) {
                    validTokens.remove(validTokens.size() - 1);
                } else {
                    break;
                }
            }
            if (!validTokens.isEmpty()) {
                suppliers.add(String.join(" ", validTokens).trim());
            }
        }
        return suppliers;
    }

    /** Normalizes entity names (suppliers) by stripping non-alphanumeric punctuation and collapsing whitespace. */
    public static String normalizeEntityName(String name) {
        if (name == null) return "";
        return name.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9& ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    // -------------------------------------------------------------------------
    // Payment claim classification with negation & contradiction detection
    // -------------------------------------------------------------------------

    public record PaymentClaimAnalysis(
            Set<PaymentStatus> positiveClaims,
            Set<PaymentStatus> negatedClaims,
            boolean isContradictory
    ) {
        public boolean hasAnyPaymentClaim() {
            return !positiveClaims.isEmpty() || !negatedClaims.isEmpty() || isContradictory;
        }
    }

    private static final Pattern NEGATION_SETTLED_PAID = Pattern.compile(
            "\\b(?:is\\s+not|isn't|was\\s+not|wasn't|has\\s+not\\s+(?:yet\\s+)?been|hasn't\\s+(?:yet\\s+)?been|have\\s+not\\s+(?:yet\\s+)?been|haven't\\s+(?:yet\\s+)?been|has\\s+not\\s+yet\\s+been|not|never)\\s+(?:yet\\s+|currently\\s+|fully\\s+|completely\\s+)?(?:settled|paid|completed)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern NEGATION_UNPAID = Pattern.compile(
            "\\b(?:is\\s+not|isn't|was\\s+not|wasn't|not|never)\\s+(?:currently\\s+)?unpaid\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern NEGATION_PENDING = Pattern.compile(
            "\\b(?:is\\s+not|isn't|was\\s+not|wasn't|not|never)\\s+(?:currently\\s+)?(?:pending|awaiting\\s+payment)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern NEGATION_OVERDUE = Pattern.compile(
            "\\b(?:is\\s+not|isn't|was\\s+not|wasn't|not|never)\\s+(?:currently\\s+)?(?:overdue|past\\s+due)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern NEGATION_PARTIAL = Pattern.compile(
            "\\b(?:is\\s+not|isn't|was\\s+not|wasn't|not|never)\\s+(?:currently\\s+)?(?:partially\\s+paid|partial\\s+payment)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern POSITIVE_SETTLED_PAID = Pattern.compile(
            "\\b(?:fully\\s+paid|payment\\s+(?:was\\s+|is\\s+|has\\s+been\\s+)?settled|payment\\s+(?:is\\s+|was\\s+|has\\s+been\\s+)?completed|payment\\s+completed|has\\s+been\\s+paid|is\\s+paid|was\\s+paid|payment\\s+received|\\bsettled\\b)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern POSITIVE_UNPAID = Pattern.compile(
            "\\b(?:unpaid|has\\s+not\\s+been\\s+paid|not\\s+paid|payment\\s+failed|payment\\s+declined)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern POSITIVE_PENDING = Pattern.compile(
            "\\b(?:pending\\s+payment|payment\\s+(?:is\\s+)?pending|awaiting\\s+payment)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern POSITIVE_OVERDUE = Pattern.compile(
            "\\b(?:payment\\s+(?:is\\s+)?overdue|overdue\\s+payment|past\\s+due|\\boverdue\\b)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern POSITIVE_PARTIAL = Pattern.compile(
            "\\b(?:partially\\s+paid|partial\\s+payment|part-paid|part\\s+paid)\\b",
            Pattern.CASE_INSENSITIVE);

    public static PaymentClaimAnalysis analyzePaymentClaims(String text) {
        Set<PaymentStatus> positive = new LinkedHashSet<>();
        Set<PaymentStatus> negated = new LinkedHashSet<>();

        List<int[]> negPaidRanges = new ArrayList<>();
        List<int[]> negUnpaidRanges = new ArrayList<>();
        List<int[]> negPendingRanges = new ArrayList<>();
        List<int[]> negOverdueRanges = new ArrayList<>();
        List<int[]> negPartialRanges = new ArrayList<>();

        // Check negations first
        Matcher m = NEGATION_SETTLED_PAID.matcher(text);
        while (m.find()) {
            negated.add(PaymentStatus.PAID);
            negPaidRanges.add(new int[]{m.start(), m.end()});
        }
        m = NEGATION_UNPAID.matcher(text);
        while (m.find()) {
            negated.add(PaymentStatus.UNPAID);
            negUnpaidRanges.add(new int[]{m.start(), m.end()});
        }
        m = NEGATION_PENDING.matcher(text);
        while (m.find()) {
            negated.add(PaymentStatus.PENDING);
            negPendingRanges.add(new int[]{m.start(), m.end()});
        }
        m = NEGATION_OVERDUE.matcher(text);
        while (m.find()) {
            negated.add(PaymentStatus.OVERDUE);
            negOverdueRanges.add(new int[]{m.start(), m.end()});
        }
        m = NEGATION_PARTIAL.matcher(text);
        while (m.find()) {
            negated.add(PaymentStatus.PARTIALLY_PAID);
            negPartialRanges.add(new int[]{m.start(), m.end()});
        }

        // Check positive assertions (excluding ones that are inside corresponding negation ranges)
        m = POSITIVE_PARTIAL.matcher(text);
        while (m.find()) {
            if (!isInsideAny(m.start(), m.end(), negPartialRanges)) {
                positive.add(PaymentStatus.PARTIALLY_PAID);
            }
        }
        m = POSITIVE_UNPAID.matcher(text);
        while (m.find()) {
            if (!isInsideAny(m.start(), m.end(), negUnpaidRanges) && !isInsideAny(m.start(), m.end(), negPaidRanges)) {
                positive.add(PaymentStatus.UNPAID);
            }
        }
        m = POSITIVE_PENDING.matcher(text);
        while (m.find()) {
            if (!isInsideAny(m.start(), m.end(), negPendingRanges)) {
                positive.add(PaymentStatus.PENDING);
            }
        }
        m = POSITIVE_OVERDUE.matcher(text);
        while (m.find()) {
            if (!isInsideAny(m.start(), m.end(), negOverdueRanges)) {
                positive.add(PaymentStatus.OVERDUE);
            }
        }
        m = POSITIVE_SETTLED_PAID.matcher(text);
        while (m.find()) {
            if (!isInsideAny(m.start(), m.end(), negPaidRanges)
                    && !positive.contains(PaymentStatus.PARTIALLY_PAID)
                    && !positive.contains(PaymentStatus.UNPAID)) {
                positive.add(PaymentStatus.PAID);
            }
        }

        // Contradiction detection:
        // 1. Multiple positive claims of different statuses (e.g. paid and unpaid)
        // 2. A status is both asserted positively and negated (e.g. is paid and not paid)
        boolean isContradictory = positive.size() > 1;
        for (PaymentStatus p : positive) {
            if (negated.contains(p)) {
                isContradictory = true;
                break;
            }
        }

        return new PaymentClaimAnalysis(positive, negated, isContradictory);
    }

    private static boolean isInsideAny(int start, int end, List<int[]> ranges) {
        for (int[] r : ranges) {
            if (start >= r[0] && end <= r[1]) {
                return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Payment status enum
    // -------------------------------------------------------------------------

    /**
     * Canonical payment status categories derived from ERP status fields.
     */
    public enum PaymentStatus {
        /** Invoice/payment has been fully paid. */
        PAID,
        /** Explicitly recorded as not paid. */
        UNPAID,
        /** Partially paid, balance outstanding. */
        PARTIALLY_PAID,
        /** Payment is pending / awaiting action. */
        PENDING,
        /** Payment is past its due date. */
        OVERDUE,
        /** An explicit status was supplied, but it was unrecognised or conflicting (e.g. PROCESSING). */
        UNKNOWN,
        /** No payment status field was present in the ERP row. */
        MISSING;

        public boolean indicatesPaymentComplete() {
            return this == PAID;
        }

        public static PaymentStatus resolve(String rawStatus) {
            if (rawStatus == null) return MISSING;
            String s = rawStatus.trim().toLowerCase(Locale.ROOT)
                    .replace('-', '_').replace(' ', '_');
            return switch (s) {
                case "paid", "fully_paid", "payment_complete", "payment_completed" -> PAID;
                case "unpaid", "not_paid", "payment_failed", "payment_declined" -> UNPAID;
                case "partially_paid", "partial_payment", "part_paid" -> PARTIALLY_PAID;
                case "pending", "awaiting_payment", "pending_payment", "payment_pending" -> PENDING;
                case "overdue", "past_due", "payment_overdue" -> OVERDUE;
                default -> UNKNOWN;
            };
        }
    }

    // -------------------------------------------------------------------------
    // Structured ERP evidence model
    // -------------------------------------------------------------------------

    /**
     * Extracts structured ERP fields directly from tool execution results.
     *
     * <p>Transaction facts are stored per-record so that validation of one purchase
     * cannot be satisfied by evidence belonging to a different purchase.</p>
     */
    public static final class StructuredErpEvidence {

        /**
         * Immutable structured representation of one ERP purchase/transaction record.
         * All string values are normalized to lower-case for comparison.
         */
        public static final class PurchaseRecord {
            private final String purchaseNumber;         // normalized, e.g. "pur-0001"
            private final Set<String> invoiceNumbers;

            // Categorized dates
            private final Set<String> purchaseDates;
            private final Set<String> invoiceDates;
            private final Set<String> receivedDates;
            private final Set<String> paymentDates;
            private final Set<String> otherDates;
            private final Set<String> dates;             // all dates (union)

            // Categorized amounts
            private final Set<Double> grandTotals;
            private final Set<Double> subtotals;
            private final Set<Double> paidAmounts;
            private final Set<Double> balances;
            private final Set<Double> refundAmounts;
            private final Set<Double> otherAmounts;
            private final Set<Double> amounts;           // all amounts (union)

            private final Set<String> supplierNames;
            private final Set<String> customerNames;
            private final Set<String> productIdentifiers;
            private final Set<Double> quantities;
            private final PaymentStatus paymentStatus;   // resolved, never null
            private final Set<String> rawStatuses;       // kept for diagnostics

            // Conflict flags
            private final boolean hasPurchaseDateConflict;
            private final boolean hasInvoiceDateConflict;
            private final boolean hasReceivedDateConflict;
            private final boolean hasPaymentDateConflict;
            private final boolean hasDateConflict;

            private final boolean hasGrandTotalConflict;
            private final boolean hasSubtotalConflict;
            private final boolean hasPaidAmountConflict;
            private final boolean hasBalanceConflict;
            private final boolean hasRefundConflict;
            private final boolean hasAmountConflict;

            private final boolean hasSupplierConflict;
            private final boolean hasPaymentStatusConflict;

            public PurchaseRecord(
                    String purchaseNumber,
                    Set<String> invoiceNumbers,
                    Set<String> purchaseDates,
                    Set<String> invoiceDates,
                    Set<String> receivedDates,
                    Set<String> paymentDates,
                    Set<String> otherDates,
                    Set<Double> grandTotals,
                    Set<Double> subtotals,
                    Set<Double> paidAmounts,
                    Set<Double> balances,
                    Set<Double> refundAmounts,
                    Set<Double> otherAmounts,
                    Set<String> supplierNames,
                    Set<String> customerNames,
                    Set<String> productIdentifiers,
                    Set<Double> quantities,
                    PaymentStatus paymentStatus,
                    Set<String> rawStatuses,
                    boolean hasPurchaseDateConflict,
                    boolean hasInvoiceDateConflict,
                    boolean hasReceivedDateConflict,
                    boolean hasPaymentDateConflict,
                    boolean hasGrandTotalConflict,
                    boolean hasSubtotalConflict,
                    boolean hasPaidAmountConflict,
                    boolean hasBalanceConflict,
                    boolean hasRefundConflict,
                    boolean hasSupplierConflict,
                    boolean hasPaymentStatusConflict
            ) {
                this.purchaseNumber          = purchaseNumber;
                this.invoiceNumbers          = Collections.unmodifiableSet(new LinkedHashSet<>(invoiceNumbers));
                this.purchaseDates           = Collections.unmodifiableSet(new LinkedHashSet<>(purchaseDates));
                this.invoiceDates            = Collections.unmodifiableSet(new LinkedHashSet<>(invoiceDates));
                this.receivedDates           = Collections.unmodifiableSet(new LinkedHashSet<>(receivedDates));
                this.paymentDates            = Collections.unmodifiableSet(new LinkedHashSet<>(paymentDates));
                this.otherDates              = Collections.unmodifiableSet(new LinkedHashSet<>(otherDates));

                Set<String> allD = new LinkedHashSet<>();
                allD.addAll(purchaseDates);
                allD.addAll(invoiceDates);
                allD.addAll(receivedDates);
                allD.addAll(paymentDates);
                allD.addAll(otherDates);
                this.dates                   = Collections.unmodifiableSet(allD);

                this.grandTotals             = Collections.unmodifiableSet(new LinkedHashSet<>(grandTotals));
                this.subtotals               = Collections.unmodifiableSet(new LinkedHashSet<>(subtotals));
                this.paidAmounts             = Collections.unmodifiableSet(new LinkedHashSet<>(paidAmounts));
                this.balances                = Collections.unmodifiableSet(new LinkedHashSet<>(balances));
                this.refundAmounts           = Collections.unmodifiableSet(new LinkedHashSet<>(refundAmounts));
                this.otherAmounts            = Collections.unmodifiableSet(new LinkedHashSet<>(otherAmounts));

                Set<Double> allA = new LinkedHashSet<>();
                allA.addAll(grandTotals);
                allA.addAll(subtotals);
                allA.addAll(paidAmounts);
                allA.addAll(balances);
                allA.addAll(refundAmounts);
                allA.addAll(otherAmounts);
                this.amounts                 = Collections.unmodifiableSet(allA);

                this.supplierNames           = Collections.unmodifiableSet(new LinkedHashSet<>(supplierNames));
                this.customerNames           = Collections.unmodifiableSet(new LinkedHashSet<>(customerNames));
                this.productIdentifiers      = Collections.unmodifiableSet(new LinkedHashSet<>(productIdentifiers));
                this.quantities              = Collections.unmodifiableSet(new LinkedHashSet<>(quantities));
                this.paymentStatus           = paymentStatus;
                this.rawStatuses             = Collections.unmodifiableSet(new LinkedHashSet<>(rawStatuses));

                this.hasPurchaseDateConflict = hasPurchaseDateConflict || (this.purchaseDates.size() > 1);
                this.hasInvoiceDateConflict  = hasInvoiceDateConflict || (this.invoiceDates.size() > 1);
                this.hasReceivedDateConflict = hasReceivedDateConflict || (this.receivedDates.size() > 1);
                this.hasPaymentDateConflict  = hasPaymentDateConflict || (this.paymentDates.size() > 1);
                this.hasDateConflict         = this.hasPurchaseDateConflict || this.hasInvoiceDateConflict
                        || this.hasReceivedDateConflict || this.hasPaymentDateConflict;

                this.hasGrandTotalConflict   = hasGrandTotalConflict || hasAmountConflict(this.grandTotals);
                this.hasSubtotalConflict     = hasSubtotalConflict || hasAmountConflict(this.subtotals);
                this.hasPaidAmountConflict   = hasPaidAmountConflict || hasAmountConflict(this.paidAmounts);
                this.hasBalanceConflict      = hasBalanceConflict || hasAmountConflict(this.balances);
                this.hasRefundConflict       = hasRefundConflict || hasAmountConflict(this.refundAmounts);
                this.hasAmountConflict       = this.hasGrandTotalConflict || this.hasSubtotalConflict
                        || this.hasPaidAmountConflict || this.hasBalanceConflict || this.hasRefundConflict;

                this.hasSupplierConflict     = hasSupplierConflict;
                this.hasPaymentStatusConflict = hasPaymentStatusConflict;
            }

            PurchaseRecord(
                    String purchaseNumber,
                    Set<String> invoiceNumbers,
                    Set<String> dates,
                    Set<Double> amounts,
                    Set<String> supplierNames,
                    Set<String> customerNames,
                    Set<String> productIdentifiers,
                    Set<Double> quantities,
                    PaymentStatus paymentStatus,
                    Set<String> rawStatuses
            ) {
                this(purchaseNumber, invoiceNumbers, dates, Set.of(), Set.of(), Set.of(), Set.of(),
                        amounts, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(),
                        supplierNames, customerNames, productIdentifiers, quantities, paymentStatus, rawStatuses,
                        false, false, false, false, false, false, false, false, false, false, false);
            }

            public String purchaseNumber()               { return purchaseNumber; }
            public Set<String> invoiceNumbers()          { return invoiceNumbers; }

            public Set<String> purchaseDates()           { return purchaseDates; }
            public Set<String> invoiceDates()            { return invoiceDates; }
            public Set<String> receivedDates()           { return receivedDates; }
            public Set<String> paymentDates()            { return paymentDates; }
            public Set<String> otherDates()              { return otherDates; }
            public Set<String> dates()                   { return dates; }

            public Set<Double> grandTotals()             { return grandTotals; }
            public Set<Double> subtotals()               { return subtotals; }
            public Set<Double> paidAmounts()             { return paidAmounts; }
            public Set<Double> balances()                { return balances; }
            public Set<Double> refundAmounts()           { return refundAmounts; }
            public Set<Double> otherAmounts()            { return otherAmounts; }
            public Set<Double> amounts()                 { return amounts; }

            public Set<String> supplierNames()           { return supplierNames; }
            public Set<String> customerNames()           { return customerNames; }
            public Set<String> productIdentifiers()      { return productIdentifiers; }
            public Set<Double> quantities()              { return quantities; }
            public PaymentStatus paymentStatus()         { return paymentStatus; }
            public Set<String> rawStatuses()             { return rawStatuses; }

            public boolean hasPurchaseDateConflict()     { return hasPurchaseDateConflict; }
            public boolean hasInvoiceDateConflict()      { return hasInvoiceDateConflict; }
            public boolean hasReceivedDateConflict()     { return hasReceivedDateConflict; }
            public boolean hasPaymentDateConflict()      { return hasPaymentDateConflict; }
            public boolean hasDateConflict()             { return hasDateConflict; }

            public boolean hasGrandTotalConflict()       { return hasGrandTotalConflict; }
            public boolean hasSubtotalConflict()         { return hasSubtotalConflict; }
            public boolean hasPaidAmountConflict()       { return hasPaidAmountConflict; }
            public boolean hasBalanceConflict()          { return hasBalanceConflict; }
            public boolean hasRefundConflict()           { return hasRefundConflict; }
            public boolean hasAmountConflict()           { return hasAmountConflict; }

            public boolean hasSupplierConflict()         { return hasSupplierConflict; }
            public boolean hasPaymentStatusConflict()    { return hasPaymentStatusConflict; }

            public boolean containsPurchaseDate(String date) {
                if (hasPurchaseDateConflict) return false;
                return purchaseDates.contains(date);
            }

            public boolean containsInvoiceDate(String date) {
                if (hasInvoiceDateConflict) return false;
                return invoiceDates.contains(date);
            }

            public boolean containsReceivedDate(String date) {
                if (hasReceivedDateConflict) return false;
                return receivedDates.contains(date);
            }

            public boolean containsPaymentDate(String date) {
                if (hasPaymentDateConflict) return false;
                return paymentDates.contains(date);
            }

            public boolean containsDate(String date) {
                if (hasDateConflict) return false;
                return dates.contains(date);
            }

            public boolean containsGrandTotal(double val) {
                if (hasGrandTotalConflict) return false;
                return grandTotals.stream().anyMatch(a -> Math.abs(a - val) < 0.01);
            }

            public boolean containsSubtotal(double val) {
                if (hasSubtotalConflict) return false;
                return subtotals.stream().anyMatch(a -> Math.abs(a - val) < 0.01);
            }

            public boolean containsPaidAmount(double val) {
                if (hasPaidAmountConflict) return false;
                return paidAmounts.stream().anyMatch(a -> Math.abs(a - val) < 0.01);
            }

            public boolean containsBalance(double val) {
                if (hasBalanceConflict) return false;
                return balances.stream().anyMatch(a -> Math.abs(a - val) < 0.01);
            }

            public boolean containsRefund(double val) {
                if (hasRefundConflict) return false;
                return refundAmounts.stream().anyMatch(a -> Math.abs(a - val) < 0.01);
            }

            public boolean containsAmount(double val) {
                if (hasAmountConflict) return false;
                return amounts.stream().anyMatch(a -> Math.abs(a - val) < 0.01);
            }

            public boolean containsSupplier(String claimed) {
                if (hasSupplierConflict) {
                    return false;
                }
                String normClaimed = normalizeEntityName(claimed);
                if (normClaimed.isBlank()) return false;
                return supplierNames.stream()
                        .map(CitationValidator::normalizeEntityName)
                        .anyMatch(s -> s.equals(normClaimed));
            }

            /**
             * Merges this record with another record for the same purchase.
             * Preserves all valid fields, but tracks field-level contradictions
             * (dates, totals, suppliers, and payment statuses) to fail closed.
             */
            public PurchaseRecord merge(PurchaseRecord other) {
                Set<String> mergedInvoices = new LinkedHashSet<>(this.invoiceNumbers);
                mergedInvoices.addAll(other.invoiceNumbers);

                Set<String> mergedCustomers = new LinkedHashSet<>(this.customerNames);
                mergedCustomers.addAll(other.customerNames);

                Set<String> mergedProducts = new LinkedHashSet<>(this.productIdentifiers);
                mergedProducts.addAll(other.productIdentifiers);

                Set<Double> mergedQuantities = new LinkedHashSet<>(this.quantities);
                mergedQuantities.addAll(other.quantities);

                Set<String> mergedRawStatuses = new LinkedHashSet<>(this.rawStatuses);
                mergedRawStatuses.addAll(other.rawStatuses);

                // Date merges & conflict detection
                boolean purchaseDateConflict = this.hasPurchaseDateConflict || other.hasPurchaseDateConflict
                        || (!this.purchaseDates.isEmpty() && !other.purchaseDates.isEmpty() && !this.purchaseDates.equals(other.purchaseDates));
                Set<String> mergedPurchaseDates = new LinkedHashSet<>(this.purchaseDates);
                mergedPurchaseDates.addAll(other.purchaseDates);

                boolean invoiceDateConflict = this.hasInvoiceDateConflict || other.hasInvoiceDateConflict
                        || (!this.invoiceDates.isEmpty() && !other.invoiceDates.isEmpty() && !this.invoiceDates.equals(other.invoiceDates));
                Set<String> mergedInvoiceDates = new LinkedHashSet<>(this.invoiceDates);
                mergedInvoiceDates.addAll(other.invoiceDates);

                boolean receivedDateConflict = this.hasReceivedDateConflict || other.hasReceivedDateConflict
                        || (!this.receivedDates.isEmpty() && !other.receivedDates.isEmpty() && !this.receivedDates.equals(other.receivedDates));
                Set<String> mergedReceivedDates = new LinkedHashSet<>(this.receivedDates);
                mergedReceivedDates.addAll(other.receivedDates);

                boolean paymentDateConflict = this.hasPaymentDateConflict || other.hasPaymentDateConflict
                        || (!this.paymentDates.isEmpty() && !other.paymentDates.isEmpty() && !this.paymentDates.equals(other.paymentDates));
                Set<String> mergedPaymentDates = new LinkedHashSet<>(this.paymentDates);
                mergedPaymentDates.addAll(other.paymentDates);

                Set<String> mergedOtherDates = new LinkedHashSet<>(this.otherDates);
                mergedOtherDates.addAll(other.otherDates);

                // Monetary merges & conflict detection
                boolean grandTotalConflict = this.hasGrandTotalConflict || other.hasGrandTotalConflict
                        || (!this.grandTotals.isEmpty() && !other.grandTotals.isEmpty() && hasAmountDifference(this.grandTotals, other.grandTotals));
                Set<Double> mergedGrandTotals = new LinkedHashSet<>(this.grandTotals);
                mergedGrandTotals.addAll(other.grandTotals);

                boolean subtotalConflict = this.hasSubtotalConflict || other.hasSubtotalConflict
                        || (!this.subtotals.isEmpty() && !other.subtotals.isEmpty() && hasAmountDifference(this.subtotals, other.subtotals));
                Set<Double> mergedSubtotals = new LinkedHashSet<>(this.subtotals);
                mergedSubtotals.addAll(other.subtotals);

                boolean paidAmountConflict = this.hasPaidAmountConflict || other.hasPaidAmountConflict
                        || (!this.paidAmounts.isEmpty() && !other.paidAmounts.isEmpty() && hasAmountDifference(this.paidAmounts, other.paidAmounts));
                Set<Double> mergedPaidAmounts = new LinkedHashSet<>(this.paidAmounts);
                mergedPaidAmounts.addAll(other.paidAmounts);

                boolean balanceConflict = this.hasBalanceConflict || other.hasBalanceConflict
                        || (!this.balances.isEmpty() && !other.balances.isEmpty() && hasAmountDifference(this.balances, other.balances));
                Set<Double> mergedBalances = new LinkedHashSet<>(this.balances);
                mergedBalances.addAll(other.balances);

                boolean refundConflict = this.hasRefundConflict || other.hasRefundConflict
                        || (!this.refundAmounts.isEmpty() && !other.refundAmounts.isEmpty() && hasAmountDifference(this.refundAmounts, other.refundAmounts));
                Set<Double> mergedRefundAmounts = new LinkedHashSet<>(this.refundAmounts);
                mergedRefundAmounts.addAll(other.refundAmounts);

                Set<Double> mergedOtherAmounts = new LinkedHashSet<>(this.otherAmounts);
                mergedOtherAmounts.addAll(other.otherAmounts);

                // Supplier conflict detection
                boolean supplierConflict = this.hasSupplierConflict || other.hasSupplierConflict;
                if (!this.supplierNames.isEmpty() && !other.supplierNames.isEmpty()) {
                    boolean allMatch = this.supplierNames.size() == other.supplierNames.size()
                            && this.supplierNames.stream().allMatch(s -> other.supplierNames.stream()
                                    .anyMatch(o -> normalizeEntityName(s).equals(normalizeEntityName(o))));
                    if (!allMatch) {
                        supplierConflict = true;
                    }
                }
                Set<String> mergedSuppliers = new LinkedHashSet<>(this.supplierNames);
                mergedSuppliers.addAll(other.supplierNames);

                // Payment status conflict detection
                boolean payConflict = this.hasPaymentStatusConflict || other.hasPaymentStatusConflict;
                PaymentStatus mergedPaymentStatus;
                if (this.paymentStatus == PaymentStatus.MISSING) {
                    mergedPaymentStatus = other.paymentStatus;
                } else if (other.paymentStatus == PaymentStatus.MISSING) {
                    mergedPaymentStatus = this.paymentStatus;
                } else if (this.paymentStatus == other.paymentStatus && this.paymentStatus != PaymentStatus.UNKNOWN) {
                    mergedPaymentStatus = this.paymentStatus;
                } else {
                    payConflict = true;
                    mergedPaymentStatus = PaymentStatus.UNKNOWN;
                }

                return new PurchaseRecord(
                        this.purchaseNumber,
                        mergedInvoices,
                        mergedPurchaseDates,
                        mergedInvoiceDates,
                        mergedReceivedDates,
                        mergedPaymentDates,
                        mergedOtherDates,
                        mergedGrandTotals,
                        mergedSubtotals,
                        mergedPaidAmounts,
                        mergedBalances,
                        mergedRefundAmounts,
                        mergedOtherAmounts,
                        mergedSuppliers,
                        mergedCustomers,
                        mergedProducts,
                        mergedQuantities,
                        mergedPaymentStatus,
                        mergedRawStatuses,
                        purchaseDateConflict,
                        invoiceDateConflict,
                        receivedDateConflict,
                        paymentDateConflict,
                        grandTotalConflict,
                        subtotalConflict,
                        paidAmountConflict,
                        balanceConflict,
                        refundConflict,
                        supplierConflict,
                        payConflict
                );
            }

            private static boolean hasAmountConflict(Set<Double> amounts) {
                if (amounts.size() <= 1) return false;
                Double first = null;
                for (Double a : amounts) {
                    if (first == null) {
                        first = a;
                    } else if (Math.abs(first - a) >= 0.01) {
                        return true;
                    }
                }
                return false;
            }

            private static boolean hasAmountDifference(Set<Double> s1, Set<Double> s2) {
                if (s1.isEmpty() || s2.isEmpty()) return false;
                for (Double a : s1) {
                    for (Double b : s2) {
                        if (Math.abs(a - b) >= 0.01) {
                            return true;
                        }
                    }
                }
                return false;
            }
        }

        // Per-record store keyed by normalized purchase number
        private final Map<String, PurchaseRecord> records = new LinkedHashMap<>();

        // Secondary index: normalized invoice number -> purchase number
        private final Map<String, String> invoiceToPurchase = new LinkedHashMap<>();

        // Invoices with ambiguous ownership (associated with multiple distinct purchases)
        private final Set<String> ambiguousInvoices = new LinkedHashSet<>();

        // Global union sets (derived from all records); kept for identifier discovery and legacy accessors
        private final Set<String> globalPurchaseNumbers  = new LinkedHashSet<>();
        private final Set<String> globalInvoiceNumbers   = new LinkedHashSet<>();
        private final Set<String> globalDates            = new LinkedHashSet<>();
        private final Set<Double> globalAmounts          = new LinkedHashSet<>();
        private final Set<Double> globalQuantities       = new LinkedHashSet<>();
        private final Set<String> globalSupplierNames    = new LinkedHashSet<>();
        private final Set<String> globalCustomerNames    = new LinkedHashSet<>();
        private final Set<String> globalProductIds       = new LinkedHashSet<>();
        private final Set<String> globalStatuses         = new LinkedHashSet<>();

        private boolean hasData = false;

        public static StructuredErpEvidence from(Object toolResult) {
            StructuredErpEvidence evidence = new StructuredErpEvidence();
            if (toolResult != null) {
                evidence.extract(toolResult);
            }
            return evidence;
        }

        private void extract(Object obj) {
            if (obj == null) return;
            if (obj instanceof Map<?, ?> map) {
                extractFromMap(map);
            } else if (obj instanceof Iterable<?> iter) {
                for (Object item : iter) {
                    extract(item);
                }
            }
        }

        private void extractFromMap(Map<?, ?> map) {
            if (map.isEmpty()) return;
            hasData = true;

            String purchaseNumber         = null;
            Set<String> invoiceNums       = new LinkedHashSet<>();
            Set<String> purchaseDates     = new LinkedHashSet<>();
            Set<String> invoiceDates      = new LinkedHashSet<>();
            Set<String> receivedDates     = new LinkedHashSet<>();
            Set<String> paymentDates      = new LinkedHashSet<>();
            Set<String> otherDates        = new LinkedHashSet<>();

            Set<Double> grandTotals       = new LinkedHashSet<>();
            Set<Double> subtotals         = new LinkedHashSet<>();
            Set<Double> paidAmounts       = new LinkedHashSet<>();
            Set<Double> balances          = new LinkedHashSet<>();
            Set<Double> refundAmounts     = new LinkedHashSet<>();
            Set<Double> otherAmounts      = new LinkedHashSet<>();

            Set<Double> quantities        = new LinkedHashSet<>();
            Set<String> supplierNames     = new LinkedHashSet<>();
            Set<String> customerNames     = new LinkedHashSet<>();
            Set<String> productIds        = new LinkedHashSet<>();
            Set<String> rawStatuses       = new LinkedHashSet<>();

            Set<PaymentStatus> candidatePaymentStatuses = new LinkedHashSet<>();
            boolean paymentStatusConflict = false;

            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) continue;
                String key = entry.getKey().toString().trim().toLowerCase(Locale.ROOT);
                Object val = entry.getValue();

                if (val instanceof Map || val instanceof Iterable) {
                    extract(val);
                    continue;
                }

                String strVal  = val.toString().trim();
                if (strVal.isEmpty()) continue;
                String lowerVal = strVal.toLowerCase(Locale.ROOT);

                // --- Identifiers ---
                if (key.contains("purchase_number") || key.equals("purchasenumber")
                        || key.equals("purchase_id") || key.equals("purchase_no")) {
                    purchaseNumber = lowerVal;
                    globalPurchaseNumbers.add(lowerVal);
                } else if (key.contains("supplier_invoice_no") || key.contains("invoice_number")
                        || key.contains("invoice_no") || key.equals("invoice")) {
                    invoiceNums.add(lowerVal);
                    globalInvoiceNumbers.add(lowerVal);
                } else if (key.contains("product_code") || key.contains("part_no")
                        || key.contains("part_number") || key.equals("sku")) {
                    productIds.add(lowerVal);
                    globalProductIds.add(lowerVal);
                } else if (key.contains("supplier_name") || key.contains("supplier")
                        || key.contains("vendor_name") || key.contains("vendor")) {
                    supplierNames.add(lowerVal);
                    globalSupplierNames.add(lowerVal);
                } else if (key.contains("customer_name") || key.contains("customer")) {
                    customerNames.add(lowerVal);
                    globalCustomerNames.add(lowerVal);
                }

                // --- Status handling ---
                if (isLifecycleStatusKey(key)) {
                    // Lifecycle status (invoice_status, order_status, receipt_status, delivery_status, etc.)
                    // Intentionally excluded from payment status validation
                    rawStatuses.add(lowerVal);
                    globalStatuses.add(lowerVal);
                } else if (isExplicitPaymentKey(key)) {
                    rawStatuses.add(lowerVal);
                    globalStatuses.add(lowerVal);
                    PaymentStatus ps = PaymentStatus.resolve(lowerVal);
                    candidatePaymentStatuses.add(ps);
                } else if (key.equals("status")) {
                    rawStatuses.add(lowerVal);
                    globalStatuses.add(lowerVal);
                    PaymentStatus ps = PaymentStatus.resolve(lowerVal);
                    // Generic 'status' field: only accept if value is an explicit payment status, NOT lifecycle UNKNOWN
                    if (ps != PaymentStatus.UNKNOWN && ps != PaymentStatus.MISSING) {
                        candidatePaymentStatuses.add(ps);
                    }
                } else if (key.contains("status")) {
                    rawStatuses.add(lowerVal);
                    globalStatuses.add(lowerVal);
                }

                // --- Dates (categorized) ---
                Matcher m = DATE_PATTERN.matcher(strVal);
                while (m.find()) {
                    String d = m.group();
                    globalDates.add(d);
                    if (isPurchaseDateKey(key)) {
                        purchaseDates.add(d);
                    } else if (isInvoiceDateKey(key)) {
                        invoiceDates.add(d);
                    } else if (isReceivedDateKey(key)) {
                        receivedDates.add(d);
                    } else if (isPaymentDateKey(key)) {
                        paymentDates.add(d);
                    } else {
                        otherDates.add(d);
                    }
                }

                // --- Monetary amounts and quantities (categorized) ---
                if (key.contains("quantity") || key.contains("qty")) {
                    Double d = parseDouble(val);
                    if (d != null) { quantities.add(d); globalQuantities.add(d); }
                } else {
                    Double d = parseDouble(val);
                    if (d != null) {
                        globalAmounts.add(d);
                        if (isRefundAmountKey(key)) {
                            refundAmounts.add(d);
                        } else if (isPaidAmountKey(key)) {
                            paidAmounts.add(d);
                        } else if (isBalanceAmountKey(key)) {
                            balances.add(d);
                        } else if (isSubtotalAmountKey(key)) {
                            subtotals.add(d);
                        } else if (isGrandTotalAmountKey(key)) {
                            grandTotals.add(d);
                        } else {
                            otherAmounts.add(d);
                        }
                    }
                }
            }

            // Resolve payment status order-independently
            PaymentStatus resolvedStatus;
            if (candidatePaymentStatuses.isEmpty()) {
                resolvedStatus = PaymentStatus.MISSING;
            } else if (candidatePaymentStatuses.size() == 1) {
                resolvedStatus = candidatePaymentStatuses.iterator().next();
            } else {
                paymentStatusConflict = true;
                resolvedStatus = PaymentStatus.UNKNOWN;
            }

            PurchaseRecord rec = new PurchaseRecord(
                    purchaseNumber != null ? purchaseNumber : (!invoiceNums.isEmpty() ? invoiceNums.iterator().next() : "erp-record-" + (records.size() + 1)),
                    invoiceNums,
                    purchaseDates, invoiceDates, receivedDates, paymentDates, otherDates,
                    grandTotals, subtotals, paidAmounts, balances, refundAmounts, otherAmounts,
                    supplierNames, customerNames, productIds, quantities,
                    resolvedStatus, rawStatuses,
                    false, false, false, false,
                    false, false, false, false, false,
                    false, paymentStatusConflict
            );

            if (purchaseNumber != null) {
                records.merge(purchaseNumber, rec, PurchaseRecord::merge);
                registerInvoices(invoiceNums, purchaseNumber);
            } else if (!invoiceNums.isEmpty()) {
                String invKey = invoiceNums.iterator().next();
                records.merge(invKey, rec, PurchaseRecord::merge);
                registerInvoices(invoiceNums, invKey);
            } else if (!rec.amounts().isEmpty() || !rec.dates().isEmpty() || !supplierNames.isEmpty()
                    || !customerNames.isEmpty() || !productIds.isEmpty() || !quantities.isEmpty()) {
                records.put(rec.purchaseNumber(), rec);
            }
        }

        private static boolean isPurchaseDateKey(String key) {
            return key.equals("purchase_date") || key.equals("purchasedate")
                    || key.equals("order_date") || key.equals("orderdate")
                    || key.equals("purchased_at") || key.equals("ordered_at");
        }

        private static boolean isInvoiceDateKey(String key) {
            return key.equals("invoice_date") || key.equals("invoicedate")
                    || key.equals("supplier_invoice_date") || key.equals("supplierinvoicedate")
                    || key.equals("invoiced_at") || key.equals("billing_date") || key.equals("billed_at");
        }

        private static boolean isReceivedDateKey(String key) {
            return key.equals("received_date") || key.equals("receiveddate")
                    || key.equals("received_at") || key.equals("receipt_date")
                    || key.equals("delivery_date") || key.equals("delivered_at");
        }

        private static boolean isPaymentDateKey(String key) {
            return key.equals("payment_date") || key.equals("paymentdate")
                    || key.equals("paid_at") || key.equals("paid_date");
        }

        private static boolean isRefundAmountKey(String key) {
            return key.equals("refund_amount") || key.equals("refundamount")
                    || key.equals("refunded_amount") || key.equals("refundedamount")
                    || key.equals("total_refund") || key.equals("refund");
        }

        private static boolean isPaidAmountKey(String key) {
            if (key.contains("unpaid")) return false;
            return key.equals("paid_amount") || key.equals("paidamount")
                    || key.equals("amount_paid") || key.equals("amountpaid")
                    || key.equals("payment_amount") || key.equals("paymentamount")
                    || key.equals("total_paid") || key.equals("totalpaid")
                    || key.equals("paid");
        }

        private static final Set<String> BALANCE_AMOUNT_KEYS = Set.of(
                "balance",
                "balance_amount", "balanceamount",
                "amount_due", "amountdue",
                "due_amount", "dueamount",
                "outstanding_balance", "outstandingbalance",
                "outstanding_amount", "outstandingamount",
                "current_balance", "currentbalance",
                "remaining_balance", "remainingbalance",
                "balance_due", "balancedue",
                "outstanding"
        );

        private static boolean isBalanceAmountKey(String key) {
            return BALANCE_AMOUNT_KEYS.contains(key);
        }

        private static boolean isSubtotalAmountKey(String key) {
            return key.equals("subtotal") || key.equals("sub_total")
                    || key.equals("net_amount") || key.equals("netamount");
        }

        private static boolean isGrandTotalAmountKey(String key) {
            return key.equals("grand_total") || key.equals("grandtotal")
                    || key.equals("total_amount") || key.equals("totalamount")
                    || key.equals("order_total") || key.equals("ordertotal")
                    || key.equals("final_amount") || key.equals("finalamount")
                    || key.equals("invoice_amount") || key.equals("invoiceamount")
                    || key.equals("total");
        }

        private static boolean isLifecycleStatusKey(String key) {
            return key.contains("invoice_status") || key.equals("invoicestatus")
                    || key.contains("order_status") || key.equals("orderstatus")
                    || key.contains("receipt_status") || key.equals("receiptstatus")
                    || key.contains("delivery_status") || key.equals("deliverystatus")
                    || key.contains("shipping_status") || key.equals("shippingstatus")
                    || key.contains("fulfillment_status") || key.equals("fulfillmentstatus")
                    || key.contains("item_status") || key.equals("itemstatus");
        }

        private static boolean isExplicitPaymentKey(String key) {
            return key.equals("payment_status") || key.equals("paymentstatus")
                    || key.equals("payment_state") || key.equals("paymentstate");
        }

        private void registerInvoices(Set<String> invoiceNums, String ownerPurId) {
            for (String inv : invoiceNums) {
                if (ambiguousInvoices.contains(inv)) {
                    continue;
                }
                String existingOwner = invoiceToPurchase.get(inv);
                if (existingOwner != null && !existingOwner.equals(ownerPurId)) {
                    // Conflicting invoice ownership detected!
                    invoiceToPurchase.remove(inv);
                    ambiguousInvoices.add(inv);
                } else {
                    invoiceToPurchase.put(inv, ownerPurId);
                }
            }
        }

        // ---- Accessors ----

        /** Returns the record for a normalized purchase number, if present. */
        public Optional<PurchaseRecord> recordFor(String normalizedPurchaseId) {
            return Optional.ofNullable(records.get(normalizedPurchaseId));
        }

        /** Returns the purchase record owning the given normalized invoice number, if present and unambiguous. */
        public Optional<PurchaseRecord> recordForInvoice(String invoiceId) {
            String normalizedId = invoiceId.toLowerCase(Locale.ROOT);
            if (ambiguousInvoices.contains(normalizedId)) {
                return Optional.empty(); // Ownership is conflicting -> fail closed
            }
            String purId = invoiceToPurchase.get(normalizedId);
            if (purId != null) {
                return recordFor(purId);
            }
            List<PurchaseRecord> matching = records.values().stream()
                    .filter(r -> r.invoiceNumbers().contains(normalizedId))
                    .toList();
            if (matching.size() == 1) {
                return Optional.of(matching.get(0));
            }
            return Optional.empty();
        }

        /** Returns all purchase records, in insertion order. */
        public List<PurchaseRecord> allRecords() {
            return List.copyOf(records.values());
        }

        /** Number of distinct purchase records in the evidence. */
        public int recordCount() {
            return records.size();
        }

        // Legacy global-union accessors — used for identifier discovery
        public Set<String> purchaseNumbers()    { return globalPurchaseNumbers; }
        public Set<String> invoiceNumbers()     { return globalInvoiceNumbers; }
        public Set<String> productIdentifiers() { return globalProductIds; }
        public Set<String> supplierNames()      { return globalSupplierNames; }
        public Set<String> customerNames()      { return globalCustomerNames; }
        public Set<String> dates()              { return globalDates; }
        public Set<Double> amounts()            { return globalAmounts; }
        public Set<Double> quantities()         { return globalQuantities; }
        public Set<String> statuses()           { return globalStatuses; }
        public boolean hasData()                { return hasData; }

        /**
         * @deprecated Use {@link #recordFor(String)} and {@link PurchaseRecord#paymentStatus()} instead.
         *             Kept for internal legacy paths only.
         */
        @Deprecated(forRemoval = true)
        public Set<String> statusesForPurchase(String normalizedPurchaseId) {
            return recordFor(normalizedPurchaseId)
                    .map(PurchaseRecord::rawStatuses)
                    .orElse(Set.of());
        }

        private static Double parseDouble(Object val) {
            if (val instanceof Number n) return n.doubleValue();
            try {
                return Double.parseDouble(val.toString().replaceAll("[$,\u20b9]", "").trim());
            } catch (Exception e) {
                return null;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Citation tag helpers
    // -------------------------------------------------------------------------

    private static List<String> findSourceTags(String text) {
        List<String> tags = new ArrayList<>();
        Matcher matcher = SOURCE_TAG.matcher(text);
        while (matcher.find()) {
            tags.add(matcher.group().trim());
        }
        return tags;
    }

    private static Optional<RetrievedChunk> findMatchingChunk(String citation, List<RetrievedChunk> chunks) {
        if (!citation.endsWith("]")) {
            return Optional.empty();
        }
        Matcher matcher = STRICT_CITATION.matcher(citation);
        if (!matcher.matches()) {
            return Optional.empty();
        }

        String identity = normalize(matcher.group(1));
        int version;
        try {
            version = Integer.parseInt(matcher.group(2));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
        String section = normalizeSection(matcher.group(3));

        return chunks.stream().filter(chunk -> {
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
        }).findFirst();
    }

    private static boolean isValidCitation(String citation, List<RetrievedChunk> chunks) {
        return findMatchingChunk(citation, chunks).isPresent();
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

    // -------------------------------------------------------------------------
    // Utility helpers
    // -------------------------------------------------------------------------

    private static boolean isExplicitAbstention(String segment) {
        String normalized = normalize(segment).replaceAll("[.!?]+$", "").trim();
        return normalized.equals("i do not have sufficient authorized documentation to answer this question")
                || normalized.equals("i do not have documentation covering this")
                || normalized.equals("i cannot determine from the available documentation")
                || normalized.equals("the retrieved documents do not contain sufficient information to answer this question");
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
        // Escape ampersands first so the entities introduced below are not double-escaped.
        return stripped.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
