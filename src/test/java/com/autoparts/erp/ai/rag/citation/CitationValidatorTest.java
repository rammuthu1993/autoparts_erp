package com.autoparts.erp.ai.rag.citation;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.autoparts.erp.ai.rag.model.RetrievedChunk;

import static org.junit.jupiter.api.Assertions.*;

class CitationValidatorTest {

    private CitationValidator validator;
    private RetrievedChunk policyChunk;
    private RetrievedChunk generalChunk;

    @BeforeEach
    void setUp() {
        validator = new CitationValidator();
        policyChunk = new RetrievedChunk(101L, UUID.randomUUID(), "POL-RET-01",
                "Return and Refund Policy", 1, "Restocking Fee",
                "A 15% restocking fee applies to special order parts.", 30, 0.95);
        generalChunk = new RetrievedChunk(102L, UUID.randomUUID(), "SOP-INV-02",
                "Inventory Receiving SOP", 2, "General",
                "Receiving must inspect shipments upon arrival.", 25, 0.88);
    }

    @Test
    @DisplayName("Accepts exact document title, version and section from this request")
    void acceptsExactCitation() {
        String response = "Special orders incur a 15% fee [Source: Return and Refund Policy v1, §Restocking Fee].";
        var result = validator.validateCitations(response, List.of(policyChunk));
        assertTrue(result.valid());
        assertEquals(1, result.matchedCitations().size());
        assertTrue(result.unsupportedStatements().isEmpty());
        assertEquals(response, result.normalizedResponse());
    }

    @Test
    @DisplayName("Accepts exact document code citations")
    void acceptsExactDocumentCode() {
        var result = validator.validateCitations(
                "A 15% fee applies [Source: POL-RET-01 v1, §Restocking Fee].", List.of(policyChunk));
        assertTrue(result.valid());
    }

    @Test
    @DisplayName("Rejects substring title and removes the entire unsupported sentence")
    void rejectsSubstringDocumentTitle() {
        var result = validator.validateCitations(
                "A fee applies [Source: Return Policy v1, §Restocking Fee].", List.of(policyChunk));
        assertFalse(result.valid());
        assertEquals(1, result.fabricatedCitations().size());
        assertEquals("I do not have sufficient authorized documentation to answer this question.",
                result.normalizedResponse());
    }

    @Test
    @DisplayName("Rejects mismatched document version")
    void rejectsMismatchedVersion() {
        var result = validator.validateCitations(
                "A fee applies [Source: POL-RET-01 v2, §Restocking Fee].", List.of(policyChunk));
        assertFalse(result.valid());
        assertEquals(1, result.fabricatedCitations().size());
    }

    @Test
    @DisplayName("Rejects fabricated section on a genuine document")
    void rejectsFabricatedSection() {
        var result = validator.validateCitations(
                "Cash refunds are granted [Source: Return and Refund Policy v1, §Cash Refunds].",
                List.of(policyChunk));
        assertFalse(result.valid());
        assertEquals(1, result.fabricatedCitations().size());
    }

    @Test
    @DisplayName("Requires citations on every factual sentence")
    void rejectsUncitedFactualSentence() {
        String response = "A 15% fee applies [Source: Return and Refund Policy v1, §Restocking Fee]. "
                + "The return period is 90 days.";
        var result = validator.validateCitations(response, List.of(policyChunk));
        assertFalse(result.valid());
        assertEquals(1, result.unsupportedStatements().size());
        assertEquals("A 15% fee applies [Source: Return and Refund Policy v1, §Restocking Fee].",
                result.normalizedResponse());
    }

    @Test
    @DisplayName("Removes invalid sentence but preserves a separate supported sentence")
    void preservesSupportedSentenceAndDropsInvalidSentence() {
        String response = "A 15% fee applies [Source: Return and Refund Policy v1, §Restocking Fee]. "
                + "Cash refunds are automatic [Source: Return Policy v1, §Cash Refunds].";
        var result = validator.validateCitations(response, List.of(policyChunk));
        assertFalse(result.valid());
        assertTrue(result.normalizedResponse().contains("A 15% fee applies"));
        assertFalse(result.normalizedResponse().contains("Cash refunds are automatic"));
    }

    @Test
    @DisplayName("Preserves a supported sentence when a fabricated citation follows a later sentence")
    void preservesSupportedSentenceWhenLaterCitationIsFabricated() {
        String response = "Cash transactions get 2% off. [Source: Discount Policy v1, §Cash Discounts] "
                + "And secret executive bonuses are 50%. [Source: Secret Executive Compensation v5, §Bonuses]";
        RetrievedChunk discountChunk = new RetrievedChunk(301L, UUID.randomUUID(), "POL-DISC-01",
                "Discount Policy", 1, "Cash Discounts",
                "Cash transactions receive a 2% settlement discount.", 18, 0.91);

        var result = validator.validateCitations(response, List.of(discountChunk));

        assertFalse(result.valid());
        assertTrue(result.normalizedResponse().contains("Cash transactions get 2% off."));
        assertTrue(result.normalizedResponse().contains("[Source: Discount Policy v1, §Cash Discounts]"));
        assertFalse(result.normalizedResponse().contains("secret executive bonuses"));
        assertEquals(1, result.fabricatedCitations().size());
        assertEquals(1, result.unsupportedStatements().size());
    }

    @Test
    @DisplayName("Allows explicit abstention without a citation")
    void allowsAbstention() {
        String response = "I do not have sufficient authorized documentation to answer this question.";
        var result = validator.validateCitations(response, List.of());
        assertTrue(result.valid());
        assertEquals(response, result.normalizedResponse());
    }

    @Test
    @DisplayName("No retrieved chunks cannot validate a model citation")
    void rejectsCitationWhenNoChunksWereRetrieved() {
        var result = validator.validateCitations(
                "A fee applies [Source: Return and Refund Policy v1, §Restocking Fee].", List.of());
        assertFalse(result.valid());
        assertEquals(1, result.fabricatedCitations().size());
    }

    @Test
    @DisplayName("Rejects citation from another request even if title looks plausible")
    void rejectsCitationNotPresentInCurrentRetrievedChunks() {
        RetrievedChunk otherRequestChunk = new RetrievedChunk(201L, UUID.randomUUID(), "POL-RET-01",
                "Return and Refund Policy", 1, "Restocking Fee", "A 15% restocking fee applies.", 30, 0.9);
        var result = validator.validateCitations(
                "A fee applies [Source: Return and Refund Policy v1, §Restocking Fee].", List.of(generalChunk));
        assertFalse(result.valid());
        assertTrue(result.fabricatedCitations().size() == 1);
        assertNotNull(otherRequestChunk); // The validator receives only the current request's allow-list.
    }

    @Test
    @DisplayName("Deduplicates repeated identical citation tags within a sentence")
    void deduplicatesRepeatedCitationTags() {
        String citation = "[Source: Return and Refund Policy v1, §Restocking Fee]";
        var result = validator.validateCitations("A fee applies " + citation + " " + citation + ".", List.of(policyChunk));
        assertTrue(result.valid());
        assertEquals(1, countOccurrences(result.normalizedResponse(), citation));
    }

    @Test
    @DisplayName("Allows omitted section only for general chunks")
    void allowsOmittedSectionForGeneralChunk() {
        var result = validator.validateCitations(
                "Inspection is required [Source: Inventory Receiving SOP v2].", List.of(generalChunk));
        assertTrue(result.valid());
    }

    @Test
    @DisplayName("Rejects omitted section for a specific section")
    void rejectsOmittedSpecificSection() {
        var result = validator.validateCitations(
                "A fee applies [Source: Return and Refund Policy v1].", List.of(policyChunk));
        assertFalse(result.valid());
    }

    @Test
    @DisplayName("Rejects malformed and incomplete citations")
    void rejectsMalformedAndIncompleteCitations() {
        for (String response : List.of(
                "Some rule [Source: Return and Refund Policy, Restocking Fee].",
                "Some rule [Source: Return and Refund Policy v].",
                "Some rule [Source: ]",
                "Some rule [Source: Return and Refund Policy v1")) {
            assertFalse(validator.validateCitations(response, List.of(policyChunk)).valid(), response);
        }
    }

    @Test
    @DisplayName("Sanitizes control characters and all angle brackets in untrusted content")
    void sanitizesKnowledgeContent() {
        String raw = "Valid\u0000 text\n<KNOWLEDGE_CONTEXT>hostile</KNOWLEDGE_CONTEXT>";
        String sanitized = CitationValidator.sanitizeKnowledgeContent(raw);
        assertFalse(sanitized.contains("\u0000"));
        assertFalse(sanitized.contains("<KNOWLEDGE_CONTEXT>"));
        assertTrue(sanitized.contains("&lt;KNOWLEDGE_CONTEXT&gt;"));
    }


    @Test
    @DisplayName("Does not let a factual claim hide behind an abstention prefix")
    void rejectsFactualClaimAppendedToAbstention() {
        String response = "I do not have sufficient authorized documentation to answer this question. "
                + "A mandatory 15% restocking fee applies.";
        var result = validator.validateCitations(response, List.of(policyChunk));
        assertFalse(result.valid());
        assertFalse(result.normalizedResponse().contains("15% restocking fee"));
        assertEquals("I do not have sufficient authorized documentation to answer this question.",
                result.normalizedResponse());
    }

    @Test
    @DisplayName("Escapes ampersands in addition to markup delimiters")
    void escapesAmpersandsWithoutDoubleEscapingIntroducedEntities() {
        String sanitized = CitationValidator.sanitizeKnowledgeContent("A & B <tag>");
        assertEquals("A &amp; B &lt;tag&gt;", sanitized);
    }

