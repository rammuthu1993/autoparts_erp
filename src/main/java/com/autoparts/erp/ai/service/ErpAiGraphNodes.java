package com.autoparts.erp.ai.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CancellationException;

import org.bsc.langgraph4j.RunnableConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.autoparts.erp.ai.exception.AiProviderResponseException;
import com.autoparts.erp.ai.security.ErpAiExecutionContext;
import com.autoparts.erp.ai.security.ToolAuthorizationException;
import com.autoparts.erp.ai.tools.CustomerLookupTool;
import com.autoparts.erp.ai.tools.InventoryHistoryLookupTool;
import com.autoparts.erp.ai.tools.ProductLookupTool;
import com.autoparts.erp.ai.tools.PurchaseLookupTool;
import com.autoparts.erp.ai.tools.SalesLookupTool;
import com.autoparts.erp.ai.tools.SupplierLookupTool;

@Component
public class ErpAiGraphNodes {

    private static final Logger log =
            LoggerFactory.getLogger(ErpAiGraphNodes.class);

    private final ChatModel chatModel;
    private final ProductLookupTool productLookupTool;
    private final ErpAiIntentClassifier intentClassifier;
    private final InventoryHistoryLookupTool inventoryHistoryLookupTool;
    private final PurchaseLookupTool purchaseLookupTool;
    private final SalesLookupTool salesLookupTool;
    private final CustomerLookupTool customerLookupTool;
    private final SupplierLookupTool supplierLookupTool;
    private final String systemPrompt;

    @Autowired
    public ErpAiGraphNodes(
            ChatModel chatModel,
            ProductLookupTool productLookupTool,
            ErpAiIntentClassifier intentClassifier,
            InventoryHistoryLookupTool inventoryHistoryLookupTool,
            PurchaseLookupTool purchaseLookupTool,
            SalesLookupTool salesLookupTool,
            CustomerLookupTool customerLookupTool,
            SupplierLookupTool supplierLookupTool
    ) {
        this.chatModel = chatModel;
        this.productLookupTool = productLookupTool;
        this.intentClassifier = intentClassifier;
        this.inventoryHistoryLookupTool = inventoryHistoryLookupTool;
        this.purchaseLookupTool = purchaseLookupTool;
        this.salesLookupTool = salesLookupTool;
        this.customerLookupTool = customerLookupTool;
        this.supplierLookupTool = supplierLookupTool;
        this.systemPrompt = loadSystemPrompt();
    }

    public ErpAiGraphNodes(
            ChatModel chatModel,
            ProductLookupTool productLookupTool,
            ErpAiIntentClassifier intentClassifier,
            InventoryHistoryLookupTool inventoryHistoryLookupTool
    ) {
        this(chatModel, productLookupTool, intentClassifier, inventoryHistoryLookupTool, null, null, null, null);
    }

    // --------------------------------------------------
    // 1. INTENT CLASSIFICATION NODE
    // --------------------------------------------------

    public Map<String, Object> classifyIntent(
            ErpAiGraphState state,
            RunnableConfig config
    ) {
        String currentMessage = state.text("currentMessage");
        ErpAiIntentResult classification = intentClassifier.classify(currentMessage);

        // Resolve deterministic product references from this conversation's USER history.
        ErpAiConversationContextResolver.Resolution resolution =
                ErpAiConversationContextResolver.resolve(currentMessage, state.history(), classification);
        if (resolution.needsClarification()) {
            classification = ErpAiIntentResult.clarification(resolution.clarificationMessage());
        } else if (resolution.contextApplied()) {
            classification = intentClassifier.classify(resolution.resolvedMessage());
        }

        log.info(
                "ERP AI intent classified. Intent: {}, Conversation: {}",
                classification.intent(),
                state.text("conversationId")
        );

        return Map.of(
                "intent", classification.intent(),
                "searchText", classification.searchText(),
                "startDate", classification.startDate(),
                "endDate", classification.endDate(),
                "limit", classification.limit() == null ? 10 : classification.limit(),
                "clarificationMessage", classification.clarificationMessage(),
                "productIdentifiers", classification.productIdentifiers()
        );
    }

    // --------------------------------------------------
    // 2. TOOL EXECUTION NODE
    // --------------------------------------------------

    public Map<String, Object> executeProductTool(
            ErpAiGraphState state,
            RunnableConfig config
    ) {
        String intent = state.text("intent");
        String searchText = state.text("searchText");

        log.info("ERP AI tool execution started. Tool intent: {}", intent);

        ErpAiExecutionContext context = (config != null)
                ? config.metadata("executionContext")
                        .filter(ErpAiExecutionContext.class::isInstance)
                        .map(ErpAiExecutionContext.class::cast)
                        .orElse(null)
                : null;

        if (context == null) {
            log.warn("ERP AI tool execution rejected: No execution context present in RunnableConfig metadata. Intent: {}", intent);
            return Map.of(
                    "toolResult", "",
                    "toolError", "ACCESS_DENIED: No execution context provided. Access denied."
            );
        }

        try {
            Object result = executeWithContext(intent, searchText, state, context);

            log.info("ERP AI tool execution completed. Intent: {}", intent);

            return Map.of(
                    "toolResult", result != null ? result : Map.of(),
                    "toolError", ""
            );

        } catch (CancellationException exception) {
            throw exception;

        } catch (ToolAuthorizationException exception) {
            log.warn("ERP AI tool authorization denied. Intent: {}, Reason: {}", intent, exception.getMessage());

            boolean requiresAdminOrManager = intent.startsWith("PURCHASE_")
                    || intent.equals("SUPPLIER_PURCHASE_HISTORY")
                    || intent.equals("SUPPLIER_SUMMARY")
                    || intent.startsWith("PRODUCT_MOVEMENT")
                    || intent.equals("RECENT_INVENTORY_MOVEMENTS")
                    || intent.equals("INVENTORY_ADJUSTMENTS");

            return Map.of(
                    "toolResult", "",
                    "toolError",
                    "ACCESS_DENIED: You do not have permission to view this ERP data. Required role: " +
                            (requiresAdminOrManager ? "Admin or Manager" : "Cashier, Manager, or Admin") + "."
            );

        } catch (RuntimeException exception) {
            log.error("ERP AI tool execution failed. Intent: {}", intent, exception);

            return Map.of(
                    "toolResult", "",
                    "toolError",
                    "Unable to retrieve the requested ERP information."
            );
        }
    }

