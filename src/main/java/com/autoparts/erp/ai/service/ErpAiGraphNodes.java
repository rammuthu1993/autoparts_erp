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
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.autoparts.erp.ai.exception.AiProviderResponseException;
import com.autoparts.erp.ai.tools.ProductLookupTool;
import com.autoparts.erp.ai.tools.InventoryHistoryLookupTool;

@Component
public class ErpAiGraphNodes {

    private static final Logger log =
            LoggerFactory.getLogger(ErpAiGraphNodes.class);
    private final InventoryHistoryLookupTool inventoryHistoryLookupTool;

    private final ChatModel chatModel;
    private final ProductLookupTool productLookupTool;
    private final ErpAiIntentClassifier intentClassifier;
    private final String systemPrompt;

    public ErpAiGraphNodes(
            ChatModel chatModel,
            ProductLookupTool productLookupTool,
            ErpAiIntentClassifier intentClassifier,
            InventoryHistoryLookupTool inventoryHistoryLookupTool
    ) {
        this.chatModel = chatModel;
        this.productLookupTool = productLookupTool;
        this.intentClassifier = intentClassifier;
        this.systemPrompt = loadSystemPrompt();
        this.inventoryHistoryLookupTool = inventoryHistoryLookupTool;
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

        // Resolve only deterministic product references from this conversation's USER history.
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
    // 2. PRODUCT TOOL EXECUTION NODE
    // --------------------------------------------------

    public Map<String, Object> executeProductTool(
            ErpAiGraphState state,
            RunnableConfig config
    ) {

        String intent = state.text("intent");
        String searchText = state.text("searchText");

        log.info(
                "ERP AI tool execution started. Tool intent: {}",
                intent
        );

        try {

            Object result = switch (intent) {

                case "PRODUCT_SEARCH" ->
                        productLookupTool.searchProducts(searchText);

                case "PRODUCT_COMPARISON" ->
                        productLookupTool.getProductsByIdentifiers(
                                productIdentifiers(state.object("productIdentifiers")));

                case "PRODUCT_DETAILS" ->
                        productLookupTool.getProductDetails(searchText);

                case "PRODUCT_STOCK" ->
                        productLookupTool.getProductStock(searchText);

                case "COUNT_PRODUCTS" ->
                        productLookupTool.countProducts();

                case "LOW_STOCK_PRODUCTS" ->
                        productLookupTool.getLowStockProducts();
                        
                case "PRODUCT_MOVEMENT_HISTORY" ->
                        inventoryHistoryLookupTool.getProductMovementHistory(searchText);

                case "PRODUCT_MOVEMENT_SUMMARY" ->
                        inventoryHistoryLookupTool.getProductMovementSummary(searchText);

                case "PRODUCT_MOVEMENTS_BY_DATE_RANGE" ->
                        inventoryHistoryLookupTool.getProductMovementsByDateRange(
                                searchText, state.text("startDate"), state.text("endDate"));

                case "RECENT_INVENTORY_MOVEMENTS" ->
                        inventoryHistoryLookupTool.getRecentInventoryMovements(
                                parseLimit(state.object("limit")));

                case "INVENTORY_ADJUSTMENTS" ->
                        inventoryHistoryLookupTool.getInventoryAdjustments(
                                searchText, parseLimit(state.object("limit")));

                default -> throw new IllegalArgumentException(
                        "Unsupported product tool intent: " + intent
                );
            };

            log.info(
                    "ERP AI tool execution completed. Intent: {}",
                    intent
            );

            return Map.of(
                    "toolResult", result,
                    "toolError", ""
            );

        } catch (CancellationException exception) {

            throw exception;

        } catch (RuntimeException exception) {

            log.error(
                    "ERP AI tool execution failed. Intent: {}",
                    intent,
                    exception
            );

            return Map.of(
                    "toolResult", "",
                    "toolError",
                    "Unable to retrieve the requested ERP information."
            );
        }
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
	        return Map.of(
	                "finalResponse",
	                "I couldn't retrieve the requested information from the ERP system. Please try again."
	        );
	    }
	
	    Object toolResult = state.object("toolResult");
	    String intent = state.text("intent");
	
	    // ----------------------------------------------
	    // Handle empty results before formatting.
    if (toolResult instanceof List<?> results && results.isEmpty()) {
        String emptyMessage = switch (intent) {
            case "PRODUCT_MOVEMENT_HISTORY", "PRODUCT_MOVEMENT_SUMMARY",
                 "PRODUCT_MOVEMENTS_BY_DATE_RANGE" ->
                    "No inventory movement records were found for " + state.text("searchText") + ".";
            case "RECENT_INVENTORY_MOVEMENTS" -> "No inventory movements have been recorded yet.";
            case "INVENTORY_ADJUSTMENTS" -> "No inventory adjustment records were found.";
            case "LOW_STOCK_PRODUCTS" ->
                    "There are currently no active products below their minimum stock level.";
            case "PRODUCT_COMPARISON" ->
                    "None of the requested products could be found. Please verify the product codes or part numbers.";
            default -> "I couldn't find any matching records in the ERP system.";
        };
        return Map.of("finalResponse", emptyMessage);
    }

        // Handle product count directly in Java
	    // ----------------------------------------------
	
	    if ("COUNT_PRODUCTS".equals(intent)) {
	
	        if (!(toolResult instanceof Number count)) {
	
	            log.error(
	                    "Unexpected product count result. Result type: {}",
	                    toolResult == null
	                            ? "null"
	                            : toolResult.getClass().getName()
	            );
	
	            return Map.of(
	                    "finalResponse",
	                    "I couldn't retrieve the active product count. Please try again."
	            );
	        }
	
	        long total = count.longValue();
	
	        log.info("Active product count response generated.");
	
	        return Map.of(
	                "finalResponse",
	                "There are " + total
	                        + " active products in the ERP system."
	        );
	    }
	
	    // ----------------------------------------------
	    // Handle product stock directly in Java
	    // ----------------------------------------------
	
	    if ("PRODUCT_STOCK".equals(intent)
	            && toolResult instanceof List<?> results) {
	
	        return Map.of(
	                "finalResponse",
	                formatProductStockResponse(results)
	        );
	    }
	
	    // ----------------------------------------------
	    // Handle inventory movement history directly in Java
	    // ----------------------------------------------
	
	    if ("PRODUCT_MOVEMENT_HISTORY".equals(intent)
	            && toolResult instanceof List<?> results) {
	
	        return Map.of(
	                "finalResponse",
	                formatMovementHistoryResponse(results)
	        );
	    }
	
	    // ----------------------------------------------
	    // Format database-backed ERP responses deterministically; no LLM is needed for facts.
    if (toolResult instanceof List<?> rows) {
        String formatted = switch (intent) {
            case "PRODUCT_SEARCH" -> formatProductSearch(rows);
            case "PRODUCT_COMPARISON" -> formatProductComparison(
                    rows, productIdentifiers(state.object("productIdentifiers")));
            case "PRODUCT_DETAILS" -> formatProductDetails(rows);
            case "LOW_STOCK_PRODUCTS" -> formatLowStockProducts(rows);
            case "PRODUCT_STOCK" -> formatProductStockResponse(rows);
            case "PRODUCT_MOVEMENT_HISTORY", "PRODUCT_MOVEMENTS_BY_DATE_RANGE",
                 "RECENT_INVENTORY_MOVEMENTS" -> formatMovementHistoryResponse(rows);
            case "PRODUCT_MOVEMENT_SUMMARY" -> formatMovementSummary(rows);
            case "INVENTORY_ADJUSTMENTS" -> formatAdjustmentHistory(rows);
            default -> null;
        };
        if (formatted != null) {
            return Map.of("finalResponse", formatted);
        }
    }

        // Prepare verified ERP tool result for LLM
	    // ----------------------------------------------
	
	    String toolContext = "";
	
	    if (toolResult != null) {
	
	        toolContext = """
	
	                VERIFIED ERP TOOL RESULT:
	
	                %s
	
	                IMPORTANT:
	                - This result came from the actual ERP database.
	                - Use only the returned values.
	                - Do not invent product codes or quantities.
	                - If a value is missing, say it is unavailable.
	                """.formatted(toolResult);
	    }
	
	    // ----------------------------------------------
	    // Prepare conversation messages
	    // ----------------------------------------------
	
	    List<Message> promptMessages = new ArrayList<>();
	
	    promptMessages.add(
	            new SystemMessage(systemPrompt + toolContext)
	    );
	
	    for (Map<String, String> item : state.history()) {
	
	        String role = item.get("role");
	        String content = item.get("content");
	
	        if (!StringUtils.hasText(content)
	                || !StringUtils.hasText(role)) {
	            continue;
	        }
	
	        switch (role.toUpperCase(Locale.ROOT)) {
	
	            case "USER" -> promptMessages.add(
	                    new UserMessage(content)
	            );
	
	            case "ASSISTANT" -> promptMessages.add(
	                    new AssistantMessage(content)
	            );
	
	            default -> log.debug(
	                    "Ignoring unsupported conversation history role: {}",
	                    role
	            );
	        }
	    }
	
	    if (promptMessages.size() == 1) {
	        promptMessages.add(
	                new UserMessage(state.text("currentMessage"))
	        );
	    }
	
	    // ----------------------------------------------
	    // Call AI provider
	    // ----------------------------------------------
	
	    log.info(
	            "ERP AI response generation started. Intent: {}",
	            intent
	    );
	
	    var chatResponse = chatModel.call(
	            new Prompt(promptMessages)
	    );
	
	    if (chatResponse == null
	            || chatResponse.getResult() == null
	            || chatResponse.getResult().getOutput() == null) {
	
	        throw new AiProviderResponseException(
	                "AI provider returned an empty response. Please try again."
	        );
	    }
	
	    String response = chatResponse
	            .getResult()
	            .getOutput()
	            .getText();
	
	    if (!StringUtils.hasText(response)) {
	
	        throw new AiProviderResponseException(
	                "AI provider returned an empty response. Please try again."
	        );
	    }
	
	    return Map.of(
	            "finalResponse",
	            response.trim()
	    );
	}
	

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
	        return "Multiple products matched your search. "
	                + "Please provide an exact product code or part number.";
	    }
	
	    if (results.isEmpty()) {
	        return "No matching product was found.";
	    }
	
	    if (!(results.get(0) instanceof Map<?, ?> product)) {
	        return "Unable to format the product stock information.";
	    }
	
	    String productCode = value(product, "product_code");
	    String partNo = value(product, "part_no");
	    String productName = value(product, "name");
	    String quantity = value(product, "qty");
	    String minimumStock = value(product, "minimum_stock");
	    String unit = value(product, "unit");
	
	    return """
	            Current stock information:
	
	            Product: %s
	            Product code: %s
	            Part number: %s
	            Available quantity: %s %s
	            Minimum stock level: %s %s
	            """.formatted(
	                    productName,
	                    productCode,
	                    partNo,
	                    quantity,
	                    unit,
	                    minimumStock,
	                    unit
	            ).trim();
	}
	
	private String formatMovementHistoryResponse(List<?> results) {
	
	    StringBuilder response = new StringBuilder();
	
	    response.append("Inventory movement history:");
	
	    int recordNumber = 0;
	
	    for (Object item : results) {
	
	        if (!(item instanceof Map<?, ?> movement)) {
	            continue;
	        }
	
	        recordNumber++;
	
	        response.append("\n\n")
	                .append(recordNumber)
	                .append(". ")
	                .append(value(movement, "movement_type"));
	
	        response.append("\nProduct code: ")
	                .append(value(movement, "product_code"));
	
	        response.append("\nProduct name: ")
	                .append(value(movement, "product_name"));
	
	        response.append("\nQuantity change: ")
	                .append(value(movement, "quantity_change"));
	
	        response.append("\nQuantity before: ")
	                .append(value(movement, "quantity_before"));
	
	        response.append("\nQuantity after: ")
	                .append(value(movement, "quantity_after"));
	
	        response.append("\nReference type: ")
	                .append(value(movement, "reference_type"));
	
	        response.append("\nDate: ")
	                .append(value(movement, "created_at"));
	
	        String reason = value(movement, "adjustment_reason");
	
	        if (!"Not available".equals(reason)) {
	            response.append("\nAdjustment reason: ")
	                    .append(reason);
	        }
	
	        String notes = value(movement, "adjustment_notes");
	
	        if (!"Not available".equals(notes)) {
	            response.append("\nAdjustment notes: ")
	                    .append(notes);
	        }
	    }
	
	    if (recordNumber == 0) {
	        return "No valid inventory movement records were found.";
	    }
	
	    return response.toString();
	}
	
	private String value(Map<?, ?> data, String key) {
	
	    Object result = data.get(key);
	
	    return result == null
	            ? "Not available"
	            : String.valueOf(result);
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
                - Product searches and multi-product comparisons
                - Product details
                - Current stock quantities
                - Product counts
                - Low-stock information
                - Inventory movement history
                - Inventory movement summaries
                - Date-range inventory movements
                - Recent inventory movements
                - Inventory adjustment history
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
                        : "Please provide the product code or part number so I can retrieve the correct product information."
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

            response =
                    "I couldn't generate a response. Please try again.";
        }

        return Map.of(
                "finalResponse",
                response.trim()
        );
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
                 "INVENTORY_ADJUSTMENTS" -> "TOOL";
	
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

            var resource =
                    new org.springframework.core.io.ClassPathResource(
                            "prompts/erp-system-prompt.st"
                    );

            try (var inputStream = resource.getInputStream()) {

                return new String(
                        inputStream.readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8
                );
            }

        } catch (Exception exception) {

            throw new IllegalStateException(
                    "Unable to load ERP AI system prompt",
                    exception
            );
        }
    }
}
