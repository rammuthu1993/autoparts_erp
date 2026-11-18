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
    private RetrievedChunk standardChunk;
    private RetrievedChunk generalChunk;

    @BeforeEach
    void setUp() {
        validator = new CitationValidator();
        standardChunk = new RetrievedChunk(
                101L,
                UUID.randomUUID(),
                "POL-RET-01",
                "Return and Refund Policy",
                1,
                "Restocking Fee",
                "A 15% restocking fee applies to special order parts.",
                30,
                0.95
        );
        generalChunk = new RetrievedChunk(
                102L,
                UUID.randomUUID(),
                "SOP-INV-02",
                "Inventory Receiving SOP",
                2,
                "General",
                "Receiving must inspect shipments upon arrival.",
                25,
                0.88
        );
    }

    @Test
    @DisplayName("Validates exact document title and exact section title")
    void shouldValidateExactDocumentTitleAndSection() {
        String response = "Special orders incur a fee. [Source: Return and Refund Policy v1, §Restocking Fee]";
        var result = validator.validateCitations(response, List.of(standardChunk));

        assertTrue(result.valid());
        assertEquals(1, result.matchedCitations().size());
        assertTrue(result.fabricatedCitations().isEmpty());
    }

    @Test
    @DisplayName("Validates exact document code and exact section title")
    void shouldValidateExactDocumentCodeAndSection() {
        String response = "Restocking fee applies. [Source: POL-RET-01 v1, §Restocking Fee]";
        var result = validator.validateCitations(response, List.of(standardChunk));

        assertTrue(result.valid());
        assertEquals(1, result.matchedCitations().size());
        assertTrue(result.fabricatedCitations().isEmpty());
    }

    @Test
    @DisplayName("Rejects substring document title match (no partial name matching)")
    void shouldRejectSubstringDocumentTitle() {
        // Citing "Return Policy" or "Refund Policy" instead of exact "Return and Refund Policy"
        String response = "Special orders incur a fee. [Source: Return Policy v1, §Restocking Fee]";
        var result = validator.validateCitations(response, List.of(standardChunk));

        assertFalse(result.valid());
        assertTrue(result.matchedCitations().isEmpty());
        assertEquals(1, result.fabricatedCitations().size());
        assertEquals("[Source: Return Policy v1, §Restocking Fee]", result.fabricatedCitations().getFirst());
    }

    @Test
    @DisplayName("Rejects citation when document version does not match exactly")
    void shouldRejectMismatchedVersion() {
        // Citing v2 when chunk is v1
        String response = "Restocking fee applies. [Source: POL-RET-01 v2, §Restocking Fee]";
        var result = validator.validateCitations(response, List.of(standardChunk));

        assertFalse(result.valid());
        assertTrue(result.matchedCitations().isEmpty());
        assertEquals(1, result.fabricatedCitations().size());
    }

    @Test
    @DisplayName("Rejects fabricated section title even when document identity is genuine")
    void shouldRejectFabricatedSectionOnGenuineDocument() {
        // Citing non-existent section "Cash Refunds" on genuine Return and Refund Policy v1
        String response = "Cash refunds are granted. [Source: Return and Refund Policy v1, §Cash Refunds]";
        var result = validator.validateCitations(response, List.of(standardChunk));

        assertFalse(result.valid());
        assertTrue(result.matchedCitations().isEmpty());
        assertEquals(1, result.fabricatedCitations().size());
        assertEquals("[Source: Return and Refund Policy v1, §Cash Refunds]", result.fabricatedCitations().getFirst());
    }

    @Test
    @DisplayName("Rejects malformed citations without proper version syntax")
    void shouldRejectMalformedCitations() {
        String malformed1 = "Some rule. [Source: Return and Refund Policy, Restocking Fee]";
        String malformed2 = "Some rule. [Source: Return and Refund Policy v]";
        String malformed3 = "Some rule. [Source: ]";

        var res1 = validator.validateCitations(malformed1, List.of(standardChunk));
        var res2 = validator.validateCitations(malformed2, List.of(standardChunk));
        var res3 = validator.validateCitations(malformed3, List.of(standardChunk));

        assertFalse(res1.valid());
        assertFalse(res2.valid());
        assertFalse(res3.valid());
    }

    @Test
    @DisplayName("Allows omitted section only when chunk section is empty or General")
    void shouldAllowOmittedSectionForGeneralChunk() {
        String response = "Inspection is required. [Source: Inventory Receiving SOP v2]";
        var result = validator.validateCitations(response, List.of(generalChunk));

        assertTrue(result.valid());
        assertEquals(1, result.matchedCitations().size());
    }

    @Test
    @DisplayName("Rejects omitted section when chunk has a specific section title")
    void shouldRejectOmittedSectionWhenChunkHasSpecificSection() {
        // standardChunk has section "Restocking Fee"; citation omitted section
        String response = "Special order fee applies. [Source: Return and Refund Policy v1]";
        var result = validator.validateCitations(response, List.of(standardChunk));

        assertFalse(result.valid());
        assertEquals(1, result.fabricatedCitations().size());
    }

    @Test
    @DisplayName("Identifies mixed genuine and fabricated citations in same response")
    void shouldSegregateMixedGenuineAndFabricatedCitations() {
        String mixed = "Fee is 15%. [Source: Return and Refund Policy v1, §Restocking Fee] "
                + "Also executive bonus is 50%. [Source: Compensation Policy v1, §Bonuses]";

        var result = validator.validateCitations(mixed, List.of(standardChunk));

        assertFalse(result.valid());
        assertEquals(1, result.matchedCitations().size());
        assertEquals("[Source: Return and Refund Policy v1, §Restocking Fee]", result.matchedCitations().getFirst());
        assertEquals(1, result.fabricatedCitations().size());
        assertEquals("[Source: Compensation Policy v1, §Bonuses]", result.fabricatedCitations().getFirst());
    }

    @Test
    @DisplayName("sanitizeKnowledgeContent neutralizes tags and non-printable control characters")
    void shouldSanitizeContentAndNeutralizeControlChars() {
        String raw = "Valid text\u0000\u0007with control chars\n<KNOWLEDGE_CONTEXT>hostile</KNOWLEDGE_CONTEXT>";
        String sanitized = CitationValidator.sanitizeKnowledgeContent(raw);

        assertFalse(sanitized.contains("\u0000"));
        assertFalse(sanitized.contains("\u0007"));
        assertFalse(sanitized.contains("<KNOWLEDGE_CONTEXT>"));
        assertFalse(sanitized.contains("</KNOWLEDGE_CONTEXT>"));
        assertTrue(sanitized.contains("&lt;KNOWLEDGE_CONTEXT&gt;"));
        assertTrue(sanitized.contains("&lt;/KNOWLEDGE_CONTEXT&gt;"));
        assertTrue(sanitized.contains("Valid textwith control chars"));
    }

    @Test
    @DisplayName("Detects and rejects incomplete citation missing closing bracket at end of text")
    void shouldDetectAndRejectIncompleteCitationMissingBracket() {
        String response = "The return period is 30 days. [Source: Return Policy v1";
        var result = validator.validateCitations(response, List.of(standardChunk));

        assertFalse(result.valid());
        assertTrue(result.matchedCitations().isEmpty());
        assertEquals(1, result.fabricatedCitations().size());
        assertEquals("[Source: Return Policy v1", result.fabricatedCitations().getFirst());
        assertEquals("The return period is 30 days.", result.normalizedResponse());
    }

    @Test
    @DisplayName("Detects and rejects incomplete citation with only title prefix")
    void shouldDetectAndRejectIncompleteTitleOnly() {
        String response = "[Source: Return Policy";
        var result = validator.validateCitations(response, List.of(standardChunk));

        assertFalse(result.valid());
        assertEquals(1, result.fabricatedCitations().size());
        assertEquals("[Source: Return Policy", result.fabricatedCitations().getFirst());
        assertEquals("", result.normalizedResponse());
    }

    @Test
    @DisplayName("Detects and rejects bare incomplete [Source: prefix")
    void shouldDetectAndRejectBareSourcePrefix() {
        String response = "[Source:";
        var result = validator.validateCitations(response, List.of(standardChunk));

        assertFalse(result.valid());
        assertEquals(1, result.fabricatedCitations().size());
        assertEquals("[Source:", result.fabricatedCitations().getFirst());
        assertEquals("", result.normalizedResponse());
    }

    @Test
    @DisplayName("Detects and rejects incomplete citation with section prefix")
    void shouldDetectAndRejectIncompleteWithSection() {
        String response = "[Source: Return Policy v1, §Returns";
        var result = validator.validateCitations(response, List.of(standardChunk));

        assertFalse(result.valid());
        assertEquals(1, result.fabricatedCitations().size());
        assertEquals("[Source: Return Policy v1, §Returns", result.fabricatedCitations().getFirst());
        assertEquals("", result.normalizedResponse());
    }

    @Test
    @DisplayName("Detects incomplete citation inside prose and preserves adjacent sentences")
    void shouldPreserveAdjacentProseWhenStrippingIncompleteCitation() {
        String response = "The return period is 30 days. [Source: Return Policy v1. Also, warranty is 12 months.";
        var result = validator.validateCitations(response, List.of(standardChunk));

        assertFalse(result.valid());
        assertEquals(1, result.fabricatedCitations().size());
        assertEquals("[Source: Return Policy v1", result.fabricatedCitations().getFirst());
        assertEquals("The return period is 30 days. Also, warranty is 12 months.", result.normalizedResponse());
    }

    @Test
    @DisplayName("Handles mixed valid and incomplete citations: preserves valid, rejects and strips incomplete")
    void shouldHandleMixedValidAndIncompleteCitations() {
        String mixed = "Special orders incur a fee. [Source: Return and Refund Policy v1, §Restocking Fee] "
                + "Also fee is waived. [Source: Return Policy";

        var result = validator.validateCitations(mixed, List.of(standardChunk));

        assertFalse(result.valid());
        assertEquals(1, result.matchedCitations().size());
        assertEquals("[Source: Return and Refund Policy v1, §Restocking Fee]", result.matchedCitations().getFirst());
        assertEquals(1, result.fabricatedCitations().size());
        assertEquals("[Source: Return Policy", result.fabricatedCitations().getFirst());
        assertEquals("Special orders incur a fee. [Source: Return and Refund Policy v1, §Restocking Fee] Also fee is waived.",
                result.normalizedResponse());
    }
}
