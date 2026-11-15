
package com.autoparts.erp.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ErpAiIntentClassifierTest {

    private ErpAiIntentClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new ErpAiIntentClassifier();
    }

    @Test
    void shouldReturnClarificationForNullMessage() {
        ErpAiIntentResult result = classifier.classify(null);

        assertEquals("CLARIFICATION", result.intent());
        assertEquals("", result.searchText());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t", "\n"})
    void shouldReturnClarificationForBlankMessage(String message) {
        ErpAiIntentResult result = classifier.classify(message);

        assertEquals("CLARIFICATION", result.intent());
        assertEquals("", result.searchText());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "How many products do we have?",
            "How many active products are available?",
            "Count products",
            "What is the total number of products?",
            "Show me the product count",
            "Tell me the number of products"
    })
    void shouldClassifyProductCountRequests(String message) {
        ErpAiIntentResult result = classifier.classify(message);

        assertEquals("COUNT_PRODUCTS", result.intent());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Show low stock products",
            "Which products need restocking?",
            "Show items that need restocking",
            "Find products below minimum stock",
            "Which products are running low?",
            "Show products that are out of stock"
    })
    void shouldClassifyLowStockRequests(String message) {
        ErpAiIntentResult result = classifier.classify(message);

        assertEquals("LOW_STOCK_PRODUCTS", result.intent());
    }

    @Test
    void shouldClassifyProductSearchByName() {
        ErpAiIntentResult result =
                classifier.classify("Show me brake pads");

        assertEquals("PRODUCT_SEARCH", result.intent());
        assertEquals("brake pads", result.searchText());
    }

    @ParameterizedTest
    @CsvSource({
            "Show GAP-000001, GAP-000001",
            "Find PRD001, PRD001",
            "Search for FILTER-001, FILTER-001",
            "Tell me about GAP000001, GAP000001"
    })
    void shouldExtractProductIdentifiers(
            String message,
            String expectedSearchText
    ) {
        ErpAiIntentResult result = classifier.classify(message);

        assertEquals("PRODUCT_SEARCH", result.intent());
        assertEquals(expectedSearchText, result.searchText());
    }

    @Test
    void shouldClassifyProductStockRequest() {
        ErpAiIntentResult result =
                classifier.classify("What is the stock for PRD001?");

        assertEquals("PRODUCT_STOCK", result.intent());
        assertEquals("PRD001", result.searchText());
    }

    @Test
    void shouldClassifyProductDetailsRequest() {
        ErpAiIntentResult result =
                classifier.classify("Show details for GAP-000001");

        assertEquals("PRODUCT_DETAILS", result.intent());
        assertEquals("GAP-000001", result.searchText());
    }

    @Test
    void shouldReturnClarificationWhenStockRequestHasNoProduct() {
        ErpAiIntentResult result =
                classifier.classify("Show current stock");

        assertEquals("CLARIFICATION", result.intent());
    }

    @Test
    void shouldReturnClarificationWhenDetailsRequestHasNoProduct() {
        ErpAiIntentResult result =
                classifier.classify("Show product details");

        assertEquals("CLARIFICATION", result.intent());
    }

    @Test
    void shouldReturnClarificationWhenSearchRequestHasNoSearchText() {
        ErpAiIntentResult result =
                classifier.classify("Find product");

        assertEquals("CLARIFICATION", result.intent());
    }

    @Test
    void shouldNotTreatNumericDatabaseIdAsProductCode() {
        ErpAiIntentResult result =
                classifier.classify("Show product 123");

        assertEquals("PRODUCT_SEARCH", result.intent());
        assertEquals("123", result.searchText());
    }

    @Test
    void shouldClassifySupplierRequestAsUnsupported() {
        ErpAiIntentResult result =
                classifier.classify("Show supplier details");

        assertEquals("UNSUPPORTED", result.intent());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Create purchase",
            "Create sales",
            "Create invoice",
            "Generate invoice",
            "Record purchase",
            "Record sales"
    })
    void shouldClassifyUnsupportedOperations(String message) {
        ErpAiIntentResult result = classifier.classify(message);

        assertEquals("UNSUPPORTED", result.intent());
    }

    @Test
    void shouldClassifyGeneralQuestion() {
        ErpAiIntentResult result =
                classifier.classify("What is an ERP system?");

        assertEquals("GENERAL", result.intent());
    }
}
