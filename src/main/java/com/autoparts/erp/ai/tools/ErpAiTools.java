
package com.autoparts.erp.ai.tools;

import java.util.List;
import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Explicit allow-list of read-only ERP capabilities exposed to the LLM.
 *
 * SQL and business rules remain in the lookup classes.
 * This class is only the Spring AI tool boundary.
 */
@Component
public class ErpAiTools {

    private final ProductLookupTool productLookupTool;
    private final PurchaseLookupTool purchaseLookupTool;
    private final InventoryHistoryLookupTool inventoryHistoryLookupTool;

    public ErpAiTools(
            ProductLookupTool productLookupTool,
            PurchaseLookupTool purchaseLookupTool,
            InventoryHistoryLookupTool inventoryHistoryLookupTool) {
        this.productLookupTool = productLookupTool;
        this.purchaseLookupTool = purchaseLookupTool;
        this.inventoryHistoryLookupTool = inventoryHistoryLookupTool;
    }

    // ------------------------- PRODUCTS -------------------------

    @Tool(
        name = "searchProducts",
        description = """
            Search active ERP products by product code, part number
            or product name.

            Use only for product discovery.

            Product identifiers belong to the product domain.
            NEVER use this tool for purchase numbers such as PUR-00000001
            or supplier invoice numbers.

            For PUR- identifiers, use searchPurchases or getPurchaseDetails.
            """
    )
    public List<Map<String, Object>> searchProducts(
            @ToolParam(description = "Product code, part number or words from product name")
            String searchText) {
        return productLookupTool.searchProducts(searchText);
    }

    @Tool(
        name = "getProductDetails",
        description = """
            Retrieve detailed information about one exact product
            using its product code or part number.

            NEVER use this tool for purchase numbers beginning with PUR-.
            Purchase numbers must be handled by getPurchaseDetails.
            """
    )
    public List<Map<String, Object>> getProductDetails(
            @ToolParam(description = "Exact product code or part number; never a purchase number")
            String productIdentifier) {
        return productLookupTool.getProductDetails(productIdentifier);
    }

    @Tool(
        name = "getProductStock",
        description = """
            Retrieve current stock quantity for one product using
            its exact product code or part number.

            NEVER use for purchase numbers or supplier invoice numbers.
            """
    )
    public List<Map<String, Object>> getProductStock(
            @ToolParam(description = "Exact product code or part number")
            String productIdentifier) {
        return productLookupTool.getProductStock(productIdentifier);
    }

    @Tool(
        name = "countProducts",
        description = """
            Count active product records.
            This counts product records, not total inventory quantity.
            """
    )
    public Long countProducts() {
        return productLookupTool.countProducts();
    }

    @Tool(
        name = "getLowStockProducts",
        description = """
            List active products whose current quantity is at or below
            their minimum stock level.
            """
    )
    public List<Map<String, Object>> getLowStockProducts() {
        return productLookupTool.getLowStockProducts();
    }

    @Tool(
        name = "compareProducts",
        description = """
            Retrieve two to five products for comparison.
            Requires exact product codes or part numbers.
            Never use purchase numbers as product identifiers.
            """
    )
    public List<Map<String, Object>> compareProducts(
            @ToolParam(description = "Two to five exact product codes or part numbers")
            List<String> identifiers) {
        return productLookupTool.getProductsByIdentifiers(identifiers);
    }

    // ------------------------- PURCHASES -------------------------