    private Object executeWithContext(
            String intent,
            String searchText,
            ErpAiGraphState state,
            ErpAiExecutionContext context
    ) {
        return switch (intent) {
            // Product Tools
            case "PRODUCT_SEARCH" ->
                    productLookupTool.searchProducts(searchText, context);
            case "PRODUCT_COMPARISON" ->
                    productLookupTool.getProductsByIdentifiers(
                            productIdentifiers(state.object("productIdentifiers")), context);
            case "PRODUCT_DETAILS" ->
                    productLookupTool.getProductDetails(searchText, context);
            case "PRODUCT_STOCK" ->
                    productLookupTool.getProductStock(searchText, context);
            case "COUNT_PRODUCTS" ->
                    productLookupTool.countProducts(context);
            case "LOW_STOCK_PRODUCTS" ->
                    productLookupTool.getLowStockProducts(context);

            // Inventory Movement Tools
            case "PRODUCT_MOVEMENT_HISTORY" ->
                    inventoryHistoryLookupTool.getProductMovementHistory(searchText, context);
            case "PRODUCT_MOVEMENT_SUMMARY" ->
                    inventoryHistoryLookupTool.getProductMovementSummary(searchText, context);
            case "PRODUCT_MOVEMENTS_BY_DATE_RANGE" ->
                    inventoryHistoryLookupTool.getProductMovementsByDateRange(
                            searchText, state.text("startDate"), state.text("endDate"), context);
            case "RECENT_INVENTORY_MOVEMENTS" ->
                    inventoryHistoryLookupTool.getRecentInventoryMovements(
                            parseLimit(state.object("limit")), context);
            case "INVENTORY_ADJUSTMENTS" ->
                    inventoryHistoryLookupTool.getInventoryAdjustments(
                            searchText, parseLimit(state.object("limit")), context);

            // Purchase Tools (Stage 2C)
            case "PURCHASE_SEARCH" ->
                    purchaseLookupTool != null ? purchaseLookupTool.searchPurchases(searchText, context) : List.of();
            case "PURCHASE_DETAILS" ->
                    purchaseLookupTool != null ? purchaseLookupTool.getPurchaseDetails(searchText, context) : Map.of();
            case "PURCHASE_DATE_RANGE" ->
                    purchaseLookupTool != null ? purchaseLookupTool.getPurchasesByDateRange(state.text("startDate"), state.text("endDate"), context) : List.of();
            case "PURCHASES_BY_SUPPLIER" ->
                    purchaseLookupTool != null ? purchaseLookupTool.getPurchasesBySupplier(searchText, context) : List.of();
            case "PURCHASE_SUMMARY" ->
                    purchaseLookupTool != null ? purchaseLookupTool.getPurchaseSummary(state.text("startDate"), state.text("endDate"), context) : Map.of();
            case "PURCHASE_STATUS" ->
                    purchaseLookupTool != null ? purchaseLookupTool.getPurchaseStatus(searchText, context) : Map.of();

            // Sales Tools (Stage 2C)
            case "SALES_SEARCH" ->
                    salesLookupTool != null ? salesLookupTool.searchSales(searchText, context) : List.of();
            case "SALE_DETAILS" ->
                    salesLookupTool != null ? salesLookupTool.getSaleDetails(searchText, context) : Map.of();
            case "SALES_DATE_RANGE" ->
                    salesLookupTool != null ? salesLookupTool.getSalesByDateRange(state.text("startDate"), state.text("endDate"), context) : List.of();
            case "SALES_BY_CUSTOMER" ->
                    salesLookupTool != null ? salesLookupTool.getSalesByCustomer(searchText, context) : List.of();
            case "SALES_SUMMARY" ->
                    salesLookupTool != null ? salesLookupTool.getSalesSummary(state.text("startDate"), state.text("endDate"), context) : Map.of();
            case "SALE_STATUS" ->
                    salesLookupTool != null ? salesLookupTool.getSaleStatus(searchText, context) : Map.of();

            // Customer Tools (Stage 2C)
            case "CUSTOMER_SEARCH" ->
                    customerLookupTool != null ? customerLookupTool.searchCustomers(searchText, context) : List.of();
            case "CUSTOMER_DETAILS" ->
                    customerLookupTool != null ? customerLookupTool.getCustomerDetails(searchText, context) : Map.of();
            case "CUSTOMER_SALES_HISTORY" ->
                    customerLookupTool != null ? customerLookupTool.getCustomerSalesHistory(searchText, context) : List.of();
            case "CUSTOMER_OUTSTANDING" ->
                    customerLookupTool != null ? customerLookupTool.getCustomerOutstandingBalance(searchText, context) : Map.of();

            // Supplier Tools (Stage 2C)
            case "SUPPLIER_SEARCH" ->
                    supplierLookupTool != null ? supplierLookupTool.searchSuppliers(searchText, context) : List.of();
            case "SUPPLIER_DETAILS" ->
                    supplierLookupTool != null ? supplierLookupTool.getSupplierDetails(searchText, context) : Map.of();
            case "SUPPLIER_PURCHASE_HISTORY" ->
                    supplierLookupTool != null ? supplierLookupTool.getSupplierPurchaseHistory(searchText, context) : List.of();
            case "SUPPLIER_SUMMARY" ->
                    supplierLookupTool != null ? supplierLookupTool.getSupplierPurchaseSummary(searchText, context) : Map.of();

            default -> throw new IllegalArgumentException("Unsupported tool intent: " + intent);
        };
    }