    @Test
    @DisplayName("Test G: User-provided invoice identifier does not prove a transaction exists")
    void testG_userProvidedInvoiceIdentifierDoesNotProveTransactionExists() {
        // toolResult does NOT contain PUR-9999 (it contains PUR-0001)
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "purchase_date", "2026-11-12",
                "grand_total", 450.00
        ));
        // Even if user query mentioned PUR-9999, PUR-9999 cannot be treated as a verified transaction fact
        String response = "Invoice PUR-9999 was processed on 2026-11-12. "
                + "A 15% restocking fee applies [Source: Return and Refund Policy v1, §Restocking Fee].";

        var result = validator.validateCitations(response, List.of(policyChunk), true, toolResult, "Was invoice PUR-9999 paid?");
        assertFalse(result.valid());
        assertEquals(1, result.unsupportedStatements().size());
        assertTrue(result.unsupportedStatements().get(0).contains("PUR-9999"));
        assertFalse(result.normalizedResponse().contains("PUR-9999"));
        assertTrue(result.normalizedResponse().contains("15% restocking fee"));
    }

    @Test
    @DisplayName("Test H: Purchase date must match purchase_date in toolResult")
    void testH_purchaseDateMustMatchPurchaseDate() {
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "purchase_date", "2026-11-12",
                "grand_total", 450.00
        ));
        String response = "Purchase PUR-0001 was completed on 2025-01-01 for 450.00. "
                + "A 15% restocking fee applies [Source: Return and Refund Policy v1, §Restocking Fee].";

        var result = validator.validateCitations(response, List.of(policyChunk), true, toolResult);
        assertFalse(result.valid());
        assertEquals(1, result.unsupportedStatements().size());
        assertTrue(result.unsupportedStatements().get(0).contains("2025-01-01"));
        assertFalse(result.normalizedResponse().contains("2025-01-01"));
    }

    @Test
    @DisplayName("Test I: Supplier name must match supplier_name in toolResult")
    void testI_supplierNameMustMatchSupplierName() {
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "supplier_name", "Acme Parts",
                "purchase_date", "2026-11-12",
                "grand_total", 450.00
        ));
        String response = "Order from supplier Fake Motors under PUR-0001 was completed on 2026-11-12. "
                + "A 15% restocking fee applies [Source: Return and Refund Policy v1, §Restocking Fee].";

        var result = validator.validateCitations(response, List.of(policyChunk), true, toolResult);
        assertFalse(result.valid());
        assertEquals(1, result.unsupportedStatements().size());
        assertTrue(result.unsupportedStatements().get(0).contains("Fake Motors"));
    }

    @Test
    @DisplayName("Test J: Amount must match grand_total in toolResult")
    void testJ_amountMustMatchGrandTotal() {
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "purchase_date", "2026-11-12",
                "grand_total", 450.00
        ));
        String response = "Purchase PUR-0001 total was 9999.00 on 2026-11-12. "
                + "A 15% restocking fee applies [Source: Return and Refund Policy v1, §Restocking Fee].";

        var result = validator.validateCitations(response, List.of(policyChunk), true, toolResult);
        assertFalse(result.valid());
        assertEquals(1, result.unsupportedStatements().size());
        assertTrue(result.unsupportedStatements().get(0).contains("9999.00"));
        assertFalse(result.normalizedResponse().contains("9999.00"));
    }

    @Test
    @DisplayName("Test K: Payment status must be supported by payment evidence")
    void testK_paymentStatusMustBeSupportedByPaymentEvidence() {
        // Status is PENDING, not PAID
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "purchase_date", "2026-11-12",
                "status", "PENDING",
                "grand_total", 450.00
        ));
        String response = "A payment was settled for PUR-0001 on 2026-11-12. "
                + "A 15% restocking fee applies [Source: Return and Refund Policy v1, §Restocking Fee].";

        var result = validator.validateCitations(response, List.of(policyChunk), true, toolResult);
        assertFalse(result.valid());
        assertEquals(1, result.unsupportedStatements().size());
        assertTrue(result.unsupportedStatements().get(0).contains("payment"));
    }

    @Test
    @DisplayName("Test L: Valid ERP claims plus valid RAG citations are accepted in the same sentence")
    void testL_validErpClaimsPlusValidRagCitationsAccepted() {
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "purchase_date", "2026-11-12",
                "status", "RECEIVED",
                "grand_total", 450.00
        ));
        String response = "You purchased under invoice PUR-0001 on 2026-11-12 for 450.00. "
                + "Under company policy, eligible items incur a fee [Source: Return and Refund Policy v1, §Restocking Fee].";

        var result = validator.validateCitations(response, List.of(policyChunk), true, toolResult);
        assertTrue(result.valid());
        assertTrue(result.unsupportedStatements().isEmpty());
        assertTrue(result.normalizedResponse().contains("PUR-0001"));
        assertTrue(result.normalizedResponse().contains("450.00"));
        assertTrue(result.normalizedResponse().contains("[Source: Return and Refund Policy v1, §Restocking Fee]"));
    }

    @Test
    @DisplayName("Test M: Unsupported ERP claims are rejected and removed from normalized response")
    void testM_unsupportedErpClaimsRejected() {
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "purchase_date", "2026-11-12",
                "grand_total", 450.00
        ));
        String response = "Fabricated purchase PUR-8888 was placed on 2024-01-01 for 8888.00. "
                + "A 15% restocking fee applies [Source: Return and Refund Policy v1, §Restocking Fee].";

        var result = validator.validateCitations(response, List.of(policyChunk), true, toolResult);
        assertFalse(result.valid());
        assertEquals(1, result.unsupportedStatements().size());
        assertFalse(result.normalizedResponse().contains("PUR-8888"));
        assertTrue(result.normalizedResponse().contains("15% restocking fee"));
    }

    @Test
    @DisplayName("Test N: Inventory quantity cannot validate a monetary amount claim")
    void testN_inventoryQuantityCannotValidateMonetaryAmountClaim() {
        // grand_total=450.00, quantity=5. The AI claims ₹5 — quantity=5 must NOT satisfy a monetary claim.
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "purchase_date", "2026-11-12",
                "grand_total", 450.00,
                "quantity", 5.0
        ));
        // "₹5" matches AMOUNT_PATTERN (currency-prefixed). evidence.amounts()={450.00}, evidence.quantities()={5.0}.
        // After fix: amounts-only check → 5.0 ∉ {450.00} → rejected.
        String response = "Purchase PUR-0001 cost ₹5 on 2026-11-12.";
        var result = validator.validateCitations(response, List.of(policyChunk), true, toolResult);
        assertFalse(result.valid(), "A monetary claim of ₹5 must not be validated by an inventory quantity of 5");
        assertEquals(1, result.unsupportedStatements().size());
    }

    @Test
    @DisplayName("Test P: PAID status from one transaction cannot validate a PENDING transaction's payment claim")
    void testP_paidStatusFromOtherTransactionCannotValidatePendingTransaction() {
        // PUR-0001 is PAID; PUR-0002 is PENDING.
        // The AI claims PUR-0002 was settled — the PAID status of PUR-0001 must not bleed over.
        var toolResult = List.of(
                java.util.Map.of(
                        "purchase_number", "PUR-0001",
                        "purchase_date", "2026-11-10",
                        "grand_total", 450.00,
                        "status", "PAID"),
                java.util.Map.of(
                        "purchase_number", "PUR-0002",
                        "purchase_date", "2026-11-12",
                        "grand_total", 300.00,
                        "status", "PENDING")
        );
        // Segment mentions PUR-0002 specifically → validator must look up PUR-0002's statuses → {pending} → no paid status → rejected
        String response = "Payment was settled for PUR-0002 on 2026-11-12.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertFalse(result.valid(),
                "A settled/paid claim about PUR-0002 must be rejected when PUR-0002 status is PENDING, "
                        + "even though PUR-0001 in the same evidence is PAID");
    }

    // =========================================================================
    // Per-record evidence & payment-status semantic regression tests (Q–Z)
    // =========================================================================

    // ---- Shared two-record tool result used by tests Q–Z ----
    // PUR-0001: 2026-11-10, ABC Motors, ₹450, PAID
    // PUR-0002: 2026-11-12, XYZ Parts, ₹300, PENDING
    private static java.util.List<java.util.Map<String, Object>> twoRecordResult() {
        return List.of(
                java.util.Map.of(
                        "purchase_number", "PUR-0001",
                        "purchase_date",   "2026-11-10",
                        "supplier_name",   "ABC Motors",
                        "grand_total",     450.00,
                        "payment_status",  "PAID"),
                java.util.Map.of(
                        "purchase_number", "PUR-0002",
                        "purchase_date",   "2026-11-12",
                        "supplier_name",   "XYZ Parts",
                        "grand_total",     300.00,
                        "payment_status",  "PENDING")
        );
    }

    @Test
    @DisplayName("Test Q-A: UNPAID status must not validate a 'paid' claim")
    void testQA_unpaidMustNotValidatePaidClaim() {
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "purchase_date",   "2026-11-12",
                "grand_total",     450.00,
                "payment_status",  "UNPAID"));
        String response = "Payment was settled for PUR-0001 on 2026-11-12.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertFalse(result.valid(), "UNPAID must not satisfy a 'settled' payment claim");
    }

    @Test
    @DisplayName("Test Q-B: PARTIALLY_PAID must not validate a fully-paid claim")
    void testQB_partiallyPaidMustNotValidateFullyPaidClaim() {
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "purchase_date",   "2026-11-12",
                "grand_total",     450.00,
                "payment_status",  "PARTIALLY_PAID"));
        String response = "Payment was settled for PUR-0001 on 2026-11-12.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertFalse(result.valid(), "PARTIALLY_PAID must not satisfy a fully-settled payment claim");
    }

    @Test
    @DisplayName("Test Q-C: RECEIVED purchase status must not establish payment confirmation")
    void testQC_receivedStatusMustNotConfirmPayment() {
        // "RECEIVED" means goods arrived in inventory; the invoice may not be paid
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "purchase_date",   "2026-11-12",
                "grand_total",     450.00,
                "status",          "RECEIVED"));
        String response = "Payment was settled for PUR-0001 on 2026-11-12.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertFalse(result.valid(), "RECEIVED is a goods-receipt status and must not confirm payment");
    }

    @Test
    @DisplayName("Test Q-D: PAID for PUR-0001 must not validate a payment claim about PUR-0002")
    void testQD_paidStatusMustNotBleedAcrossPurchases() {
        // PUR-0001=PAID, PUR-0002=PENDING; claim about PUR-0002 must be rejected
        String response = "Payment was settled for PUR-0002 on 2026-11-12.";
        var result = validator.validateCitations(response, List.of(), true, twoRecordResult());
        assertFalse(result.valid(),
                "PAID status for PUR-0001 must not validate a payment claim about PUR-0002 (PENDING)");
    }

    @Test
    @DisplayName("Test Q-E: Amount from PUR-0001 must not validate a claim about PUR-0002")
    void testQE_amountMustNotBleedAcrossPurchases() {
        // PUR-0001 total=450, PUR-0002 total=300. AI claims PUR-0002 cost ₹450 (wrong amount).
        String response = "PUR-0002 was purchased for ₹450 on 2026-11-12.";
        var result = validator.validateCitations(response, List.of(), true, twoRecordResult());
        assertFalse(result.valid(),
                "₹450 belongs to PUR-0001 and must not validate a claim about PUR-0002");
    }

    @Test
    @DisplayName("Test Q-F: Date from PUR-0001 must not validate a claim about PUR-0002")
    void testQF_dateMustNotBleedAcrossPurchases() {
        // PUR-0001 date=2026-11-10, PUR-0002 date=2026-11-12. AI claims PUR-0002 on 2026-11-10.
        String response = "PUR-0002 was purchased on 2026-11-10.";
        var result = validator.validateCitations(response, List.of(), true, twoRecordResult());
        assertFalse(result.valid(),
                "2026-11-10 belongs to PUR-0001 and must not validate a date claim about PUR-0002");
    }

    @Test
    @DisplayName("Test Q-G: Supplier from PUR-0001 must not validate a claim about PUR-0002")
    void testQG_supplierMustNotBleedAcrossPurchases() {
        // PUR-0001 supplier=ABC Motors, PUR-0002 supplier=XYZ Parts. AI claims PUR-0002 from ABC Motors.
        String response = "PUR-0002 was ordered from supplier ABC Motors on 2026-11-12.";
        var result = validator.validateCitations(response, List.of(), true, twoRecordResult());
        assertFalse(result.valid(),
                "ABC Motors belongs to PUR-0001 and must not validate a supplier claim about PUR-0002");
    }

    @Test
    @DisplayName("Test Q-H: Correct values for PUR-0002 must be accepted")
    void testQH_correctValuesForPurchase2AreAccepted() {
        // All values belong to PUR-0002's own record — must pass
        String response = "PUR-0002 was ordered from supplier XYZ Parts on 2026-11-12 for 300.00.";
        var result = validator.validateCitations(response, List.of(), true, twoRecordResult());
        assertTrue(result.valid(),
                "All claimed values belong to PUR-0002 and should be accepted");
    }

    @Test
    @DisplayName("Test Q-I: Ambiguous payment claim with no purchase ID and multiple records fails closed")
    void testQI_ambiguousPaymentClaimFailsClosed() {
        // No purchase number in the segment; two records present — must fail closed
        String response = "The supplier payment has been settled.";
        var result = validator.validateCitations(response, List.of(), true, twoRecordResult());
        assertFalse(result.valid(),
                "An unidentified payment claim must fail closed when multiple records exist in evidence");
    }

    @Test
    @DisplayName("Test Q-J: Existing Test N (qty cannot validate amount) still passes")
    void testQJ_existingTestN_quantityCannotValidateMonetaryAmount() {
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "purchase_date",   "2026-11-12",
                "grand_total",     450.00,
                "quantity",        5.0));
        String response = "Purchase PUR-0001 cost ₹5 on 2026-11-12.";
        var result = validator.validateCitations(response, List.of(policyChunk), true, toolResult);
        assertFalse(result.valid(), "Quantity=5 must not validate a monetary claim of ₹5");
        assertEquals(1, result.unsupportedStatements().size());
    }

    @Test
    @DisplayName("Test Q-J2: Existing Test P (PAID bleed) still passes")
    void testQJ2_existingTestP_paidStatusBleedPrevented() {
        var toolResult = List.of(
                java.util.Map.of("purchase_number", "PUR-0001", "purchase_date", "2026-11-10",
                        "grand_total", 450.00, "status", "PAID"),
                java.util.Map.of("purchase_number", "PUR-0002", "purchase_date", "2026-11-12",
                        "grand_total", 300.00, "status", "PENDING"));
        String response = "Payment was settled for PUR-0002 on 2026-11-12.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertFalse(result.valid(),
                "PAID status of PUR-0001 must not validate PUR-0002's payment claim (PENDING)");
    }

    @Test
    @DisplayName("Test Q-K: PAID single-record payment claim is accepted")
    void testQK_singleRecordPaidPaymentClaimAccepted() {
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "purchase_date",   "2026-11-12",
                "grand_total",     450.00,
                "payment_status",  "PAID"));
        String response = "Payment was settled for PUR-0001 on 2026-11-12.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertTrue(result.valid(), "PAID single-record claim must be accepted");
    }

    @Test
    @DisplayName("Test Q-L: Ambiguous payment with single record is accepted when record is PAID")
    void testQL_singleRecordAmbiguousPaymentAcceptedWhenPaid() {
        // No purchase number in segment; only one record in evidence and it is PAID
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "grand_total",     450.00,
                "payment_status",  "PAID"));
        String response = "The supplier payment has been settled.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertTrue(result.valid(),
                "With exactly one PAID record, an unidentified payment claim should be accepted");
    }

    @Test
    @DisplayName("Test R-B: Named supplier claim with no supplier evidence in record must fail")
    void testRB_namedSupplierWithNoSupplierEvidenceMustFail() {
        // PUR-0001 has no supplier_name
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "purchase_date",   "2026-11-12",
                "grand_total",     450.00
        ));
        String response = "PUR-0001 was purchased from supplier ABC Motors on 2026-11-12.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertFalse(result.valid(), "Supplier claim must fail when record has no supplier evidence");
    }

    @Test
    @DisplayName("Test R-C: Fabricated supplier name must fail")
    void testRC_fabricatedSupplierNameMustFail() {
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "supplier_name",   "Acme Parts",
                "purchase_date",   "2026-11-12",
                "grand_total",     450.00
        ));
        String response = "PUR-0001 was purchased from supplier Fraudulent Auto Parts on 2026-11-12.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertFalse(result.valid(), "Fabricated supplier name must fail");
    }

    @Test
    @DisplayName("Test R-D: Correct supplier name for referenced purchase must pass")
    void testRD_correctSupplierNameMustPass() {
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "supplier_name",   "Acme Parts",
                "purchase_date",   "2026-11-12",
                "grand_total",     450.00
        ));
        String response = "PUR-0001 was purchased from supplier Acme Parts on 2026-11-12 for 450.00.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertTrue(result.valid(), "Correct supplier name must pass");
    }

    @Test
    @DisplayName("Test R-F: Date from PUR-0001 must not validate an unidentified transaction claim when multiple records exist")
    void testRF_dateFromPur0001MustNotValidateUnidentifiedTransaction() {
        // Two records. Response claims date 2026-11-10 without identifying PUR-0001
        String response = "The purchase was made on 2026-11-10.";
        var result = validator.validateCitations(response, List.of(), true, twoRecordResult());
        assertFalse(result.valid(), "Unidentified transaction date claim must fail closed when multiple records exist");
    }

    @Test
    @DisplayName("Test R-G: Amount from PUR-0001 must not validate an unidentified transaction claim when multiple records exist")
    void testRG_amountFromPur0001MustNotValidateUnidentifiedTransaction() {
        // Two records. Response claims ₹450 without identifying PUR-0001
        String response = "The purchase order total was ₹450.";
        var result = validator.validateCitations(response, List.of(), true, twoRecordResult());
        assertFalse(result.valid(), "Unidentified transaction amount claim must fail closed when multiple records exist");
    }

    @Test
    @DisplayName("Test R-H: Invoice number resolves to the owning purchase record for date/amount validation")
    void testRH_invoiceNumberResolvesToOwningPurchaseRecord() {
        var toolResult = List.of(
                java.util.Map.of(
                        "purchase_number",     "PUR-0001",
                        "supplier_invoice_no", "INV-1001",
                        "purchase_date",       "2026-11-10",
                        "grand_total",         450.00),
                java.util.Map.of(
                        "purchase_number",     "PUR-0002",
                        "supplier_invoice_no", "INV-1002",
                        "purchase_date",       "2026-11-12",
                        "grand_total",         300.00)
        );
        // Correct match: INV-1002 with its own date and amount
        String validResponse = "Invoice INV-1002 was issued on 2026-11-12 for 300.00.";
        var validResult = validator.validateCitations(validResponse, List.of(), true, toolResult);
        assertTrue(validResult.valid(), "INV-1002 should resolve to PUR-0002 and accept its valid date and amount");

        // Cross-contamination: INV-1002 with PUR-0001's amount (450.00)
        String invalidResponse = "Invoice INV-1002 was issued on 2026-11-12 for 450.00.";
        var invalidResult = validator.validateCitations(invalidResponse, List.of(), true, toolResult);
        assertFalse(invalidResult.valid(), "INV-1002 must not accept amount belonging to PUR-0001");
    }

    @Test
    @DisplayName("Test R-I: Two maps for the same purchase with complementary fields preserve both sets of evidence")
    void testRI_complementaryDuplicateMapsPreserveBothEvidences() {
        var toolResult = List.of(
                java.util.Map.of(
                        "purchase_number", "PUR-0001",
                        "supplier_name",   "ABC Motors"),
                java.util.Map.of(
                        "purchase_number", "PUR-0001",
                        "grand_total",     450.00,
                        "purchase_date",   "2026-11-10")
        );
        // Response uses supplier from map 1 and amount/date from map 2
        String response = "PUR-0001 was purchased from supplier ABC Motors for 450.00 on 2026-11-10.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertTrue(result.valid(), "Complementary maps for same purchase must merge without losing evidence");
    }

    @Test
    @DisplayName("Test R-J: Two maps for the same purchase with conflicting payment statuses do not accept PAID")
    void testRJ_conflictingPaymentStatusesFailClosed() {
        var toolResult = List.of(
                java.util.Map.of(
                        "purchase_number", "PUR-0001",
                        "purchase_date",   "2026-11-10",
                        "payment_status",  "PAID"),
                java.util.Map.of(
                        "purchase_number", "PUR-0001",
                        "purchase_date",   "2026-11-10",
                        "payment_status",  "PENDING")
        );
        // Should detect conflict and NOT accept PAID
        String response = "Payment was settled for PUR-0001 on 2026-11-10.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertFalse(result.valid(), "Conflicting payment statuses for the same purchase must fail closed");
    }

    // =========================================================================
    // Correctness Pass Tests (Issues A - F)
    // =========================================================================

    @Test
    @DisplayName("Issue A-1: Conflicting dates for the same purchase fail closed")
    void testIssueA1_conflictingDatesFailClosed() {
        var toolResult = List.of(
                java.util.Map.of("purchase_number", "PUR-0001", "purchase_date", "2026-11-10", "grand_total", 450.00),
                java.util.Map.of("purchase_number", "PUR-0001", "purchase_date", "2026-11-15", "grand_total", 450.00)
        );
        // Neither date can authorize a claim
        String response1 = "PUR-0001 was purchased on 2026-11-10.";
        assertFalse(validator.validateCitations(response1, List.of(), true, toolResult).valid());

        String response2 = "PUR-0001 was purchased on 2026-11-15.";
        assertFalse(validator.validateCitations(response2, List.of(), true, toolResult).valid());
    }

    @Test
    @DisplayName("Issue A-2: Conflicting monetary amounts for the same purchase fail closed")
    void testIssueA2_conflictingAmountsFailClosed() {
        var toolResult = List.of(
                java.util.Map.of("purchase_number", "PUR-0001", "grand_total", 450.00, "purchase_date", "2026-11-10"),
                java.util.Map.of("purchase_number", "PUR-0001", "grand_total", 600.00, "purchase_date", "2026-11-10")
        );
        // Neither amount can authorize a claim
        String response1 = "PUR-0001 total was ₹450 on 2026-11-10.";
        assertFalse(validator.validateCitations(response1, List.of(), true, toolResult).valid());

        String response2 = "PUR-0001 total was ₹600 on 2026-11-10.";
        assertFalse(validator.validateCitations(response2, List.of(), true, toolResult).valid());
    }

    @Test
    @DisplayName("Issue A-3: Conflicting suppliers for the same purchase fail closed")
    void testIssueA3_conflictingSuppliersFailClosed() {
        var toolResult = List.of(
                java.util.Map.of("purchase_number", "PUR-0001", "supplier_name", "ABC Motors"),
                java.util.Map.of("purchase_number", "PUR-0001", "supplier_name", "XYZ Parts")
        );
        String response1 = "PUR-0001 was purchased from supplier ABC Motors.";
        assertFalse(validator.validateCitations(response1, List.of(), true, toolResult).valid());

        String response2 = "PUR-0001 was purchased from supplier XYZ Parts.";
        assertFalse(validator.validateCitations(response2, List.of(), true, toolResult).valid());
    }

    @Test
    @DisplayName("Issue B: Explicit unrecognised status (PROCESSING) conflicting with PAID fails closed")
    void testIssueB_processingStatusConflictingWithPaidFailsClosed() {
        var toolResult = List.of(
                java.util.Map.of("purchase_number", "PUR-0001", "payment_status", "PROCESSING"),
                java.util.Map.of("purchase_number", "PUR-0001", "payment_status", "PAID")
        );
        String response = "Payment was settled for PUR-0001.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertFalse(result.valid(), "PROCESSING conflicting with PAID must fail closed");
    }

    @Test
    @DisplayName("Issue C-1: Cited policy amount passes when supported by RAG chunk even if differing from purchase total")
    void testIssueC1_citedPolicyAmountPassesWhenSupportedByRagChunk() {
        RetrievedChunk feePolicyChunk = new RetrievedChunk(105L, UUID.randomUUID(), "POL-RET-01",
                "Return and Refund Policy", 1, "Restocking Fee",
                "A ₹100 restocking fee applies to special order parts.", 30, 0.95);

        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "purchase_date",   "2026-11-12",
                "grand_total",     450.00
        ));

        // ₹100 is supported by the cited policy chunk, not the purchase total (₹450)
        String response = "A ₹100 restocking fee applies [Source: Return and Refund Policy v1, §Restocking Fee].";
        var result = validator.validateCitations(response, List.of(feePolicyChunk), true, toolResult);
        assertTrue(result.valid(), "Cited RAG policy amount must be validated against the policy chunk, not purchase total");
    }

    @Test
    @DisplayName("Issue C-2: Cited policy date passes when supported by RAG chunk even if differing from purchase date")
    void testIssueC2_citedPolicyDatePassesWhenSupportedByRagChunk() {
        RetrievedChunk datedPolicyChunk = new RetrievedChunk(106L, UUID.randomUUID(), "POL-RET-01",
                "Return and Refund Policy", 1, "General",
                "Under policy effective 2025-06-01, returns must be pre-approved.", 30, 0.95);

        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "purchase_date",   "2026-11-12",
                "grand_total",     450.00
        ));

        // 2025-06-01 is supported by the policy chunk, not the purchase date (2026-11-12)
        String response = "Under policy effective 2025-06-01, returns must be pre-approved [Source: Return and Refund Policy v1].";
        var result = validator.validateCitations(response, List.of(datedPolicyChunk), true, toolResult);
        assertTrue(result.valid(), "Cited RAG policy date must be validated against the policy chunk, not purchase date");
    }

    @Test
    @DisplayName("Issue C-3: Mixed sentence with valid policy amount but fabricated ERP amount must fail")
    void testIssueC3_mixedSentenceWithFabricatedErpAmountFails() {
        RetrievedChunk feePolicyChunk = new RetrievedChunk(105L, UUID.randomUUID(), "POL-RET-01",
                "Return and Refund Policy", 1, "Restocking Fee",
                "A ₹100 restocking fee applies to special order parts.", 30, 0.95);

        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "purchase_date",   "2026-11-12",
                "grand_total",     450.00
        ));

        // ₹100 is valid (RAG), but ₹9999 is fabricated (ERP)
        String response = "PUR-0001 was purchased for ₹9999.00 and incurs a ₹100 fee [Source: Return and Refund Policy v1, §Restocking Fee].";
        var result = validator.validateCitations(response, List.of(feePolicyChunk), true, toolResult);
        assertFalse(result.valid(), "Fabricated ERP amount in mixed sentence must still fail");
    }

    @Test
    @DisplayName("Issue C-4: Valid mixed ERP and RAG response passes")
    void testIssueC4_validMixedErpAndRagResponsePasses() {
        RetrievedChunk feePolicyChunk = new RetrievedChunk(105L, UUID.randomUUID(), "POL-RET-01",
                "Return and Refund Policy", 1, "Restocking Fee",
                "A ₹100 restocking fee applies to special order parts.", 30, 0.95);

        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "purchase_date",   "2026-11-12",
                "grand_total",     450.00
        ));

        // ₹450 and 2026-11-12 match PUR-0001; ₹100 matches the policy chunk
        String response = "PUR-0001 was purchased on 2026-11-12 for 450.00 and incurs a ₹100 fee [Source: Return and Refund Policy v1, §Restocking Fee].";
        var result = validator.validateCitations(response, List.of(feePolicyChunk), true, toolResult);
        assertTrue(result.valid(), "Valid mixed ERP and RAG response must pass");
    }

    @Test
    @DisplayName("Issue D-1: Truthful PENDING payment claim passes, and false PENDING claim fails")
    void testIssueD1_pendingPaymentValidation() {
        var toolResultPending = List.of(java.util.Map.of(
                "purchase_number", "PUR-0002",
                "purchase_date",   "2026-11-12",
                "payment_status",  "PENDING"
        ));
        String response = "PUR-0002 is currently pending payment on 2026-11-12.";
        var result = validator.validateCitations(response, List.of(), true, toolResultPending);
        assertTrue(result.valid(), "Truthful PENDING claim must pass");

        var toolResultUnpaid = List.of(java.util.Map.of(
                "purchase_number", "PUR-0002",
                "purchase_date",   "2026-11-12",
                "payment_status",  "UNPAID"
        ));
        var resultFalse = validator.validateCitations(response, List.of(), true, toolResultUnpaid);
        assertFalse(resultFalse.valid(), "PENDING claim must fail when record is UNPAID");
    }

    @Test
    @DisplayName("Issue D-2: Truthful UNPAID payment claim passes, and false UNPAID claim fails")
    void testIssueD2_unpaidPaymentValidation() {
        var toolResultUnpaid = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "purchase_date",   "2026-11-12",
                "payment_status",  "UNPAID"
        ));
        String response = "PUR-0001 is currently unpaid on 2026-11-12.";
        var result = validator.validateCitations(response, List.of(), true, toolResultUnpaid);
        assertTrue(result.valid(), "Truthful UNPAID claim must pass");

        var toolResultPaid = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "purchase_date",   "2026-11-12",
                "payment_status",  "PAID"
        ));
        var resultFalse = validator.validateCitations(response, List.of(), true, toolResultPaid);
        assertFalse(resultFalse.valid(), "UNPAID claim must fail when record is PAID");
    }

    @Test
    @DisplayName("Issue D-3: PARTIALLY_PAID and OVERDUE truthful payment claims pass")
    void testIssueD3_partialAndOverduePaymentValidation() {
        var toolResultPartial = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "payment_status",  "PARTIALLY_PAID"
        ));
        String responsePartial = "PUR-0001 was partially paid.";
        assertTrue(validator.validateCitations(responsePartial, List.of(), true, toolResultPartial).valid());

        var toolResultOverdue = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "payment_status",  "OVERDUE"
        ));
        String responseOverdue = "PUR-0001 payment is overdue.";
        assertTrue(validator.validateCitations(responseOverdue, List.of(), true, toolResultOverdue).valid());
    }

    @Test
    @DisplayName("Issue E-1: Partial supplier name substring does not match full supplier entity")
    void testIssueE1_partialSupplierNameDoesNotMatchFullEntity() {
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "supplier_name",   "Acme Parts"
        ));
        // Claiming only "Parts" should not match "Acme Parts"
        String response = "PUR-0001 was ordered from supplier Parts.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertFalse(result.valid(), "Substring 'Parts' must not match entity 'Acme Parts'");
    }

    @Test
    @DisplayName("Issue E-2: Literal 'supplier: ABC Motors' syntax is extracted and validated")
    void testIssueE2_literalSupplierColonSyntaxExtracted() {
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "supplier_name",   "ABC Motors",
                "purchase_date",   "2026-11-10"
        ));
        String response = "PUR-0001 from supplier: ABC Motors on 2026-11-10.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertTrue(result.valid(), "'supplier: ABC Motors' must be properly recognized and matched");
    }

    @Test
    @DisplayName("Issue F: Conflicting invoice ownership fails closed")
    void testIssueF_conflictingInvoiceOwnershipFailsClosed() {
        // Same invoice INV-1001 appears under two different purchases
        var toolResult = List.of(
                java.util.Map.of("purchase_number", "PUR-0001", "supplier_invoice_no", "INV-1001", "purchase_date", "2026-11-10"),
                java.util.Map.of("purchase_number", "PUR-0002", "supplier_invoice_no", "INV-1001", "purchase_date", "2026-11-12")
        );
        String response = "Invoice INV-1001 was issued on 2026-11-10.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertFalse(result.valid(), "Ambiguous invoice ownership must fail closed");
    }

    // -------------------------------------------------------------------------
    // Final Edge-Case Hardening Tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Edge-Case 1A: Multiple transactions in one sentence validate amounts per record")
    void testMultiTransactionSentence_differentAmountsValidatedPerRecord() {
        var toolResult = List.of(
                java.util.Map.of("purchase_number", "PUR-0001", "grand_total", 450.00),
                java.util.Map.of("purchase_number", "PUR-0002", "grand_total", 300.00)
        );

        // Matching amounts per record: PUR-0001=450, PUR-0002=300 -> PASS
        String validResponse = "PUR-0001 was ₹450 and PUR-0002 was ₹300.";
        var validResult = validator.validateCitations(validResponse, List.of(), true, toolResult);
        assertTrue(validResult.valid(), "Each purchase amount must be correctly matched to its specific record");

        // Fabricated amount for PUR-0002: 999 instead of 300 -> FAIL
        String invalidResponse = "PUR-0001 was ₹450 and PUR-0002 was ₹999.";
        var invalidResult = validator.validateCitations(invalidResponse, List.of(), true, toolResult);
        assertFalse(invalidResult.valid(), "Fabricated amount for PUR-0002 must fail even if PUR-0001 amount is valid");
    }

    @Test
    @DisplayName("Edge-Case 1B: Multi-transaction sentence with unassigned fact in preamble fails closed")
    void testMultiTransactionSentence_unassignedPreambleFailsClosed() {
        var toolResult = List.of(
                java.util.Map.of("purchase_number", "PUR-0001", "grand_total", 450.00),
                java.util.Map.of("purchase_number", "PUR-0002", "grand_total", 300.00)
        );

        // Preamble has an amount before the first PUR ID that cannot be unambiguously attributed
        String response = "The total cost was ₹450 for PUR-0001 and PUR-0002.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertFalse(result.valid(), "Ambiguous preamble fact across multiple transactions must fail closed");
    }

    @Test
    @DisplayName("Edge-Case 2A: Payment negation 'not settled' fails when record is PAID")
    void testPaymentNegation_notSettledFailsAgainstPaid() {
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "payment_status", "PAID"
        ));
        String response = "PUR-0001 payment is not settled.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertFalse(result.valid(), "Claiming 'not settled' when record is PAID must fail");
    }

    @Test
    @DisplayName("Edge-Case 2B: Payment negation 'not overdue' fails when record is OVERDUE")
    void testPaymentNegation_notOverdueFailsAgainstOverdue() {
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "payment_status", "OVERDUE"
        ));
        String response = "PUR-0001 payment is not overdue.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertFalse(result.valid(), "Claiming 'not overdue' when record is OVERDUE must fail");
    }

    @Test
    @DisplayName("Edge-Case 2C: Payment negation 'not pending' fails when record is PENDING")
    void testPaymentNegation_notPendingFailsAgainstPending() {
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "payment_status", "PENDING"
        ));
        String response = "PUR-0001 payment is not pending.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertFalse(result.valid(), "Claiming 'not pending' when record is PENDING must fail");
    }

    @Test
    @DisplayName("Edge-Case 2D: Positive 'unpaid' matches UNPAID, while 'not unpaid' fails against UNPAID")
    void testPaymentNegation_unpaidAndNotUnpaid() {
        var toolResultUnpaid = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "payment_status", "UNPAID"
        ));
        // Positive unpaid matches UNPAID
        String responseUnpaid = "PUR-0001 is unpaid.";
        assertTrue(validator.validateCitations(responseUnpaid, List.of(), true, toolResultUnpaid).valid(),
                "Positive 'unpaid' claim must match UNPAID record");

        // Negated unpaid must NOT match UNPAID record
        String responseNotUnpaid = "PUR-0001 is not unpaid.";
        assertFalse(validator.validateCitations(responseNotUnpaid, List.of(), true, toolResultUnpaid).valid(),
                "Negated 'not unpaid' claim must fail when record is UNPAID");
    }

    @Test
    @DisplayName("Edge-Case 2E: Contradictory payment claims in one sentence fail closed")
    void testPaymentContradiction_paidAndUnpaidFailsClosed() {
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "payment_status", "PAID"
        ));
        String response = "PUR-0001 is paid but also unpaid.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertFalse(result.valid(), "Contradictory payment assertions in the same sentence must fail closed");
    }

    @Test
    @DisplayName("Edge-Case 3A: Invoices resolving to different purchases in one sentence fail closed")
    void testMultipleInvoices_differentPurchasesFailsClosed() {
        var toolResult = List.of(
                java.util.Map.of("purchase_number", "PUR-0001", "supplier_invoice_no", "INV-1001", "purchase_date", "2026-11-10"),
                java.util.Map.of("purchase_number", "PUR-0002", "supplier_invoice_no", "INV-2002", "purchase_date", "2026-11-12")
        );
        String response = "Invoices INV-1001 and INV-2002 were processed on 2026-11-10.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertFalse(result.valid(), "Invoices belonging to different purchases in one clause must fail closed");
    }

    @Test
    @DisplayName("Edge-Case 3B: Invoices resolving to same purchase pass")
    void testMultipleInvoices_samePurchasePasses() {
        var toolResult = List.of(
                java.util.Map.of("purchase_number", "PUR-0001", "supplier_invoice_no", "INV-1001", "purchase_date", "2026-11-10")
        );
        String response = "Invoice INV-1001 was processed on 2026-11-10.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertTrue(result.valid(), "Single invoice resolving unambiguously must pass");
    }

    @Test
    @DisplayName("Edge-Case 4A: Fabricated purchase total matching policy chunk amount must fail")
    void testRagVsErpAmountCollision_fabricatedPurchaseTotalFailsEvenIfInPolicy() {
        RetrievedChunk feePolicyChunk = new RetrievedChunk(105L, UUID.randomUUID(), "POL-RET-01",
                "Return and Refund Policy", 1, "Restocking Fee",
                "A ₹100 restocking fee applies to special order parts.", 30, 0.95);

        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "purchase_date",   "2026-11-12",
                "grand_total",     450.00
        ));

        // The sentence claims purchase price was ₹100 (which is in the policy chunk, but NOT in PUR-0001 total which is ₹450)
        String response = "PUR-0001 was purchased for ₹100 and a ₹100 fee applies [Source: Return and Refund Policy v1, §Restocking Fee].";
        var result = validator.validateCitations(response, List.of(feePolicyChunk), true, toolResult);
        assertFalse(result.valid(), "Fabricated purchase total of ₹100 must fail against ERP record of ₹450, even if ₹100 exists in policy chunk");
    }

    @Test
    @DisplayName("Edge-Case 4B: Truthful purchase total with cited policy fee passes")
    void testRagVsErpAmountCollision_validPurchaseTotalWithCitedPolicyFeePasses() {
        RetrievedChunk feePolicyChunk = new RetrievedChunk(105L, UUID.randomUUID(), "POL-RET-01",
                "Return and Refund Policy", 1, "Restocking Fee",
                "A ₹100 restocking fee applies to special order parts.", 30, 0.95);

        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "purchase_date",   "2026-11-12",
                "grand_total",     450.00
        ));

        String response = "PUR-0001 was purchased for 450.00 and a ₹100 fee applies [Source: Return and Refund Policy v1, §Restocking Fee].";
        var result = validator.validateCitations(response, List.of(feePolicyChunk), true, toolResult);
        assertTrue(result.valid(), "Valid purchase total and valid policy fee must both pass");
    }

    @Test
    @DisplayName("Edge-Case 5: Long supplier legal name is accepted without truncation")
    void testLongSupplierLegalName_acceptedWithoutTruncation() {
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "supplier_name",   "ABC Automotive Components and Spares Pvt Ltd",
                "purchase_date",   "2026-11-10"
        ));
        String response = "PUR-0001 from supplier: ABC Automotive Components and Spares Pvt Ltd on 2026-11-10.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertTrue(result.valid(), "Long supplier legal name with literal 'supplier:' must match entity");
    }

    // -------------------------------------------------------------------------
    // Final Regression Coverage: Multiple Invoices, Payment Negation, Date Collisions
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Coverage 1A: Two invoices belonging to different purchases without dates or amounts must fail")
    void testMultipleInvoices_differentPurchasesWithoutDatesOrAmountsFailsClosed() {
        var toolResult = List.of(
                java.util.Map.of("purchase_number", "PUR-0001", "supplier_invoice_no", "INV-1001"),
                java.util.Map.of("purchase_number", "PUR-0002", "supplier_invoice_no", "INV-2002")
        );
        String response = "Invoices INV-1001 and INV-2002 were processed.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertFalse(result.valid(), "Two invoices from different purchases in one response must fail closed on ownership alone");
    }

    @Test
    @DisplayName("Coverage 1B: Two invoices belonging to the same purchase must pass")
    void testMultipleInvoices_samePurchaseMultipleInvoicesPasses() {
        var toolResult = List.of(
                java.util.Map.of("purchase_number", "PUR-0001", "supplier_invoice_no", "INV-1001"),
                java.util.Map.of("purchase_number", "PUR-0001", "supplier_invoice_no", "INV-1002")
        );
        String response = "Invoices INV-1001 and INV-1002 were processed.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertTrue(result.valid(), "Multiple invoices belonging to the same purchase must pass");
    }

    @Test
    @DisplayName("Coverage 1C: One valid invoice and one unknown invoice must fail")
    void testMultipleInvoices_oneValidOneUnknownInvoiceFails() {
        var toolResult = List.of(
                java.util.Map.of("purchase_number", "PUR-0001", "supplier_invoice_no", "INV-1001")
        );
        String response = "Invoices INV-1001 and INV-9999 were processed.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertFalse(result.valid(), "Response referencing an unknown invoice ID must fail");
    }

    @Test
    @DisplayName("Coverage 1D: An invoice with ambiguous ownership must fail")
    void testMultipleInvoices_ambiguousInvoiceOwnershipFails() {
        var toolResult = List.of(
                java.util.Map.of("purchase_number", "PUR-0001", "supplier_invoice_no", "INV-1001"),
                java.util.Map.of("purchase_number", "PUR-0002", "supplier_invoice_no", "INV-1001")
        );
        String response = "Invoice INV-1001 was processed.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertFalse(result.valid(), "Ambiguous invoice owned by multiple purchases must fail closed");
    }

    @Test
    @DisplayName("Coverage 1E: Purchase ID combined with an invoice ID belonging to another purchase must fail")
    void testMultipleInvoices_purchaseIdCombinedWithOtherPurchaseInvoiceFails() {
        var toolResult = List.of(
                java.util.Map.of("purchase_number", "PUR-0001", "supplier_invoice_no", "INV-1001"),
                java.util.Map.of("purchase_number", "PUR-0002", "supplier_invoice_no", "INV-2002")
        );
        String response = "PUR-0001 was billed under invoice INV-2002.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertFalse(result.valid(), "Mismatched purchase ID and invoice ID from different purchase must fail");
    }

    @Test
    @DisplayName("Coverage 2A: 'has not yet been paid' fails against PAID and passes against UNPAID")
    void testPaymentNegation_hasNotYetBeenPaidFailsAgainstPaidAndPassesAgainstUnpaid() {
        var toolResultPaid = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "payment_status", "PAID"
        ));
        String response = "PUR-0001 has not yet been paid.";
        var resultPaid = validator.validateCitations(response, List.of(), true, toolResultPaid);
        assertFalse(resultPaid.valid(), "Claiming 'has not yet been paid' when record is PAID must fail");

        var toolResultUnpaid = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "payment_status", "UNPAID"
        ));
        var resultUnpaid = validator.validateCitations(response, List.of(), true, toolResultUnpaid);
        assertTrue(resultUnpaid.valid(), "Claiming 'has not yet been paid' when record is UNPAID must pass");
    }

    @Test
    @DisplayName("Coverage 2B: 'payment wasn't settled' fails against PAID and passes against UNPAID")
    void testPaymentNegation_paymentWasntSettledFailsAgainstPaid() {
        var toolResultPaid = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "payment_status", "PAID"
        ));
        String response = "PUR-0001 payment wasn't settled.";
        var resultPaid = validator.validateCitations(response, List.of(), true, toolResultPaid);
        assertFalse(resultPaid.valid(), "Claiming 'payment wasn't settled' when record is PAID must fail");

        var toolResultUnpaid = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "payment_status", "UNPAID"
        ));
        var resultUnpaid = validator.validateCitations(response, List.of(), true, toolResultUnpaid);
        assertTrue(resultUnpaid.valid(), "Claiming 'payment wasn't settled' when record is UNPAID must pass");
    }

    @Test
    @DisplayName("Coverage 2C: 'payment has not been completed' fails against PAID and passes against UNPAID")
    void testPaymentNegation_paymentHasNotBeenCompletedFailsAgainstPaid() {
        var toolResultPaid = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "payment_status", "PAID"
        ));
        String response = "PUR-0001 payment has not been completed.";
        var resultPaid = validator.validateCitations(response, List.of(), true, toolResultPaid);
        assertFalse(resultPaid.valid(), "Claiming 'payment has not been completed' when record is PAID must fail");

        var toolResultUnpaid = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "payment_status", "UNPAID"
        ));
        var resultUnpaid = validator.validateCitations(response, List.of(), true, toolResultUnpaid);
        assertTrue(resultUnpaid.valid(), "Claiming 'payment has not been completed' when record is UNPAID must pass");
    }

    @Test
    @DisplayName("Coverage 2D: 'payment is not fully paid' fails against PAID and passes against UNPAID")
    void testPaymentNegation_paymentIsNotFullyPaidFailsAgainstPaid() {
        var toolResultPaid = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "payment_status", "PAID"
        ));
        String response = "PUR-0001 payment is not fully paid.";
        var resultPaid = validator.validateCitations(response, List.of(), true, toolResultPaid);
        assertFalse(resultPaid.valid(), "Claiming 'not fully paid' when record is PAID must fail");

        var toolResultUnpaid = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "payment_status", "UNPAID"
        ));
        var resultUnpaid = validator.validateCitations(response, List.of(), true, toolResultUnpaid);
        assertTrue(resultUnpaid.valid(), "Claiming 'not fully paid' when record is UNPAID must pass");
    }

    @Test
    @DisplayName("Coverage 2E: Positive PAID, UNPAID, and PARTIALLY_PAID claims match their respective statuses")
    void testPaymentPositive_exactStatusMatching() {
        var toolResultPaid = List.of(java.util.Map.of("purchase_number", "PUR-0001", "payment_status", "PAID"));
        var toolResultUnpaid = List.of(java.util.Map.of("purchase_number", "PUR-0001", "payment_status", "UNPAID"));
        var toolResultPartial = List.of(java.util.Map.of("purchase_number", "PUR-0001", "payment_status", "PARTIALLY_PAID"));

        // Positive PAID
        assertTrue(validator.validateCitations("PUR-0001 was fully paid.", List.of(), true, toolResultPaid).valid());
        assertFalse(validator.validateCitations("PUR-0001 was fully paid.", List.of(), true, toolResultUnpaid).valid());

        // Positive UNPAID
        assertTrue(validator.validateCitations("PUR-0001 is unpaid.", List.of(), true, toolResultUnpaid).valid());
        assertFalse(validator.validateCitations("PUR-0001 is unpaid.", List.of(), true, toolResultPaid).valid());

        // Positive PARTIALLY_PAID
        assertTrue(validator.validateCitations("PUR-0001 was partially paid.", List.of(), true, toolResultPartial).valid());
        assertFalse(validator.validateCitations("PUR-0001 was partially paid.", List.of(), true, toolResultPaid).valid());
    }

    @Test
    @DisplayName("Coverage 2F: Contradictory positive and negated claims in same sentence fail closed")
    void testPaymentContradiction_positiveAndNegatedClaimsFailClosed() {
        var toolResultPaid = List.of(java.util.Map.of("purchase_number", "PUR-0001", "payment_status", "PAID"));

        String response1 = "PUR-0001 is paid but has not yet been paid.";
        assertFalse(validator.validateCitations(response1, List.of(), true, toolResultPaid).valid(),
                "Contradiction between 'is paid' and 'has not yet been paid' must fail closed");

        String response2 = "PUR-0001 payment wasn't settled and was settled.";
        assertFalse(validator.validateCitations(response2, List.of(), true, toolResultPaid).valid(),
                "Contradiction between 'wasn't settled' and 'was settled' must fail closed");
    }

    @Test
    @DisplayName("Coverage 3A: Policy effective date differing from purchase date passes when cited")
    void testDateAttribution_policyEffectiveDateDiffersFromPurchaseDatePasses() {
        RetrievedChunk policyChunk = new RetrievedChunk(101L, UUID.randomUUID(), "POL-RET-01",
                "Return Policy", 1, "General",
                "Under policy effective 2025-06-01, returns require prior manager approval.", 30, 0.95);

        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "purchase_date", "2026-11-12",
                "grand_total", 450.00
        ));

        String response = "Under policy effective 2025-06-01, returns require prior manager approval [Source: Return Policy v1].";
        var result = validator.validateCitations(response, List.of(policyChunk), true, toolResult);
        assertTrue(result.valid(), "Policy effective date supported by cited chunk must pass even though it differs from purchase date");
    }

    @Test
    @DisplayName("Coverage 3B: Fabricated purchase date matching cited policy chunk date must fail against ERP record")
    void testDateCollision_fabricatedPurchaseDateMatchingCitedPolicyDateFails() {
        RetrievedChunk policyChunk = new RetrievedChunk(101L, UUID.randomUUID(), "POL-RET-01",
                "Return Policy", 1, "General",
                "Under policy effective 2025-06-01, returns require prior manager approval.", 30, 0.95);

        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "purchase_date", "2026-11-12",
                "grand_total", 450.00
        ));

        // Attempting to attribute policy date 2025-06-01 as the PUR-0001 purchase date
        String response = "PUR-0001 was purchased on 2025-06-01 under policy effective 2025-06-01 [Source: Return Policy v1].";
        var result = validator.validateCitations(response, List.of(policyChunk), true, toolResult);
        assertFalse(result.valid(), "Fabricated purchase date 2025-06-01 must fail against actual ERP purchase date 2026-11-12");
    }

    @Test
    @DisplayName("Coverage 3C: Correct purchase date combined with valid cited policy effective date passes")
    void testDateCollision_correctPurchaseDateWithValidCitedPolicyDatePasses() {
        RetrievedChunk policyChunk = new RetrievedChunk(101L, UUID.randomUUID(), "POL-RET-01",
                "Return Policy", 1, "General",
                "Under policy effective 2025-06-01, returns require prior manager approval.", 30, 0.95);

        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "purchase_date", "2026-11-12",
                "grand_total", 450.00
        ));

        String response = "PUR-0001 was purchased on 2026-11-12 and under policy effective 2025-06-01 returns require approval [Source: Return Policy v1].";
        var result = validator.validateCitations(response, List.of(policyChunk), true, toolResult);
        assertTrue(result.valid(), "True purchase date (2026-11-12) and true cited policy date (2025-06-01) must both pass");
    }

    @Test
    @DisplayName("Coverage 3D: Policy date without ERP transaction claim passes only when supported by chunk")
    void testDateAttribution_policyDateWithoutErpDateClaimPassesOnlyWhenSupported() {
        RetrievedChunk policyChunk = new RetrievedChunk(102L, UUID.randomUUID(), "POL-WAR-01",
                "Warranty Policy", 1, "General",
                "The warranty policy became effective on 2025-01-01 for all registered accounts.", 30, 0.95);

        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "purchase_date", "2026-11-12"
        ));

        // Supported policy date
        String validResponse = "The warranty policy became effective on 2025-01-01 [Source: Warranty Policy v1].";
        assertTrue(validator.validateCitations(validResponse, List.of(policyChunk), true, toolResult).valid(),
                "Supported policy date must pass");

        // Unsupported policy date
        String invalidResponse = "The warranty policy became effective on 2024-01-01 [Source: Warranty Policy v1].";
        assertFalse(validator.validateCitations(invalidResponse, List.of(policyChunk), true, toolResult).valid(),
                "Unsupported policy date must fail");
    }

    // -------------------------------------------------------------------------
    // Final Critical Hardening: Strict Date, Amount, and Negative Status Evidence
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Fix A-1: 'PUR-0001 was dated ...' fails against ERP when date is from cited policy")
    void testDateAttribution_datedWordingFailsAgainstErpMismatchEvenIfInPolicy() {
        RetrievedChunk policyChunk = new RetrievedChunk(101L, UUID.randomUUID(), "POL-RET-01",
                "Return Policy", 1, "General",
                "Under policy effective 2025-06-01, returns require prior manager approval.", 30, 0.95);

        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "purchase_date", "2026-11-12",
                "grand_total", 450.00
        ));

        String response = "PUR-0001 was dated 2025-06-01 under policy effective 2025-06-01 [Source: Return Policy v1].";
        var result = validator.validateCitations(response, List.of(policyChunk), true, toolResult);
        assertFalse(result.valid(), "PUR-0001 was dated 2025-06-01 must fail when ERP purchase date is 2026-11-12");
    }

    @Test
    @DisplayName("Fix A-2: 'PUR-0001 was bought on ...' fails against ERP when date is from cited policy")
    void testDateAttribution_boughtOnWordingFailsAgainstErpMismatchEvenIfInPolicy() {
        RetrievedChunk policyChunk = new RetrievedChunk(101L, UUID.randomUUID(), "POL-RET-01",
                "Return Policy", 1, "General",
                "Under policy effective 2025-06-01, returns require prior manager approval.", 30, 0.95);

        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "purchase_date", "2026-11-12",
                "grand_total", 450.00
        ));

        String response = "PUR-0001 was bought on 2025-06-01 under policy effective 2025-06-01 [Source: Return Policy v1].";
        var result = validator.validateCitations(response, List.of(policyChunk), true, toolResult);
        assertFalse(result.valid(), "PUR-0001 was bought on 2025-06-01 must fail when ERP purchase date is 2026-11-12");
    }

    @Test
    @DisplayName("Fix B-1: Claiming transaction total as policy fee amount fails against ERP record")
    void testAmountAttribution_totalWasPolicyFeeAmountFailsAgainstErp() {
        RetrievedChunk policyChunk = new RetrievedChunk(101L, UUID.randomUUID(), "POL-RET-01",
                "Return Policy", 1, "General",
                "A ₹100 restocking fee applies to returned items.", 30, 0.95);

        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "grand_total", 450.00
        ));

        String response = "PUR-0001 total was ₹100 including the restocking fee [Source: Return Policy v1].";
        var result = validator.validateCitations(response, List.of(policyChunk), true, toolResult);
        assertFalse(result.valid(), "Transaction total of ₹100 must fail against ERP record of ₹450 even if fee is ₹100");
    }

    @Test
    @DisplayName("Fix B-2: Correctly stated purchase total and restocking fee passes")
    void testAmountAttribution_correctPurchaseTotalAndFeePasses() {
        RetrievedChunk policyChunk = new RetrievedChunk(101L, UUID.randomUUID(), "POL-RET-01",
                "Return Policy", 1, "General",
                "A ₹100 restocking fee applies to returned items.", 30, 0.95);

        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "grand_total", 450.00
        ));

        String response = "PUR-0001 total was ₹450 and restocking fee was ₹100 [Source: Return Policy v1].";
        var result = validator.validateCitations(response, List.of(policyChunk), true, toolResult);
        assertTrue(result.valid(), "True purchase total and true policy fee must both pass");
    }

    @Test
    @DisplayName("Fix B-3: Identical numerical values between ERP and policy chunk must not authorize fabricated total")
    void testAmountAttribution_identicalNumericValuesDoNotAuthorizeFabricatedTotal() {
        RetrievedChunk policyChunk = new RetrievedChunk(101L, UUID.randomUUID(), "POL-RET-01",
                "Return Policy", 1, "General",
                "A ₹450 restocking fee applies to major assemblies.", 30, 0.95);

        // ERP record total is ₹300, NOT ₹450
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "grand_total", 300.00
        ));

        // Claiming PUR-0001 total was ₹450 because the policy chunk has ₹450
        String response = "PUR-0001 total was ₹450 and restocking fee was ₹450 [Source: Return Policy v1].";
        var result = validator.validateCitations(response, List.of(policyChunk), true, toolResult);
        assertFalse(result.valid(), "Fabricated ERP total of ₹450 must fail against ERP total of ₹300 even if policy has ₹450");
    }

    @Test
    @DisplayName("Fix C-1: PROCESSING payment status (UNKNOWN) must fail negative payment claim")
    void testPaymentNegation_processingStatusFailsNegativeClaim() {
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "payment_status", "PROCESSING"
        ));
        String response = "PUR-0001 payment is not settled.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertFalse(result.valid(), "PROCESSING payment status resolves to UNKNOWN and must fail negative payment claim");
    }

    @Test
    @DisplayName("Fix C-2: Missing payment status must fail negative payment claim")
    void testPaymentNegation_missingStatusFailsNegativeClaim() {
        // Record without any payment_status field
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "grand_total", 450.00
        ));
        String response = "PUR-0001 payment is not settled.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertFalse(result.valid(), "Missing payment status must fail closed on negative payment claim");
    }

    @Test
    @DisplayName("Fix C-3: UNKNOWN payment status must fail 'payment is not overdue'")
    void testPaymentNegation_unknownStatusFailsNotOverdueClaim() {
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "payment_status", "UNKNOWN_STATUS"
        ));
        String response = "PUR-0001 payment is not overdue.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertFalse(result.valid(), "UNKNOWN payment status cannot authorize 'is not overdue'");
    }

    @Test
    @DisplayName("Fix C-4: PAID status proves 'is not overdue' but fails 'is not settled'")
    void testPaymentNegation_paidStatusProvesNotOverdueButFailsNotSettled() {
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "payment_status", "PAID"
        ));

        // PAID contradicts 'is not settled' -> FAIL
        String responseNotSettled = "PUR-0001 payment is not settled.";
        assertFalse(validator.validateCitations(responseNotSettled, List.of(), true, toolResult).valid(),
                "PAID status contradicts 'is not settled'");

        // PAID proves 'is not overdue' -> PASS
        String responseNotOverdue = "PUR-0001 payment is not overdue.";
        assertTrue(validator.validateCitations(responseNotOverdue, List.of(), true, toolResult).valid(),
                "PAID status explicitly establishes 'is not overdue'");
    }

    @Test
    @DisplayName("Fix C-5: UNPAID status proves 'is not settled'")
    void testPaymentNegation_unpaidStatusProvesNotSettled() {
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "payment_status", "UNPAID"
        ));
        String response = "PUR-0001 payment is not settled.";
        assertTrue(validator.validateCitations(response, List.of(), true, toolResult).valid(),
                "UNPAID status explicitly proves 'is not settled'");
    }

    // =========================================================================
    // Regression Tests: Payment-Status Field Attribution
    // =========================================================================

    @Test
    @DisplayName("Payment Attribution 1: invoice_status=PAID must not authorize a PAID claim")
    void testPaymentStatus_invoiceStatusPaidDoesNotAuthorizePaymentClaim() {
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "invoice_status", "PAID"
        ));
        String response = "PUR-0001 has been fully paid.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertFalse(result.valid(), "invoice_status represents invoice lifecycle and must NOT authorize a payment claim");
    }

    @Test
    @DisplayName("Payment Attribution 2: order_status=PAID must not authorize a PAID claim")
    void testPaymentStatus_orderStatusPaidDoesNotAuthorizePaymentClaim() {
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "order_status", "PAID"
        ));
        String response = "PUR-0001 has been paid.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertFalse(result.valid(), "order_status represents order lifecycle and must NOT authorize a payment claim");
    }

    @Test
    @DisplayName("Payment Attribution 3: status=RECEIVED must not authorize a PAID claim")
    void testPaymentStatus_statusReceivedDoesNotAuthorizePaymentClaim() {
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "status", "RECEIVED"
        ));
        String response = "PUR-0001 payment is completed.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertFalse(result.valid(), "status=RECEIVED does not imply payment and must fail closed on payment claims");
    }

    @Test
    @DisplayName("Payment Attribution 4: Explicit payment_status=PAID authorizes a valid PAID claim")
    void testPaymentStatus_explicitPaymentStatusPaidAuthorizesPaidClaim() {
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "payment_status", "PAID"
        ));
        String response = "PUR-0001 has been fully paid.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertTrue(result.valid(), "Explicit payment_status=PAID must authorize a valid PAID claim");
    }

    @Test
    @DisplayName("Payment Attribution 5: Conflicting candidate payment fields fail closed")
    void testPaymentStatus_conflictingPaymentFieldsFailClosed() {
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "payment_status", "PAID",
                "payment_state", "UNPAID"
        ));
        String response = "PUR-0001 has been fully paid.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertFalse(result.valid(), "Conflicting payment fields (PAID vs UNPAID) must fail closed");
    }

    @Test
    @DisplayName("Payment Attribution 6: Reversing map insertion order yields identical validation outcome")
    void testPaymentStatus_reversingMapInsertionOrderYieldsSameResult() {
        // Conflicting fields in order A
        var mapA = new java.util.LinkedHashMap<String, Object>();
        mapA.put("payment_status", "PAID");
        mapA.put("payment_state", "UNPAID");
        mapA.put("purchase_number", "PUR-0001");

        // Conflicting fields in order B
        var mapB = new java.util.LinkedHashMap<String, Object>();
        mapB.put("payment_state", "UNPAID");
        mapB.put("payment_status", "PAID");
        mapB.put("purchase_number", "PUR-0001");

        String response = "PUR-0001 has been fully paid.";
        assertFalse(validator.validateCitations(response, List.of(), true, List.of(mapA)).valid(),
                "Map A must fail due to conflicting payment statuses");
        assertFalse(validator.validateCitations(response, List.of(), true, List.of(mapB)).valid(),
                "Map B must fail identically regardless of map insertion order");

        // Consistent fields in order C and D
        var mapC = new java.util.LinkedHashMap<String, Object>();
        mapC.put("payment_status", "PAID");
        mapC.put("payment_state", "PAID");
        mapC.put("purchase_number", "PUR-0001");

        var mapD = new java.util.LinkedHashMap<String, Object>();
        mapD.put("payment_state", "PAID");
        mapD.put("payment_status", "PAID");
        mapD.put("purchase_number", "PUR-0001");

        assertTrue(validator.validateCitations(response, List.of(), true, List.of(mapC)).valid(),
                "Map C must pass with consistent payment statuses");
        assertTrue(validator.validateCitations(response, List.of(), true, List.of(mapD)).valid(),
                "Map D must pass identically regardless of map insertion order");
    }

    // =========================================================================
    // Regression Tests: Monetary Evidence Attribution by Field Meaning
    // =========================================================================

    @Test
    @DisplayName("Monetary Attribution 1: Purchase total ₹450 passes when grand_total is 450")
    void testMonetaryAttribution_purchaseTotal450Passes() {
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "grand_total", 450.00,
                "paid_amount", 100.00,
                "balance", 350.00,
                "refund_amount", 50.00
        ));
        String response = "PUR-0001 purchase total was ₹450.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertTrue(result.valid(), "Claim of purchase total ₹450 must match grand_total evidence");
    }

    @Test
    @DisplayName("Monetary Attribution 2: Purchase total ₹100 fails even though paid_amount is 100")
    void testMonetaryAttribution_purchaseTotal100Fails() {
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "grand_total", 450.00,
                "paid_amount", 100.00,
                "balance", 350.00,
                "refund_amount", 50.00
        ));
        String response = "PUR-0001 purchase total was ₹100.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertFalse(result.valid(), "Claim of purchase total ₹100 must fail when grand_total is 450 (100 is paid amount)");
    }

    @Test
    @DisplayName("Monetary Attribution 3: Paid amount ₹100 passes against paid_amount evidence")
    void testMonetaryAttribution_paidAmount100Passes() {
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "grand_total", 450.00,
                "paid_amount", 100.00,
                "balance", 350.00,
                "refund_amount", 50.00
        ));
        String response = "For PUR-0001, amount paid was ₹100.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertTrue(result.valid(), "Claim of amount paid ₹100 must match paid_amount evidence");
    }

    @Test
    @DisplayName("Monetary Attribution 4: Outstanding balance ₹350 passes against balance evidence")
    void testMonetaryAttribution_outstandingBalance350Passes() {
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "grand_total", 450.00,
                "paid_amount", 100.00,
                "balance", 350.00,
                "refund_amount", 50.00
        ));
        String response = "For PUR-0001, the outstanding balance is ₹350.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertTrue(result.valid(), "Claim of outstanding balance ₹350 must match balance evidence");
    }

    @Test
    @DisplayName("Monetary Attribution 5: Refund amount ₹50 passes against refund_amount evidence")
    void testMonetaryAttribution_refundAmount50Passes() {
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "grand_total", 450.00,
                "paid_amount", 100.00,
                "balance", 350.00,
                "refund_amount", 50.00
        ));
        String response = "For PUR-0001, the refund amount was ₹50.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertTrue(result.valid(), "Claim of refund amount ₹50 must match refund_amount evidence");
    }

    @Test
    @DisplayName("Monetary Attribution 6: Conflicting grand totals across records fail closed")
    void testMonetaryAttribution_conflictingGrandTotalsFailClosed() {
        var toolResult = List.of(
                java.util.Map.of("purchase_number", "PUR-0001", "grand_total", 450.00),
                java.util.Map.of("purchase_number", "PUR-0001", "grand_total", 600.00)
        );
        String response = "PUR-0001 purchase total was ₹450.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertFalse(result.valid(), "Conflicting grand totals (450 vs 600) for the same purchase must fail closed");
    }

    // =========================================================================
    // Regression Tests: Date Evidence Attribution by Field Meaning
    // =========================================================================

    @Test
    @DisplayName("Date Attribution 1: Purchase date matching only invoice_date fails")
    void testDateAttribution_purchaseDateMatchingInvoiceDateFails() {
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "purchase_date", "2026-11-10",
                "invoice_date", "2026-11-12",
                "received_date", "2026-11-15"
        ));
        String response = "PUR-0001 was purchased on 2026-11-12.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertFalse(result.valid(), "Claim of purchase date 2026-11-12 must fail when that date belongs only to invoice_date");
    }

    @Test
    @DisplayName("Date Attribution 2: Purchase date matching only received_date fails")
    void testDateAttribution_purchaseDateMatchingReceivedDateFails() {
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "purchase_date", "2026-11-10",
                "invoice_date", "2026-11-12",
                "received_date", "2026-11-15"
        ));
        String response = "PUR-0001 was purchased on 2026-11-15.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertFalse(result.valid(), "Claim of purchase date 2026-11-15 must fail when that date belongs only to received_date");
    }

    @Test
    @DisplayName("Date Attribution 3: Correct purchase date passes")
    void testDateAttribution_correctPurchaseDatePasses() {
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "purchase_date", "2026-11-10",
                "invoice_date", "2026-11-12",
                "received_date", "2026-11-15"
        ));
        String response = "PUR-0001 was purchased on 2026-11-10.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertTrue(result.valid(), "Claim of purchase date 2026-11-10 must pass against purchase_date evidence");
    }

    @Test
    @DisplayName("Date Attribution 4: Correct invoice date passes when claimed as invoice date")
    void testDateAttribution_correctInvoiceDatePasses() {
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "invoice_number", "INV-9001",
                "purchase_date", "2026-11-10",
                "invoice_date", "2026-11-12",
                "received_date", "2026-11-15"
        ));
        String response = "Invoice INV-9001 date was 2026-11-12.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertTrue(result.valid(), "Claim of invoice date 2026-11-12 must pass against invoice_date evidence");
    }

    @Test
    @DisplayName("Date Attribution 5: Conflicting purchase dates across records fail closed")
    void testDateAttribution_conflictingPurchaseDatesFailClosed() {
        var toolResult = List.of(
                java.util.Map.of("purchase_number", "PUR-0001", "purchase_date", "2026-11-10"),
                java.util.Map.of("purchase_number", "PUR-0001", "purchase_date", "2026-11-11")
        );
        String response = "PUR-0001 was purchased on 2026-11-10.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertFalse(result.valid(), "Conflicting purchase dates (2026-11-10 vs 2026-11-11) must fail closed");
    }

    // =========================================================================
    // Regression Tests: Strict Semantic Evidence Isolation (No Fallbacks)
    // =========================================================================

    @Test
    @DisplayName("Date Isolation 1: Only invoice_date=2026-11-12; purchase-date claim for that date must fail")
    void testDateIsolation_onlyInvoiceDateFailsPurchaseDateClaim() {
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "invoice_date", "2026-11-12"
        ));
        String response = "PUR-0001 was purchased on 2026-11-12.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertFalse(result.valid(), "Only invoice_date=2026-11-12 present; purchase-date claim for that date must fail");
    }

    @Test
    @DisplayName("Date Isolation 2: Only received_date=2026-11-15; purchase-date claim for that date must fail")
    void testDateIsolation_onlyReceivedDateFailsPurchaseDateClaim() {
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "received_date", "2026-11-15"
        ));
        String response = "PUR-0001 was purchased on 2026-11-15.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertFalse(result.valid(), "Only received_date=2026-11-15 present; purchase-date claim for that date must fail");
    }

    @Test
    @DisplayName("Date Isolation 3: Only payment_date=2026-11-20; purchase-date claim for that date must fail")
    void testDateIsolation_onlyPaymentDateFailsPurchaseDateClaim() {
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "payment_date", "2026-11-20"
        ));
        String response = "PUR-0001 was purchased on 2026-11-20.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertFalse(result.valid(), "Only payment_date=2026-11-20 present; purchase-date claim for that date must fail");
    }

    @Test
    @DisplayName("Date Isolation 4: Only purchase_date=2026-11-10; invoice-date claim for that date must fail")
    void testDateIsolation_onlyPurchaseDateFailsInvoiceDateClaim() {
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "invoice_number", "INV-9001",
                "purchase_date", "2026-11-10"
        ));
        String response = "Invoice INV-9001 date was 2026-11-10.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertFalse(result.valid(), "Only purchase_date=2026-11-10 present; invoice-date claim for that date must fail");
    }

    @Test
    @DisplayName("Date Isolation 5: Missing relevant date category must fail")
    void testDateIsolation_missingRelevantDateCategoryFails() {
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "grand_total", 450.00
        ));
        String response = "PUR-0001 was purchased on 2026-11-10.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertFalse(result.valid(), "Missing purchase_date evidence must fail closed for purchase-date claim");
    }

    @Test
    @DisplayName("Date Isolation 6: Correct matching date category must pass")
    void testDateIsolation_correctMatchingDateCategoryPasses() {
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "purchase_date", "2026-11-10"
        ));
        String response = "PUR-0001 was purchased on 2026-11-10.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertTrue(result.valid(), "Correct matching purchase_date evidence must pass");
    }

    @Test
    @DisplayName("Monetary Isolation 1: Only paid_amount=100; grand-total claim of 100 must fail")
    void testMonetaryIsolation_onlyPaidAmountFailsGrandTotalClaim() {
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "paid_amount", 100.00
        ));
        String response = "PUR-0001 purchase total was ₹100.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertFalse(result.valid(), "Only paid_amount=100 present; grand-total claim of 100 must fail");
    }

    @Test
    @DisplayName("Monetary Isolation 2: Only balance=350; grand-total claim of 350 must fail")
    void testMonetaryIsolation_onlyBalanceFailsGrandTotalClaim() {
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "balance", 350.00
        ));
        String response = "PUR-0001 purchase total was ₹350.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertFalse(result.valid(), "Only balance=350 present; grand-total claim of 350 must fail");
    }

    @Test
    @DisplayName("Monetary Isolation 3: Only refund_amount=50; grand-total claim of 50 must fail")
    void testMonetaryIsolation_onlyRefundAmountFailsGrandTotalClaim() {
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "refund_amount", 50.00
        ));
        String response = "PUR-0001 purchase total was ₹50.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertFalse(result.valid(), "Only refund_amount=50 present; grand-total claim of 50 must fail");
    }

    @Test
    @DisplayName("Monetary Isolation 4: Only grand_total=450; paid-amount claim of 450 must fail")
    void testMonetaryIsolation_onlyGrandTotalFailsPaidAmountClaim() {
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "grand_total", 450.00
        ));
        String response = "For PUR-0001, amount paid was ₹450.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertFalse(result.valid(), "Only grand_total=450 present; paid-amount claim of 450 must fail");
    }

    @Test
    @DisplayName("Monetary Isolation 5: tax_total=100 must not establish grand total of 100")
    void testMonetaryIsolation_taxTotalDoesNotEstablishGrandTotal() {
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "tax_total", 100.00
        ));
        String response = "PUR-0001 purchase total was ₹100.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertFalse(result.valid(), "tax_total=100 must not establish purchase grand total of 100");
    }

    @Test
    @DisplayName("Monetary Isolation 6: Generic amount=100 must not establish grand total without an explicitly supported semantic mapping")
    void testMonetaryIsolation_genericAmountDoesNotEstablishGrandTotal() {
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "amount", 100.00
        ));
        String response = "PUR-0001 purchase total was ₹100.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertFalse(result.valid(), "Generic amount=100 without semantic mapping must not establish grand total");
    }

    @Test
    @DisplayName("Monetary Isolation 7: Correct semantic category must pass")
    void testMonetaryIsolation_correctSemanticCategoryPasses() {
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "grand_total", 450.00
        ));
        String response = "PUR-0001 purchase total was ₹450.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertTrue(result.valid(), "Matching grand_total evidence must pass");
    }

    @Test
    @DisplayName("Monetary Isolation 8: Missing relevant category must fail")
    void testMonetaryIsolation_missingRelevantCategoryFails() {
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "purchase_date", "2026-11-10"
        ));
        String response = "PUR-0001 purchase total was ₹450.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertFalse(result.valid(), "Missing grand_total evidence must fail closed for grand-total claim");
    }


    // =========================================================================
    // Regression Tests: Neutral-Context Isolation & Explicit Balance Aliases
    // =========================================================================

    @Test
    @DisplayName("Neutral Date Isolation 1: Only invoice_date exists; neutral date claim must fail")
    void testNeutralDateIsolation_onlyInvoiceDateCannotAuthorizeNeutralDateClaim() {
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "invoice_date", "2026-11-12"
        ));
        String response = "The date was 2026-11-12.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertFalse(result.valid(), "Neutral date claim must not validate against invoice_date via union fallback");
    }

    @Test
    @DisplayName("Neutral Date Isolation 2: Only received_date exists; neutral date claim must fail")
    void testNeutralDateIsolation_onlyReceivedDateCannotAuthorizeNeutralDateClaim() {
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "received_date", "2026-11-15"
        ));
        String response = "The date was 2026-11-15.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertFalse(result.valid(), "Neutral date claim must not validate against received_date via union fallback");
    }

    @Test
    @DisplayName("Neutral Date Isolation 3: Only payment_date exists; neutral date claim must fail")
    void testNeutralDateIsolation_onlyPaymentDateCannotAuthorizeNeutralDateClaim() {
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "payment_date", "2026-11-20"
        ));
        String response = "The date was 2026-11-20.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertFalse(result.valid(), "Neutral date claim must not validate against payment_date via union fallback");
    }

    @Test
    @DisplayName("Neutral Date Isolation 4: Generic 'date' key does not establish a purchase date")
    void testNeutralDateIsolation_genericDateKeyDoesNotEstablishPurchaseDate() {
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "date", "2026-11-10"
        ));
        assertFalse(validator.validateCitations("PUR-0001 was purchased on 2026-11-10.", List.of(), true, toolResult).valid(),
                "Ambiguous generic 'date' key must be classified as otherDates, not purchaseDates");
        assertFalse(validator.validateCitations("The date was 2026-11-10.", List.of(), true, toolResult).valid(),
                "Neutral date claim must fail closed against ambiguous generic 'date' key");
    }

    @Test
    @DisplayName("Neutral Date Isolation 5: Explicit purchase, invoice, received, and payment date claims pass")
    void testNeutralDateIsolation_explicitDateCategoriesContinueToPass() {
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "invoice_number", "INV-9001",
                "purchase_date", "2026-11-10",
                "invoice_date", "2026-11-12",
                "received_date", "2026-11-15",
                "payment_date", "2026-11-20",
                "payment_status", "PAID"
        ));
        assertTrue(validator.validateCitations("PUR-0001 was purchased on 2026-11-10.", List.of(), true, toolResult).valid());
        assertTrue(validator.validateCitations("Invoice INV-9001 date was 2026-11-12.", List.of(), true, toolResult).valid());
        assertTrue(validator.validateCitations("PUR-0001 was delivered on 2026-11-15.", List.of(), true, toolResult).valid());
        assertTrue(validator.validateCitations("PUR-0001 was paid on 2026-11-20.", List.of(), true, toolResult).valid());
    }

    @Test
    @DisplayName("Neutral Monetary Isolation 1: Only paid_amount=100 exists; neutral amount claim must fail")
    void testNeutralMonetaryIsolation_onlyPaidAmountCannotAuthorizeNeutralAmountClaim() {
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "paid_amount", 100.00
        ));
        String response = "The figure was ₹100.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertFalse(result.valid(), "Neutral monetary claim must not validate against paid_amount via union fallback");
    }

    @Test
    @DisplayName("Neutral Monetary Isolation 2: Only balance=350 exists; neutral amount claim must fail")
    void testNeutralMonetaryIsolation_onlyBalanceCannotAuthorizeNeutralAmountClaim() {
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "balance", 350.00
        ));
        String response = "The figure was ₹350.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertFalse(result.valid(), "Neutral monetary claim must not validate against balance via union fallback");
    }

    @Test
    @DisplayName("Neutral Monetary Isolation 3: Only refund_amount=50 exists; neutral amount claim must fail")
    void testNeutralMonetaryIsolation_onlyRefundAmountCannotAuthorizeNeutralAmountClaim() {
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "refund_amount", 50.00
        ));
        String response = "The figure was ₹50.";
        var result = validator.validateCitations(response, List.of(), true, toolResult);
        assertFalse(result.valid(), "Neutral monetary claim must not validate against refund_amount via union fallback");
    }

    @Test
    @DisplayName("Neutral Monetary Isolation 4: Excluded monetary aliases cannot establish grand total or neutral claim")
    void testNeutralMonetaryIsolation_excludedMonetaryAliasesCannotEstablishGrandTotal() {
        for (String excludedKey : List.of("tax_total", "shipping_total", "discount_total", "unit_price")) {
            var toolResult = List.of(java.util.Map.of(
                    "purchase_number", "PUR-0001",
                    excludedKey, 120.00
            ));
            assertFalse(validator.validateCitations("PUR-0001 purchase total was ₹120.", List.of(), true, toolResult).valid(),
                    excludedKey + " must not establish purchase grand total");
            assertFalse(validator.validateCitations("The figure was ₹120.", List.of(), true, toolResult).valid(),
                    excludedKey + " must not authorize a neutral monetary claim");
        }
    }

    @Test
    @DisplayName("Neutral Monetary Isolation 5: Generic 'amount' cannot establish grand total or neutral claim")
    void testNeutralMonetaryIsolation_genericAmountCannotEstablishGrandTotalOrNeutralClaim() {
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "amount", 100.00
        ));
        assertFalse(validator.validateCitations("PUR-0001 purchase total was ₹100.", List.of(), true, toolResult).valid(),
                "Generic 'amount' must not establish grand total");
        assertFalse(validator.validateCitations("The figure was ₹100.", List.of(), true, toolResult).valid(),
                "Generic 'amount' must not authorize a neutral monetary claim");
    }

    @Test
    @DisplayName("Neutral Monetary Isolation 6: Explicit monetary categories continue to pass")
    void testNeutralMonetaryIsolation_explicitMonetaryCategoriesContinueToPass() {
        var toolResult = List.of(java.util.Map.of(
                "purchase_number", "PUR-0001",
                "grand_total", 450.00,
                "subtotal", 400.00,
                "paid_amount", 100.00,
                "balance", 350.00,
                "refund_amount", 50.00
        ));
        assertTrue(validator.validateCitations("PUR-0001 purchase total was ₹450.", List.of(), true, toolResult).valid());
        assertTrue(validator.validateCitations("For PUR-0001, the subtotal was ₹400.", List.of(), true, toolResult).valid());
        assertTrue(validator.validateCitations("For PUR-0001, amount paid was ₹100.", List.of(), true, toolResult).valid());
        assertTrue(validator.validateCitations("For PUR-0001, the outstanding balance is ₹350.", List.of(), true, toolResult).valid());
        assertTrue(validator.validateCitations("For PUR-0001, the refund amount was ₹50.", List.of(), true, toolResult).valid());
    }

    @Test
    @DisplayName("Balance Aliases 1: All supported balance aliases are categorized correctly")
    void testBalanceAliases_supportedAliasesAreCategorizedCorrectly() {
        for (String alias : List.of(
                "balance", "balance_amount", "balanceamount", "amount_due", "amountdue", "due_amount", "dueamount",
                "outstanding_balance", "outstandingbalance", "current_balance", "currentbalance",
                "outstanding", "balance_due", "balancedue", "remaining_balance", "remainingbalance",
                "outstanding_amount", "outstandingamount")) {
            var toolResult = List.of(java.util.Map.of(
                    "purchase_number", "PUR-0001",
                    alias, 350.00
            ));
            assertTrue(validator.validateCitations("For PUR-0001, the outstanding balance is ₹350.", List.of(), true, toolResult).valid(),
                    "Supported balance alias '" + alias + "' must authorize a balance claim");
            assertFalse(validator.validateCitations("PUR-0001 purchase total was ₹350.", List.of(), true, toolResult).valid(),
                    "Supported balance alias '" + alias + "' must not authorize a grand-total claim");
        }
    }

    @Test
    @DisplayName("Balance Aliases 2: Unrelated numeric keys containing 'due' or 'balance' are not categorized as balances")
    void testBalanceAliases_unrelatedNumericKeysContainingDueOrBalanceAreRejected() {
        for (String unrelatedKey : List.of(
                "due_days", "days_past_due", "balance_sheet_id",
                "balance_version", "overdue_count", "outstanding_items_count")) {
            var toolResult = List.of(java.util.Map.of(
                    "purchase_number", "PUR-0001",
                    unrelatedKey, 350.00
            ));
            assertFalse(validator.validateCitations("For PUR-0001, the outstanding balance is ₹350.", List.of(), true, toolResult).valid(),
                    "Unrelated numeric key '" + unrelatedKey + "' must not be classified as a balance");
        }
    }

    private static long countOccurrences(String text, String value) {
        return (text.length() - text.replace(value, "").length()) / value.length();
    }
}
