package com.autoparts.erp.ai.rag.retrieval;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Tests for Stage 3C Phase 4 Task 1 query normalization and semantic safety. */
class KnowledgeQueryPreprocessorTest {

    private final KnowledgeQueryPreprocessor preprocessor = new KnowledgeQueryPreprocessor();

    @Test
    @DisplayName("Normalize whitespace and punctuation without merging adjacent terms")
    void normalizesWhitespaceAndPunctuation() {
        var result = preprocessor.preprocess("  battery, warranty; tester/wiring  ");
        assertEquals("battery warranty tester wiring", result.normalizedText());
        assertEquals(List.of("battery", "warranty", "tester", "wiring"), result.keywords());
        assertTrue(result.isSafeForStandardRetrieval());
    }

    @Test
    @DisplayName("Preserve hyphenated product and document codes")
    void preservesHyphenatedCodes() {
        var result = preprocessor.preprocess("POL-WAR-001 mid-day like-for-like");
        assertEquals("pol-war-001 mid-day like-for-like", result.normalizedText());
        assertTrue(result.keywords().contains("pol-war-001"));
        assertTrue(result.keywords().contains("mid-day"));
        assertTrue(result.keywords().contains("like-for-like"));
    }

    @Test
    @DisplayName("Preserve decimal measurements")
    void preservesDecimals() {
        var result = preprocessor.preprocess("rotor runout exceeding 0.05 mm and voltage 13.5V");
        assertTrue(result.keywords().contains("0.05"));
        assertTrue(result.keywords().contains("13.5v"));
    }

    @Test
    @DisplayName("Remove conversational prefix and polite suffix")
    void removesConversationWrappers() {
        var result = preprocessor.preprocess("Could you please explain warranty period, thank you!");
        assertEquals("warranty period", result.normalizedText());
    }

    @Test
    @DisplayName("Generate ordinary retrieval tiers for unconstrained positive queries")
    void generatesStandardTiersForPositiveQuery() {
        var result = preprocessor.preprocess("battery warranty period");
        assertEquals("battery warranty period", result.exactPhraseText());
        assertEquals("\"battery warranty period\"", result.exactPhraseWebsearch());
        assertEquals("battery warranty period", result.booleanAndQuery());
        assertEquals("battery OR warranty OR period", result.orQuery());
        assertEquals(List.of("battery", "warranty", "period"), result.positiveTerms());
        assertTrue(result.isSafeForStandardRetrieval());
    }

    @Test
    @DisplayName("Natural-language NOT is metadata and never becomes PostgreSQL minus-term syntax")
    void modelsNotWithoutFtsExclusion() {
        var result = preprocessor.preprocess("alternator not charging");
        assertTrue(result.hasNegation());
        assertEquals(List.of("not"), result.negationTerms());
        assertEquals(List.of("alternator"), result.positiveTerms());
        assertEquals(List.of("charging"), result.excludedScopeTerms());
        assertEquals("", result.booleanAndQuery());
        assertEquals("", result.orQuery());
        assertEquals("", result.exactPhraseText());
        assertFalse(result.isSafeForStandardRetrieval());
        assertFalse(result.booleanAndQuery().contains("-charging"));
    }

    @Test
    @DisplayName("WITHOUT keeps the scoped term as metadata, not an FTS exclusion")
    void modelsWithoutScope() {
        var result = preprocessor.preprocess("products without warranty");
        assertTrue(result.hasNegation());
        assertEquals(List.of("products"), result.positiveTerms());
        assertEquals(List.of("warranty"), result.excludedScopeTerms());
        assertEquals("", result.booleanAndQuery());
        assertEquals("", result.exactPhraseText());
        assertEquals("", result.orQuery());
    }

    @Test
    @DisplayName("Positive re-entry after WITHOUT is parsed correctly")
    void handlesPositiveReentryAfterWithout() {
        var result = preprocessor.preprocess("products without warranty and with receipt");
        assertEquals(List.of("products", "receipt"), result.positiveTerms());
        assertEquals(List.of("warranty"), result.excludedScopeTerms());
        assertTrue(result.constraintScopeKnown());
        assertEquals("", result.booleanAndQuery());
        assertEquals("", result.orQuery());
    }

    @Test
    @DisplayName("EXCLUDING scope is metadata and does not become FTS minus terms")
    void handlesExcludingCommercialFleetUse() {
        var result = preprocessor.preprocess("products excluding commercial fleet use");
        assertTrue(result.hasExclusion());
        assertEquals(List.of("excluding"), result.exclusionTerms());
        assertEquals(List.of("products"), result.positiveTerms());
        assertEquals(List.of("commercial", "fleet", "use"), result.excludedScopeTerms());
        assertEquals("", result.booleanAndQuery());
        assertEquals("", result.orQuery());
        assertEquals("", result.exactPhraseText());
    }

    @Test
    @DisplayName("NEVER scopes all following content terms")
    void handlesNeverIssueCashRefunds() {
        var result = preprocessor.preprocess("never issue cash refunds");
        assertTrue(result.hasNegation());
        assertEquals(List.of("never"), result.negationTerms());
        assertTrue(result.positiveTerms().isEmpty());
        assertEquals(List.of("issue", "cash", "refunds"), result.excludedScopeTerms());
        assertTrue(result.isPureExclusion());
        assertEquals("", result.booleanAndQuery());
        assertEquals("", result.orQuery());
    }

    @Test
    @DisplayName("NO warranty is treated as a constrained negative-only query")
    void handlesNoWarranty() {
        var result = preprocessor.preprocess("no warranty");
        assertTrue(result.hasNegation());
        assertEquals(List.of("warranty"), result.excludedScopeTerms());
        assertTrue(result.isPureExclusion());
        assertEquals("", result.booleanAndQuery());
    }

