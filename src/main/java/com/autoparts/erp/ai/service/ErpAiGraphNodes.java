
package com.autoparts.erp.ai.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

import com.autoparts.erp.ai.tools.ProductLookupTool;

@Component
public class ErpAiGraphNodes {

    private static final Logger log =
            LoggerFactory.getLogger(ErpAiGraphNodes.class);

    private static final Pattern PRODUCT_IDENTIFIER =
            Pattern.compile(
                    "\\b[A-Z0-9]+(?:-[A-Z0-9]+)+\\b",
                    Pattern.CASE_INSENSITIVE
            );

    private final ChatModel chatModel;
    private final ProductLookupTool productLookupTool;
    private final String systemPrompt;

    public ErpAiGraphNodes(
            ChatModel chatModel,
            ProductLookupTool productLookupTool
    ) {
        this.chatModel = chatModel;
        this.productLookupTool = productLookupTool;
        this.systemPrompt = loadSystemPrompt();
    }

    // --------------------------------------------------
    // 1. INTENT CLASSIFICATION NODE
    // --------------------------------------------------

    public Map<String, Object> classifyIntent(
            ErpAiGraphState state,
            RunnableConfig config
    ) {
        String message = state.text("currentMessage")
                .trim();

        String normalized = message.toLowerCase(Locale.ROOT);

        String intent;
        String searchText = extractSearchText(message);

        if (isUnsupportedRequest(normalized)) {
            intent = "UNSUPPORTED";

        } else if (containsAny(
                normalized,
                "low stock",
                "below minimum",
                "minimum stock",
                "need restocking",
                "need to restock",
                "restock products"
        )) {
            intent = "LOW_STOCK_PRODUCTS";

        } else if (containsAny(
                normalized,
                "how many products",
                "total products",
                "product count",
                "number of products",
                "count products"
        )) {
            intent = "COUNT_PRODUCTS";

        } else if (containsAny(
                normalized,
                "stock",
                "available quantity",
                "quantity available",
                "how much quantity"
        )) {
            intent = StringUtils.hasText(searchText)
                    ? "PRODUCT_STOCK"
                    : "CLARIFICATION";

        } else if (containsAny(
                normalized,
                "product details",
                "details of",
                "information about",
                "price of",
                "description of"
        )) {
            intent = StringUtils.hasText(searchText)
                    ? "PRODUCT_DETAILS"
                    : "CLARIFICATION";

        } else if (containsAny(
                normalized,
                "find product",
                "search product",
                "lookup product",
                "look up product",
                "show product",
                "find ",
                "search "
        )) {
            intent = StringUtils.hasText(searchText)
                    ? "PRODUCT_SEARCH"
                    : "CLARIFICATION";

        } else {
            intent = "GENERAL";
        }

        log.info(
                "ERP AI intent classified. Intent: {}, Conversation: {}",
                intent,
                state.text("conversationId")
        );

        return Map.of(
                "intent", intent,
                "searchText", searchText
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

                case "PRODUCT_DETAILS" ->
                        productLookupTool.getProductDetails(searchText);

                case "PRODUCT_STOCK" ->
                        productLookupTool.getProductStock(searchText);

                case "COUNT_PRODUCTS" ->
                        productLookupTool.countProducts();

                case "LOW_STOCK_PRODUCTS" ->
                        productLookupTool.getLowStockProducts();

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
                    "toolResult",
                    "",
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

        if (toolResult instanceof List<?> results
                && results.isEmpty()) {
            return Map.of(
                    "finalResponse",
                    "I couldn't find any matching products in the ERP system."
            );
        }

        String intent = state.text("intent");

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

                case "USER" ->
                        promptMessages.add(new UserMessage(content));

                case "ASSISTANT" ->
                        promptMessages.add(new AssistantMessage(content));

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
            throw new IllegalStateException(
                    "Sarvam AI returned an empty response"
            );
        }

        String response = chatResponse
                .getResult()
                .getOutput()
                .getText();

        if (!StringUtils.hasText(response)) {
            throw new IllegalStateException(
                    "Sarvam AI returned an empty response"
            );
        }

        return Map.of(
                "finalResponse",
                response.trim()
        );
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

                Currently, I can help with product searches, product details, stock quantities, product counts and low-stock information.
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
                "Please provide the product code or part number so I can retrieve the correct product information."
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

        return switch (state.text("intent")) {

            case "PRODUCT_SEARCH",
                 "PRODUCT_DETAILS",
                 "PRODUCT_STOCK",
                 "COUNT_PRODUCTS",
                 "LOW_STOCK_PRODUCTS" -> "TOOL";

            case "UNSUPPORTED" -> "UNSUPPORTED";

            case "CLARIFICATION" -> "CLARIFICATION";

            default -> "GENERAL";
        };
    }

    private boolean isUnsupportedRequest(String message) {

        if (message.contains("supplier")) {
            return true;
        }

        return containsAny(
                message,
                "create purchase",
                "create sales",
                "create invoice",
                "generate invoice",
                "make purchase",
                "make sales",
                "record purchase",
                "record sales"
        );
    }

    private String extractSearchText(String message) {

        Matcher matcher = PRODUCT_IDENTIFIER.matcher(message);

        if (matcher.find()) {
            return matcher.group().trim();
        }

        String cleaned = message.trim();

        cleaned = cleaned.replaceFirst(
                "(?i)^(please\\s+)?(find|search|lookup|look up|show|get|check)\\s+",
                ""
        );

        cleaned = cleaned.replaceFirst(
                "(?i)^for\\s+",
                ""
        );

        cleaned = cleaned.replaceFirst(
                "(?i)^the\\s+",
                ""
        );

        cleaned = cleaned.replaceFirst(
                "(?i)^product\\s+",
                ""
        );

        return cleaned.trim();
    }

    private boolean containsAny(
            String message,
            String... values
    ) {
        for (String value : values) {
            if (message.contains(value)) {
                return true;
            }
        }

        return false;
    }

    private String loadSystemPrompt() {

        try {
            var resource = new org.springframework.core.io.ClassPathResource(
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