    // --------------------------------------------------
    // 3. RESPONSE GENERATION NODE
    // --------------------------------------------------

    public Map<String, Object> generateResponse(
            ErpAiGraphState state,
            RunnableConfig config
    ) {
        String toolError = state.text("toolError");

        if (StringUtils.hasText(toolError)) {
            if (toolError.startsWith("ACCESS_DENIED:")) {
                return Map.of("finalResponse", toolError.substring("ACCESS_DENIED:".length()).trim());
            }
            return Map.of("finalResponse", "I couldn't retrieve the requested information from the ERP system. Please try again.");
        }

        Object toolResult = state.object("toolResult");
        String intent = state.text("intent");

        // Empty list handling
        if (toolResult instanceof List<?> results && results.isEmpty()) {
            String emptyMessage = switch (intent) {
                case "PRODUCT_MOVEMENT_HISTORY", "PRODUCT_MOVEMENT_SUMMARY", "PRODUCT_MOVEMENTS_BY_DATE_RANGE" ->
                        "No inventory movement records were found for " + state.text("searchText") + ".";
                case "RECENT_INVENTORY_MOVEMENTS" -> "No inventory movements have been recorded yet.";
                case "INVENTORY_ADJUSTMENTS" -> "No inventory adjustment records were found.";
                case "LOW_STOCK_PRODUCTS" -> "There are currently no active products below their minimum stock level.";
                case "PRODUCT_COMPARISON" -> "None of the requested products could be found. Please verify the product codes or part numbers.";
                case "PURCHASE_SEARCH", "PURCHASE_DATE_RANGE", "PURCHASES_BY_SUPPLIER" -> "No purchase records were found matching your request.";
                case "SALES_SEARCH", "SALES_DATE_RANGE", "SALES_BY_CUSTOMER" -> "No sales invoices were found matching your request.";
                case "CUSTOMER_SEARCH" -> "No matching customer records were found.";
                case "CUSTOMER_SALES_HISTORY" -> "No sales invoices found for this customer.";
                case "SUPPLIER_SEARCH" -> "No matching supplier records were found.";
                case "SUPPLIER_PURCHASE_HISTORY" -> "No purchase orders found for this supplier.";
                default -> "I couldn't find any matching records in the ERP system.";
            };
            return Map.of("finalResponse", emptyMessage);
        }

        // Empty map handling
        if (toolResult instanceof Map<?, ?> map && map.isEmpty()) {
            String emptyMessage = switch (intent) {
                case "PURCHASE_DETAILS", "PURCHASE_STATUS" -> "No purchase order found for " + state.text("searchText") + ".";
                case "SALE_DETAILS", "SALE_STATUS" -> "No sales invoice found for " + state.text("searchText") + ".";
                case "CUSTOMER_DETAILS", "CUSTOMER_OUTSTANDING" -> "No customer records found for " + state.text("searchText") + ".";
                case "SUPPLIER_DETAILS", "SUPPLIER_SUMMARY" -> "No supplier records found for " + state.text("searchText") + ".";
                default -> "No matching records were found.";
            };
            return Map.of("finalResponse", emptyMessage);
        }

        // Product Count
        if ("COUNT_PRODUCTS".equals(intent)) {
            if (!(toolResult instanceof Number count)) {
                return Map.of("finalResponse", "I couldn't retrieve the active product count. Please try again.");
            }
            return Map.of("finalResponse", "There are " + count.longValue() + " active products in the ERP system.");
        }

        // Deterministic Map-based formatting (Single record or summary)
        if (toolResult instanceof Map<?, ?> map) {
            String formatted = switch (intent) {
                case "PURCHASE_DETAILS" -> formatPurchaseDetails(map);
                case "PURCHASE_SUMMARY" -> formatPurchaseSummary(map);
                case "PURCHASE_STATUS" -> formatPurchaseStatus(map);
                case "SALE_DETAILS" -> formatSaleDetails(map);
                case "SALES_SUMMARY" -> formatSalesSummary(map);
                case "SALE_STATUS" -> formatSaleStatus(map);
                case "CUSTOMER_DETAILS" -> formatCustomerDetails(map);
                case "CUSTOMER_OUTSTANDING" -> formatCustomerOutstanding(map);
                case "SUPPLIER_DETAILS" -> formatSupplierDetails(map);
                case "SUPPLIER_SUMMARY" -> formatSupplierSummary(map);
                default -> null;
            };
            if (formatted != null) {
                return Map.of("finalResponse", formatted);
            }
        }

        // Deterministic List-based formatting
        if (toolResult instanceof List<?> rows) {
            String formatted = switch (intent) {
                case "PRODUCT_SEARCH" -> formatProductSearch(rows);
                case "PRODUCT_COMPARISON" -> formatProductComparison(rows, productIdentifiers(state.object("productIdentifiers")));
                case "PRODUCT_DETAILS" -> formatProductDetails(rows);
                case "LOW_STOCK_PRODUCTS" -> formatLowStockProducts(rows);
                case "PRODUCT_STOCK" -> formatProductStockResponse(rows);
                case "PRODUCT_MOVEMENT_HISTORY", "PRODUCT_MOVEMENTS_BY_DATE_RANGE", "RECENT_INVENTORY_MOVEMENTS" ->
                        formatMovementHistoryResponse(rows);
                case "PRODUCT_MOVEMENT_SUMMARY" -> formatMovementSummary(rows);
                case "INVENTORY_ADJUSTMENTS" -> formatAdjustmentHistory(rows);
                case "PURCHASE_SEARCH", "PURCHASE_DATE_RANGE", "PURCHASES_BY_SUPPLIER" -> formatPurchases(rows);
                case "SALES_SEARCH", "SALES_DATE_RANGE", "SALES_BY_CUSTOMER" -> formatSales(rows);
                case "CUSTOMER_SEARCH" -> formatCustomers(rows);
                case "CUSTOMER_SALES_HISTORY" -> formatCustomerSalesHistory(rows);
                case "SUPPLIER_SEARCH" -> formatSuppliers(rows);
                case "SUPPLIER_PURCHASE_HISTORY" -> formatSupplierPurchases(rows);
                default -> null;
            };
            if (formatted != null) {
                return Map.of("finalResponse", formatted);
            }
        }

        // LLM prompt fallback for general questions
        String toolContext = toolResult != null
                ? "\n\nVERIFIED ERP TOOL RESULT:\n" + toolResult + "\n\nUse only the returned values. If missing, say unavailable."
                : "";

        List<Message> promptMessages = new ArrayList<>();
        promptMessages.add(new SystemMessage(systemPrompt + toolContext));

        for (Map<String, String> item : state.history()) {
            String role = item.get("role");
            String content = item.get("content");
            if (StringUtils.hasText(content) && StringUtils.hasText(role)) {
                if ("USER".equalsIgnoreCase(role)) {
                    promptMessages.add(new UserMessage(content));
                } else if ("ASSISTANT".equalsIgnoreCase(role)) {
                    promptMessages.add(new AssistantMessage(content));
                }
            }
        }

        if (promptMessages.size() == 1) {
            promptMessages.add(new UserMessage(state.text("currentMessage")));
        }

        var chatResponse = chatModel.call(new Prompt(promptMessages));
        if (chatResponse == null || chatResponse.getResult() == null || chatResponse.getResult().getOutput() == null) {
            throw new AiProviderResponseException("AI provider returned an empty response. Please try again.");
        }

        String response = chatResponse.getResult().getOutput().getText();
        if (!StringUtils.hasText(response)) {
            throw new AiProviderResponseException("AI provider returned an empty response. Please try again.");
        }

        return Map.of("finalResponse", response.trim());
    }

