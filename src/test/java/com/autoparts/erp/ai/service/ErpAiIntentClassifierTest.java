package com.autoparts.erp.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void shouldClassifySupplierMutationAsUnsupported() {
        ErpAiIntentResult result =
                classifier.classify("Add supplier Acme Parts");

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

    @Test
    void shouldClassifyMovementSummaryAndExtractProductCode() {
        ErpAiIntentResult result = classifier.classify(
                "Show movement summary for GAP-000001");
        assertEquals("PRODUCT_MOVEMENT_SUMMARY", result.intent());
        assertEquals("GAP-000001", result.searchText());
    }

    @Test
    void shouldClassifyDateRangeMovementRequestAndExtractDates() {
        ErpAiIntentResult result = classifier.classify(
                "Show movements for GAP-000001 from 2026-11-01 to 2026-11-15");
        assertEquals("PRODUCT_MOVEMENTS_BY_DATE_RANGE", result.intent());
        assertEquals("GAP-000001", result.searchText());
        assertEquals("2026-11-01", result.startDate());
        assertEquals("2026-11-15", result.endDate());
    }

    @Test
    void shouldClarifyWhenDateRangeIsIncompleteOrReversed() {
        assertEquals("CLARIFICATION", classifier.classify(
                "Show movements for GAP-000001 from 2026-11-01").intent());
        assertEquals("CLARIFICATION", classifier.classify(
                "Show movements for GAP-000001 from 2026-11-15 to 2026-11-01").intent());
    }

    @Test
    void shouldClassifyRecentMovementsAndExtractLimit() {
        ErpAiIntentResult result = classifier.classify(
                "Show the last 12 inventory movements");
        assertEquals("RECENT_INVENTORY_MOVEMENTS", result.intent());
        assertEquals(12, result.limit());
    }

    @Test
    void shouldUseSafeDefaultForRecentMovements() {
        ErpAiIntentResult result = classifier.classify("Show recent inventory movements");
        assertEquals("RECENT_INVENTORY_MOVEMENTS", result.intent());
        assertEquals(10, result.limit());
    }

    @Test
    void shouldClassifyInventoryAdjustmentsWithOptionalProductFilter() {
        ErpAiIntentResult all = classifier.classify("Show all inventory adjustments");
        ErpAiIntentResult product = classifier.classify(
                "Show adjustment history for GAP-000001");
        assertEquals("INVENTORY_ADJUSTMENTS", all.intent());
        assertEquals("", all.searchText());
        assertEquals("INVENTORY_ADJUSTMENTS", product.intent());
        assertEquals("GAP-000001", product.searchText());
    }
    
    @Test
    void shouldExtractProductSearchTextWithoutMatchingPrefix() {
        ErpAiIntentResult result =
                classifier.classify("Find products matching brake pad");

        assertEquals("PRODUCT_SEARCH", result.intent());
        assertEquals("brake pad", result.searchText());
    }

    @Test
    void shouldClarifyStockRequestWithoutProductIdentifier() {
        ErpAiIntentResult result =
                classifier.classify("How many available quantity?");

        assertEquals("CLARIFICATION", result.intent());
        assertTrue(result.clarificationMessage()
                .contains("product code or part number"));
    }

    @Test
    void shouldClassifyStockRequestWithProductCode() {
        ErpAiIntentResult result =
                classifier.classify("Stock for GAP-000001?");

        assertEquals("PRODUCT_STOCK", result.intent());
        assertEquals("GAP-000001", result.searchText());
    }

    @Test
    void shouldClassifyProductCountRequest() {
        ErpAiIntentResult result =
                classifier.classify("How many products are there?");

        assertEquals("COUNT_PRODUCTS", result.intent());
    }


    @Test
    void shouldClassifyMinimumStockAsAFieldSpecificProductIntent() {
        ErpAiIntentResult result = classifier.classify(
                "What is minimum stock for GAP-000001?");
        assertEquals("PRODUCT_MINIMUM_STOCK", result.intent());
        assertEquals("GAP-000001", result.searchText());
    }

    @Test
    void shouldClassifyAvailableQuantityAsAFieldSpecificProductIntent() {
        ErpAiIntentResult result = classifier.classify(
                "What is available quantity for GAP-000001?");
        assertEquals("PRODUCT_AVAILABLE_QUANTITY", result.intent());
        assertEquals("GAP-000001", result.searchText());
    }

    @Test
    void shouldClassifyPartNumberAsAFieldSpecificProductIntent() {
        ErpAiIntentResult result = classifier.classify(
                "What is the part number for GAP-000001?");
        assertEquals("PRODUCT_PART_NUMBER", result.intent());
        assertEquals("GAP-000001", result.searchText());
    }

    @Test
    void shouldClarifyMinimumStockRequestWithoutProductIdentifier() {
        ErpAiIntentResult result = classifier.classify("What is minimum stock?");
        assertEquals("CLARIFICATION", result.intent());
    }

    @Test
    void shouldClassifyProductSpecificLowStockStatus() {
        ErpAiIntentResult result = classifier.classify(
                "Is GAP-000001 low in stock?");
        assertEquals("PRODUCT_LOW_STOCK_STATUS", result.intent());
        assertEquals("GAP-000001", result.searchText());
    }

}