    @Tool(
        name = "searchPurchases",
        description = """
            Search purchase records.

            Use this tool for:
            - Purchase number, especially identifiers beginning with PUR-
            - Supplier invoice number
            - Supplier name
            - Purchase status such as DRAFT or RECEIVED
            - Purchase date ranges

            Date filters are inclusive.
            Dates must use YYYY-MM-DD format.

            For a purchase identifier such as PUR-00000001,
            use searchText with that exact identifier.

            For supplier filtering, use supplierName.

            For status filtering, use status.

            For date filtering, provide startDate and endDate.

            For the latest purchases, leave filters empty and set limit.

            NEVER use product lookup tools for purchase identifiers.
            """
    )
    public List<Map<String, Object>> searchPurchases(
            @ToolParam(
                description = "Exact purchase number, supplier invoice number or supplier name",
                required = false
            )
            String searchText,

            @ToolParam(
                description = "Supplier name filter",
                required = false
            )
            String supplierName,

            @ToolParam(
                description = "Purchase status, for example DRAFT or RECEIVED",
                required = false
            )
            String status,

            @ToolParam(
                description = "Inclusive start date in YYYY-MM-DD format",
                required = false
            )
            String startDate,

            @ToolParam(
                description = "Inclusive end date in YYYY-MM-DD format",
                required = false
            )
            String endDate,

            @ToolParam(
                description = "Maximum results, from 1 to 50; defaults to 10",
                required = false
            )
            Integer limit) {

        return purchaseLookupTool.searchPurchases(
                searchText,
                supplierName,
                status,
                startDate,
                endDate,
                limit == null ? 10 : limit
        );
    }

    @Tool(
        name = "getPurchaseDetails",
        description = """
            Retrieve complete purchase information, including purchase
            header, supplier, invoice, status, financial summary and
            purchased product lines.

            Use this for an exact purchase number such as PUR-00000001
            or an exact supplier invoice number such as ABC-INV-1001.

            This is the correct tool for purchase identifiers.
            NEVER send a purchase number to getProductDetails.
            """
    )
    public List<Map<String, Object>> getPurchaseDetails(
            @ToolParam(
                description = "Exact purchase number such as PUR-00000001 or supplier invoice number"
            )
            String identifier) {
        return purchaseLookupTool.getPurchaseDetails(identifier);
    }

    // ------------------------- INVENTORY HISTORY -------------------------

    @Tool(
        name = "getProductMovementHistory",
        description = """
            Retrieve the latest inventory movement records for an exact
            product code or part number.
            """
    )
    public List<Map<String, Object>> getProductMovementHistory(
            @ToolParam(description = "Exact product code or part number")
            String productIdentifier) {
        return inventoryHistoryLookupTool.getProductMovementHistory(productIdentifier);
    }

    @Tool(
        name = "getProductMovementSummary",
        description = """
            Summarize inventory movement counts and quantity changes
            by movement type for one product.
            """
    )
    public List<Map<String, Object>> getProductMovementSummary(
            @ToolParam(description = "Exact product code or part number")
            String productIdentifier) {
        return inventoryHistoryLookupTool.getProductMovementSummary(productIdentifier);
    }

    @Tool(
        name = "getProductMovementsByDateRange",
        description = """
            Retrieve inventory movement history for one product between
            inclusive dates. Dates must use YYYY-MM-DD.
            """
    )
    public List<Map<String, Object>> getProductMovementsByDateRange(
            @ToolParam(description = "Exact product code or part number")
            String productIdentifier,

            @ToolParam(description = "Inclusive start date in YYYY-MM-DD format")
            String startDate,

            @ToolParam(description = "Inclusive end date in YYYY-MM-DD format")
            String endDate) {
        return inventoryHistoryLookupTool.getProductMovementsByDateRange(
                productIdentifier,
                startDate,
                endDate
        );
    }

    @Tool(
        name = "getRecentInventoryMovements",
        description = """
            Retrieve recent inventory movements across all products.
            Limit defaults to 10 and is capped at 50.
            """
    )
    public List<Map<String, Object>> getRecentInventoryMovements(
            @ToolParam(
                description = "Maximum number of records, from 1 to 50",
                required = false
            )
            Integer limit) {
        return inventoryHistoryLookupTool.getRecentInventoryMovements(
                limit == null ? 10 : limit
        );
    }

    @Tool(
        name = "getInventoryAdjustments",
        description = """
            Retrieve inventory adjustment history.
            Product identifier is optional.
            Limit defaults to 10 and is capped at 50.
            """
    )
    public List<Map<String, Object>> getInventoryAdjustments(
            @ToolParam(
                description = "Optional exact product code or part number",
                required = false
            )
            String productIdentifier,

            @ToolParam(
                description = "Maximum number of records, from 1 to 50",
                required = false
            )
            Integer limit) {
        return inventoryHistoryLookupTool.getInventoryAdjustments(
                productIdentifier,
                limit == null ? 10 : limit
        );
    }
}