    // --------------------------------------------------
    // FORMATTING HELPERS: PURCHASE
    // --------------------------------------------------

    private String formatPurchases(List<?> rows) {
        StringBuilder out = new StringBuilder("Matching purchases:");
        int index = 0;
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> pur)) continue;
            index++;
            out.append("\n\n").append(index).append(". Purchase: ").append(value(pur, "purchase_number"))
                    .append("\nSupplier: ").append(value(pur, "supplier_name"))
                    .append("\nSupplier invoice: ").append(value(pur, "supplier_invoice_no"))
                    .append("\nDate: ").append(value(pur, "purchase_date"))
                    .append("\nStatus: ").append(value(pur, "status"))
                    .append("\nTotal: ₹").append(value(pur, "grand_total"));
        }
        return index == 0 ? "No purchase records were found." : out.toString();
    }

    private String formatPurchaseDetails(Map<?, ?> pur) {
        StringBuilder out = new StringBuilder("Purchase details:\n")
                .append("Purchase number: ").append(value(pur, "purchase_number"))
                .append("\nSupplier: ").append(value(pur, "supplier_name"))
                .append("\nSupplier invoice: ").append(value(pur, "supplier_invoice_no"))
                .append("\nDate: ").append(value(pur, "purchase_date"))
                .append("\nStatus: ").append(value(pur, "status"))
                .append("\nSubtotal: ₹").append(value(pur, "subtotal"))
                .append("\nTax: ₹").append(value(pur, "tax_total"))
                .append("\nGrand total: ₹").append(value(pur, "grand_total"));

        if (pur.get("items") instanceof List<?> items && !items.isEmpty()) {
            out.append("\n\nLine items:");
            int idx = 0;
            for (Object itemObj : items) {
                if (!(itemObj instanceof Map<?, ?> item)) continue;
                idx++;
                out.append("\n  ").append(idx).append(". ").append(value(item, "product_name"))
                        .append(" (").append(value(item, "product_code")).append(")")
                        .append(" - Qty: ").append(value(item, "quantity"))
                        .append(" @ ₹").append(value(item, "purchase_price"))
                        .append(" = ₹").append(value(item, "line_total"));
            }
        }
        return out.toString();
    }

    private String formatPurchaseSummary(Map<?, ?> data) {
        return """
                Purchase summary:
                Total purchases: %s
                Total procurement amount: ₹%s
                Received amount: ₹%s (Count: %s)
                Draft amount: ₹%s (Count: %s)
                Cancelled amount: ₹%s (Count: %s)
                """.formatted(
                value(data, "total_count"),
                value(data, "total_amount"),
                value(data, "received_amount"), value(data, "received_count"),
                value(data, "draft_amount"), value(data, "draft_count"),
                value(data, "cancelled_amount"), value(data, "cancelled_count")
        ).trim();
    }

    private String formatPurchaseStatus(Map<?, ?> data) {
        return """
                Purchase status:
                Purchase number: %s
                Supplier: %s
                Supplier invoice: %s
                Status: %s
                Date: %s
                Grand total: ₹%s
                """.formatted(
                value(data, "purchase_number"),
                value(data, "supplier_name"),
                value(data, "supplier_invoice_no"),
                value(data, "status"),
                value(data, "purchase_date"),
                value(data, "grand_total")
        ).trim();
    }

    // --------------------------------------------------
    // FORMATTING HELPERS: SALES
    // --------------------------------------------------

    private String formatSales(List<?> rows) {
        StringBuilder out = new StringBuilder("Matching sales invoices:");
        int index = 0;
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> sale)) continue;
            index++;
            out.append("\n\n").append(index).append(". Invoice: ").append(value(sale, "invoice_number"))
                    .append("\nCustomer: ").append(value(sale, "customer_name"))
                    .append("\nDate: ").append(value(sale, "sale_date"))
                    .append("\nStatus: ").append(value(sale, "status"))
                    .append("\nPayment status: ").append(value(sale, "payment_status"))
                    .append("\nGrand total: ₹").append(value(sale, "grand_total"))
                    .append("\nBalance: ₹").append(value(sale, "balance_amount"));
        }
        return index == 0 ? "No sales invoices were found." : out.toString();
    }

    private String formatSaleDetails(Map<?, ?> sale) {
        StringBuilder out = new StringBuilder("Sale invoice details:\n")
                .append("Invoice number: ").append(value(sale, "invoice_number"))
                .append("\nCustomer: ").append(value(sale, "customer_name"))
                .append(" (Phone: ").append(value(sale, "customer_phone")).append(")")
                .append("\nDate: ").append(value(sale, "sale_date"))
                .append("\nStatus: ").append(value(sale, "status"))
                .append("\nPayment status: ").append(value(sale, "payment_status"))
                .append("\nSubtotal: ₹").append(value(sale, "subtotal"))
                .append("\nTax: ₹").append(value(sale, "total_tax_amount"))
                .append("\nGrand total: ₹").append(value(sale, "grand_total"))
                .append("\nPaid amount: ₹").append(value(sale, "paid_amount"))
                .append("\nBalance due: ₹").append(value(sale, "balance_amount"));

        if (sale.get("items") instanceof List<?> items && !items.isEmpty()) {
            out.append("\n\nItems:");
            int idx = 0;
            for (Object itemObj : items) {
                if (!(itemObj instanceof Map<?, ?> item)) continue;
                idx++;
                out.append("\n  ").append(idx).append(". ").append(value(item, "product_name"))
                        .append(" (").append(value(item, "product_code")).append(")")
                        .append(" - Qty: ").append(value(item, "quantity")).append(" ").append(value(item, "unit"))
                        .append(" @ ₹").append(value(item, "unit_price"))
                        .append(" = ₹").append(value(item, "line_total"));
            }
        }

        if (sale.get("payments") instanceof List<?> payments && !payments.isEmpty()) {
            out.append("\n\nPayments:");
            int idx = 0;
            for (Object payObj : payments) {
                if (!(payObj instanceof Map<?, ?> pay)) continue;
                idx++;
                out.append("\n  ").append(idx).append(". ").append(value(pay, "payment_method"))
                        .append(" - ₹").append(value(pay, "amount"))
                        .append(" (Date: ").append(value(pay, "payment_date")).append(")");
            }
        }
        return out.toString();
    }

    private String formatSalesSummary(Map<?, ?> data) {
        return """
                Sales summary:
                Total invoices: %s
                Total invoiced amount: ₹%s
                Completed revenue: ₹%s (Invoices: %s)
                Total collected / paid: ₹%s
                Outstanding balance: ₹%s
                Fully paid invoices: %s
                Partially paid invoices: %s
                Unpaid invoices: %s
                Cancelled invoices: %s
                """.formatted(
                value(data, "total_count"),
                value(data, "total_amount"),
                value(data, "completed_amount"), value(data, "completed_count"),
                value(data, "total_paid"),
                value(data, "total_balance"),
                value(data, "paid_invoices_count"),
                value(data, "partially_paid_count"),
                value(data, "unpaid_count"),
                value(data, "cancelled_count")
        ).trim();
    }

    private String formatSaleStatus(Map<?, ?> data) {
        return """
                Invoice status:
                Invoice number: %s
                Customer: %s
                Status: %s
                Payment status: %s
                Date: %s
                Grand total: ₹%s
                Paid amount: ₹%s
                Balance due: ₹%s
                """.formatted(
                value(data, "invoice_number"),
                value(data, "customer_name"),
                value(data, "status"),
                value(data, "payment_status"),
                value(data, "sale_date"),
                value(data, "grand_total"),
                value(data, "paid_amount"),
                value(data, "balance_amount")
        ).trim();
    }

    // --------------------------------------------------
    // FORMATTING HELPERS: CUSTOMER
    // --------------------------------------------------

    private String formatCustomers(List<?> rows) {
        StringBuilder out = new StringBuilder("Matching customers:");
        int index = 0;
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> cust)) continue;
            index++;
            out.append("\n\n").append(index).append(". ").append(value(cust, "customer_name"))
                    .append("\nCustomer code: ").append(value(cust, "customer_code"))
                    .append("\nPhone: ").append(value(cust, "phone"))
                    .append("\nCity: ").append(value(cust, "city"))
                    .append("\nActive: ").append(value(cust, "active"));
        }
        return index == 0 ? "No customer records were found." : out.toString();
    }

    private String formatCustomerDetails(Map<?, ?> cust) {
        return """
                Customer details:
                Name: %s
                Customer code: %s
                Phone: %s
                Email: %s
                City: %s
                State: %s
                Pincode: %s
                GSTIN: %s
                Active: %s
                """.formatted(
                value(cust, "customer_name"),
                value(cust, "customer_code"),
                value(cust, "phone"),
                value(cust, "email"),
                value(cust, "city"),
                value(cust, "state"),
                value(cust, "pincode"),
                value(cust, "gstin"),
                value(cust, "active")
        ).trim();
    }

    private String formatCustomerSalesHistory(List<?> rows) {
        StringBuilder out = new StringBuilder("Customer sales history:");
        int index = 0;
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> sale)) continue;
            index++;
            out.append("\n\n").append(index).append(". Invoice: ").append(value(sale, "invoice_number"))
                    .append("\nDate: ").append(value(sale, "sale_date"))
                    .append("\nStatus: ").append(value(sale, "status"))
                    .append("\nPayment status: ").append(value(sale, "payment_status"))
                    .append("\nGrand total: ₹").append(value(sale, "grand_total"))
                    .append("\nBalance due: ₹").append(value(sale, "balance_amount"));
        }
        return index == 0 ? "No sales history found for this customer." : out.toString();
    }

    private String formatCustomerOutstanding(Map<?, ?> data) {
        return """
                Customer account summary:
                Customer: %s
                Customer code: %s
                Phone: %s
                Total completed invoices: %s
                Total invoiced amount: ₹%s
                Total paid amount: ₹%s
                Current outstanding balance: ₹%s
                """.formatted(
                value(data, "customer_name"),
                value(data, "customer_code"),
                value(data, "phone"),
                value(data, "total_invoices"),
                value(data, "total_invoiced"),
                value(data, "total_paid"),
                value(data, "outstanding_balance")
        ).trim();
    }

    // --------------------------------------------------
    // FORMATTING HELPERS: SUPPLIER
    // --------------------------------------------------

    private String formatSuppliers(List<?> rows) {
        StringBuilder out = new StringBuilder("Matching suppliers:");
        int index = 0;
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> sup)) continue;
            index++;
            out.append("\n\n").append(index).append(". ").append(value(sup, "supplier_name"))
                    .append("\nContact person: ").append(value(sup, "contact_person"))
                    .append("\nPhone: ").append(value(sup, "phone"))
                    .append("\nCity: ").append(value(sup, "city"))
                    .append("\nPayment terms: ").append(value(sup, "payment_terms_days")).append(" days")
                    .append("\nActive: ").append(value(sup, "active"));
        }
        return index == 0 ? "No supplier records were found." : out.toString();
    }

    private String formatSupplierDetails(Map<?, ?> sup) {
        return """
                Supplier details:
                Supplier: %s
                Contact person: %s
                Phone: %s
                Email: %s
                Address: %s
                City: %s, State: %s
                Pincode: %s
                GSTIN: %s
                Payment terms: %s days
                Active: %s
                """.formatted(
                value(sup, "supplier_name"),
                value(sup, "contact_person"),
                value(sup, "phone"),
                value(sup, "email"),
                value(sup, "address"),
                value(sup, "city"), value(sup, "state"),
                value(sup, "pincode"),
                value(sup, "gstin"),
                value(sup, "payment_terms_days"),
                value(sup, "active")
        ).trim();
    }

    private String formatSupplierPurchases(List<?> rows) {
        StringBuilder out = new StringBuilder("Supplier purchase history:");
        int index = 0;
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> pur)) continue;
            index++;
            out.append("\n\n").append(index).append(". Purchase: ").append(value(pur, "purchase_number"))
                    .append("\nSupplier invoice: ").append(value(pur, "supplier_invoice_no"))
                    .append("\nDate: ").append(value(pur, "purchase_date"))
                    .append("\nStatus: ").append(value(pur, "status"))
                    .append("\nGrand total: ₹").append(value(pur, "grand_total"));
        }
        return index == 0 ? "No purchase orders found for this supplier." : out.toString();
    }

    private String formatSupplierSummary(Map<?, ?> data) {
        return """
                Supplier procurement summary:
                Supplier: %s
                Total purchase orders: %s
                Total procurement amount: ₹%s
                Received amount: ₹%s (Orders: %s)
                Draft orders: %s
                Cancelled orders: %s
                """.formatted(
                value(data, "supplier_name"),
                value(data, "total_purchases"),
                value(data, "total_purchased"),
                value(data, "total_received_amount"), value(data, "received_count"),
                value(data, "draft_count"),
                value(data, "cancelled_count")
        ).trim();
    }

    // --------------------------------------------------
    // FORMATTING HELPERS: PRODUCT & INVENTORY
    // --------------------------------------------------

    @SuppressWarnings("unchecked")
    private List<String> productIdentifiers(Object raw) {
        if (raw instanceof List<?> values) {
            return values.stream().filter(String.class::isInstance)
                    .map(String.class::cast).toList();
        }
        return List.of();
    }

    private String formatProductComparison(List<?> rows, List<String> requestedIdentifiers) {
        StringBuilder out = new StringBuilder("Product comparison:");
        java.util.Set<String> found = new java.util.HashSet<>();
        int index = 0;
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> product)) continue;
            index++;
            String productCode = value(product, "product_code");
            String partNo = value(product, "part_no");
            found.add(productCode.toLowerCase(Locale.ROOT));
            found.add(partNo.toLowerCase(Locale.ROOT));
            out.append("\n\n").append(index).append(". ").append(value(product, "name"))
                    .append("\nProduct code: ").append(productCode)
                    .append("\nPart number: ").append(partNo)
                    .append("\nAvailable quantity: ").append(value(product, "qty"))
                    .append(" ").append(value(product, "unit"))
                    .append("\nMinimum stock: ").append(value(product, "minimum_stock"))
                    .append(" ").append(value(product, "unit"))
                    .append("\nPurchase price: ").append(value(product, "purchase_price"))
                    .append("\nSelling price: ").append(value(product, "selling_price"));
        }
        List<String> missing = requestedIdentifiers.stream()
                .filter(id -> !found.contains(id.toLowerCase(Locale.ROOT)))
                .toList();
        if (!missing.isEmpty()) {
            out.append("\n\nNot found: ").append(String.join(", ", missing));
        }
        return index == 0 ? "None of the requested products could be found." : out.toString();
    }

    private String formatProductSearch(List<?> rows) {
        StringBuilder out = new StringBuilder("Matching products:");
        int index = 0;
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> product)) continue;
            index++;
            out.append("\n\n").append(index).append(". ").append(value(product, "name"))
                    .append("\nProduct code: ").append(value(product, "product_code"))
                    .append("\nPart number: ").append(value(product, "part_no"))
                    .append("\nAvailable quantity: ").append(value(product, "qty"))
                    .append(" ").append(value(product, "unit"));
        }
        return index == 0 ? "No matching products were found." : out.toString();
    }

    private String formatProductDetails(List<?> rows) {
        if (rows.size() != 1 || !(rows.get(0) instanceof Map<?, ?> product)) {
            return rows.isEmpty() ? "No matching product was found."
                    : "Multiple products matched. Please provide an exact product code or part number.";
        }
        return """
                Product details:
                Product: %s
                Product code: %s
                Part number: %s
                Description: %s
                Purchase price: %s
                Selling price: %s
                Available quantity: %s %s
                Minimum stock: %s %s
                Barcode: %s
                Active: %s
                """.formatted(value(product, "name"), value(product, "product_code"),
                value(product, "part_no"), value(product, "description"),
                value(product, "purchase_price"), value(product, "selling_price"),
                value(product, "qty"), value(product, "unit"),
                value(product, "minimum_stock"), value(product, "unit"),
                value(product, "barcode"), value(product, "active")).trim();
    }

    private String formatLowStockProducts(List<?> rows) {
        StringBuilder out = new StringBuilder("Products at or below minimum stock:");
        int index = 0;
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> product)) continue;
            index++;
            out.append("\n\n").append(index).append(". ").append(value(product, "name"))
                    .append("\nProduct code: ").append(value(product, "product_code"))
                    .append("\nAvailable: ").append(value(product, "qty"))
                    .append(" ").append(value(product, "unit"))
                    .append("\nMinimum: ").append(value(product, "minimum_stock"))
                    .append(" ").append(value(product, "unit"));
        }
        return index == 0 ? "There are currently no active products below their minimum stock level."
                : out.toString();
    }

    private String formatMovementSummary(List<?> rows) {
        if (rows.isEmpty() || !(rows.get(0) instanceof Map<?, ?> first)) {
            return "No inventory movement summary is available.";
        }
        StringBuilder out = new StringBuilder("Inventory movement summary:\n")
                .append("Product: ").append(value(first, "product_name"))
                .append("\nProduct code: ").append(value(first, "product_code"))
                .append("\nCurrent stock: ").append(value(first, "current_stock"))
                .append(" ").append(value(first, "unit"));
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> movement)) continue;
            out.append("\n\n").append(value(movement, "movement_type"))
                    .append("\nMovement count: ").append(value(movement, "movement_count"))
                    .append("\nTotal quantity moved: ").append(value(movement, "total_quantity_moved"))
                    .append("\nNet quantity change: ").append(value(movement, "net_quantity_change"))
                    .append("\nFirst movement: ").append(value(movement, "first_movement_at"))
                    .append("\nLatest movement: ").append(value(movement, "latest_movement_at"));
        }
        return out.toString();
    }

    private String formatAdjustmentHistory(List<?> rows) {
        StringBuilder out = new StringBuilder("Inventory adjustment history:");
        int index = 0;
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> adjustment)) continue;
            index++;
            out.append("\n\n").append(index).append(". ").append(value(adjustment, "reason"))
                    .append("\nProduct: ").append(value(adjustment, "product_name"))
                    .append("\nProduct code: ").append(value(adjustment, "product_code"))
                    .append("\nQuantity change: ").append(value(adjustment, "quantity_change"))
                    .append("\nQuantity before: ").append(value(adjustment, "quantity_before"))
                    .append("\nQuantity after: ").append(value(adjustment, "quantity_after"))
                    .append("\nNotes: ").append(value(adjustment, "adjustment_notes"))
                    .append("\nDate: ").append(value(adjustment, "adjustment_created_at"));
        }
        return index == 0 ? "No inventory adjustment records were found." : out.toString();
    }

    private int parseLimit(Object rawLimit) {
        if (rawLimit instanceof Number number) {
            return Math.max(1, Math.min(50, number.intValue()));
        }
        return 10;
    }

    private String formatProductStockResponse(List<?> results) {
        if (results.size() > 1) {
            return "Multiple products matched your search. Please provide an exact product code or part number.";
        }
        if (results.isEmpty() || !(results.get(0) instanceof Map<?, ?> product)) {
            return "No matching product was found.";
        }

        return """
                Current stock information:

                Product: %s
                Product code: %s
                Part number: %s
                Available quantity: %s %s
                Minimum stock level: %s %s
                """.formatted(
                value(product, "name"),
                value(product, "product_code"),
                value(product, "part_no"),
                value(product, "qty"),
                value(product, "unit"),
                value(product, "minimum_stock"),
                value(product, "unit")
        ).trim();
    }

    private String formatMovementHistoryResponse(List<?> results) {
        StringBuilder response = new StringBuilder("Inventory movement history:");
        int recordNumber = 0;
        for (Object item : results) {
            if (!(item instanceof Map<?, ?> movement)) continue;
            recordNumber++;
            response.append("\n\n").append(recordNumber).append(". ").append(value(movement, "movement_type"))
                    .append("\nProduct code: ").append(value(movement, "product_code"))
                    .append("\nProduct name: ").append(value(movement, "product_name"))
                    .append("\nQuantity change: ").append(value(movement, "quantity_change"))
                    .append("\nQuantity before: ").append(value(movement, "quantity_before"))
                    .append("\nQuantity after: ").append(value(movement, "quantity_after"))
                    .append("\nReference type: ").append(value(movement, "reference_type"))
                    .append("\nDate: ").append(value(movement, "created_at"));

            String reason = value(movement, "adjustment_reason");
            if (!"Not available".equals(reason)) {
                response.append("\nAdjustment reason: ").append(reason);
            }
            String notes = value(movement, "adjustment_notes");
            if (!"Not available".equals(notes)) {
                response.append("\nAdjustment notes: ").append(notes);
            }
        }
        return recordNumber == 0 ? "No valid inventory movement records were found." : response.toString();
    }

    private String value(Map<?, ?> data, String key) {
        Object result = data.get(key);
        return result == null ? "Not available" : String.valueOf(result);
    }

    // --------------------------------------------------
    // 4. UNSUPPORTED REQUEST NODE
    // --------------------------------------------------

    public Map<String, Object> unsupportedResponse(
            ErpAiGraphState state,
            RunnableConfig config
    ) {
        return Map.of(
                "finalResponse",
                """
                This operation is not currently available through the ERP AI assistant.

                Currently, I can help with:
                - Product searches, comparisons, stock levels, counts, and low-stock queries
                - Inventory movement history, summaries, date-range logs, and adjustments
                - Purchase orders, purchase details, supplier purchases, and procurement totals
                - Sales invoices, invoice details, customer sales, and revenue summaries
                - Customer lookup, customer sales history, and outstanding receivable balances
                - Supplier directory, contact information, purchase history, and spend summaries
                """
        );
    }

    // --------------------------------------------------
    // 5. CLARIFICATION NODE
    // --------------------------------------------------

    public Map<String, Object> clarificationResponse(
            ErpAiGraphState state,
            RunnableConfig config
    ) {
        return Map.of(
                "finalResponse",
                StringUtils.hasText(state.text("clarificationMessage"))
                        ? state.text("clarificationMessage")
                        : "Please provide the relevant product code, invoice number, purchase number, customer, or supplier identifier."
        );
    }

    // --------------------------------------------------
    // 6. RESPONSE VALIDATION NODE
    // --------------------------------------------------

    public Map<String, Object> validateResponse(
            ErpAiGraphState state,
            RunnableConfig config
    ) {
        String response = state.text("finalResponse");
        if (!StringUtils.hasText(response)) {
            response = "I couldn't generate a response. Please try again.";
        }
        return Map.of("finalResponse", response.trim());
    }

    // --------------------------------------------------
    // ROUTING HELPERS
    // --------------------------------------------------

    public static String routeIntent(ErpAiGraphState state) {
        String intent = state.text("intent");
        if (!StringUtils.hasText(intent)) {
            return "CLARIFICATION";
        }

        return switch (intent) {
            // Products & Inventory (Stage 2B)
            case "PRODUCT_SEARCH",
                 "PRODUCT_COMPARISON",
                 "PRODUCT_DETAILS",
                 "PRODUCT_STOCK",
                 "COUNT_PRODUCTS",
                 "LOW_STOCK_PRODUCTS",
                 "PRODUCT_MOVEMENT_HISTORY",
                 "PRODUCT_MOVEMENT_SUMMARY",
                 "PRODUCT_MOVEMENTS_BY_DATE_RANGE",
                 "RECENT_INVENTORY_MOVEMENTS",
                 "INVENTORY_ADJUSTMENTS",

            // Purchases (Stage 2C)
                 "PURCHASE_SEARCH",
                 "PURCHASE_DETAILS",
                 "PURCHASE_DATE_RANGE",
                 "PURCHASES_BY_SUPPLIER",
                 "PURCHASE_SUMMARY",
                 "PURCHASE_STATUS",

            // Sales (Stage 2C)
                 "SALES_SEARCH",
                 "SALE_DETAILS",
                 "SALES_DATE_RANGE",
                 "SALES_BY_CUSTOMER",
                 "SALES_SUMMARY",
                 "SALE_STATUS",

            // Customers (Stage 2C)
                 "CUSTOMER_SEARCH",
                 "CUSTOMER_DETAILS",
                 "CUSTOMER_SALES_HISTORY",
                 "CUSTOMER_OUTSTANDING",

            // Suppliers (Stage 2C)
                 "SUPPLIER_SEARCH",
                 "SUPPLIER_DETAILS",
                 "SUPPLIER_PURCHASE_HISTORY",
                 "SUPPLIER_SUMMARY" -> "TOOL";

            case "UNSUPPORTED" -> "UNSUPPORTED";
            case "CLARIFICATION" -> "CLARIFICATION";
            case "GENERAL" -> "GENERAL";
            default -> "CLARIFICATION";
        };
    }

    // --------------------------------------------------
    // LOAD SYSTEM PROMPT
    // --------------------------------------------------

    private String loadSystemPrompt() {
        try {
            var resource = new org.springframework.core.io.ClassPathResource("prompts/erp-system-prompt.st");
            try (var inputStream = resource.getInputStream()) {
                return new String(inputStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load ERP AI system prompt", exception);
        }
    }
}