    @Test
    @DisplayName("NEITHER NOR records both scoped terms without inventing a positive anchor")
    void handlesNeitherNor() {
        var result = preprocessor.preprocess("neither alternator nor starter");
        assertTrue(result.hasNegation());
        assertEquals(List.of("neither", "nor"), result.negationTerms());
        assertTrue(result.positiveTerms().isEmpty());
        assertEquals(List.of("alternator", "starter"), result.excludedScopeTerms());
        assertTrue(result.isPureExclusion());
        assertTrue(result.constraintScopeKnown());
        assertEquals("", result.booleanAndQuery());
        assertEquals("", result.orQuery());
    }

    @Test
    @DisplayName("Unpaired NEITHER is marked ambiguous")
    void marksUnpairedNeitherAmbiguous() {
        var result = preprocessor.preprocess("neither alternator starter");
        assertTrue(result.hasNegation());
        assertFalse(result.constraintScopeKnown());
        assertEquals("", result.booleanAndQuery());
    }

    @Test
    @DisplayName("A standalone NOR is marked ambiguous")
    void marksStandaloneNorAmbiguous() {
        var result = preprocessor.preprocess("alternator nor starter");
        assertTrue(result.hasNegation());
        assertFalse(result.constraintScopeKnown());
        assertEquals("", result.booleanAndQuery());
    }

    @Test
    @DisplayName("All constrained examples disable phrase, Boolean and OR execution tiers")
    void disablesExecutableTiersForConstrainedQueries() {
        List<String> queries = List.of(
                "alternator not charging",
                "products without warranty",
                "never issue cash refunds",
                "no warranty",
                "neither alternator nor starter",
                "products excluding commercial fleet use",
                "products without warranty and with receipt"
        );
        for (String query : queries) {
            var result = preprocessor.preprocess(query);
            assertTrue(result.isConstrained(), query);
            assertEquals("", result.exactPhraseText(), query);
            assertEquals("", result.exactPhraseWebsearch(), query);
            assertEquals("", result.booleanAndQuery(), query);
            assertEquals("", result.orQuery(), query);
        }
    }


    @Test
    @DisplayName("NOT scopes the next content term without turning it into an FTS exclusion")
    void handlesNotEligibleForWarranty() {
        var result = preprocessor.preprocess("not eligible for warranty");
        assertTrue(result.hasNegation());
        assertEquals(List.of("eligible"), result.excludedScopeTerms());
        assertTrue(result.positiveTerms().containsAll(List.of("for", "warranty")));
        assertTrue(result.constraintScopeKnown());
        assertEquals("", result.exactPhraseText());
        assertEquals("", result.booleanAndQuery());
        assertEquals("", result.orQuery());
    }

    @Test
    @DisplayName("Hyphenated NON compound remains a searchable business term")
    void preservesHyphenatedNonRefundableTerm() {
        var result = preprocessor.preprocess("non-refundable parts");
        assertFalse(result.hasNegation());
        assertEquals(List.of("non-refundable", "parts"), result.positiveTerms());
        assertTrue(result.isSafeForStandardRetrieval());
    }

    @Test
    @DisplayName("Standalone NON prefix is marked ambiguous instead of guessing its scope")
    void marksStandaloneNonPrefixAmbiguous() {
        var result = preprocessor.preprocess("non refundable parts");
        assertTrue(result.hasNegation());
        assertFalse(result.constraintScopeKnown());
        assertEquals("", result.exactPhraseText());
        assertEquals("", result.booleanAndQuery());
        assertEquals("", result.orQuery());
    }

    @Test
    @DisplayName("EXCEPT is treated as a bounded exclusion operator")
    void handlesExceptOperator() {
        var result = preprocessor.preprocess("products except commercial fleet use");
        assertTrue(result.hasExclusion());
        assertEquals(List.of("products"), result.positiveTerms());
        assertEquals(List.of("commercial", "fleet", "use"), result.excludedScopeTerms());
        assertEquals("", result.exactPhraseText());
        assertEquals("", result.booleanAndQuery());
        assertEquals("", result.orQuery());
    }

    @Test
    @DisplayName("Business noun EXCLUSIONS is not treated as an exclusion operator")
    void preservesExclusionsBusinessNoun() {
        var result = preprocessor.preprocess("battery warranty tester receipt exclusions");
        assertFalse(result.hasExclusion());
        assertTrue(result.positiveTerms().contains("exclusions"));
        assertEquals("battery warranty tester receipt exclusions", result.booleanAndQuery());
    }

    @Test
    @DisplayName("Preserve temporal and numeric business terms")
    void preservesTemporalTerms() {
        var result = preprocessor.preprocess("must return before 30 days within warranty window period");
        assertTrue(result.keywords().containsAll(List.of("before", "30", "days", "within", "window", "period")));
    }

    @Test
    @DisplayName("Deduplicate repeated terms without changing first-occurrence order")
    void deduplicatesKeywords() {
        var result = preprocessor.preprocess("battery warranty battery period warranty");
        assertEquals(List.of("battery", "warranty", "period"), result.keywords());
        assertEquals(List.of("battery", "warranty", "period"), result.positiveTerms());
    }

    @Test
    @DisplayName("Handle null, empty and whitespace-only queries")
    void handlesEmptyQueries() {
        assertTrue(preprocessor.preprocess(null).isEmpty());
        assertTrue(preprocessor.preprocess("").isEmpty());
        assertTrue(preprocessor.preprocess("  \t\n ").isEmpty());
    }
}
