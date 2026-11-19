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

    private static long countOccurrences(String text, String value) {
        return (text.length() - text.replace(value, "").length()) / value.length();
    }
}
