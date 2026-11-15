
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

@Component
public class ErpAiGraphNodes {

    private static final Logger log =
            LoggerFactory.getLogger(ErpAiGraphNodes.class);

    private final ChatModel chatModel;
    private final ProductLookupTool productLookupTool;
    private final ErpAiIntentClassifier intentClassifier;
    private final String systemPrompt;

    public ErpAiGraphNodes(
            ChatModel chatModel,
            ProductLookupTool productLookupTool,
            ErpAiIntentClassifier intentClassifier
    ) {
        this.chatModel = chatModel;
        this.productLookupTool = productLookupTool;
        this.intentClassifier = intentClassifier;
        this.systemPrompt = loadSystemPrompt();
    }

    // --------------------------------------------------
    // 1. INTENT CLASSIFICATION NODE
    // --------------------------------------------------

    public Map<String, Object> classifyIntent(
            ErpAiGraphState state,
            RunnableConfig config
    ) {
        ErpAiIntentResult classification =
                intentClassifier.classify(state.text("currentMessage"));

        log.info(
                "ERP AI intent classified. Intent: {}, Conversation: {}",
                classification.intent(),
                state.text("conversationId")
        );

        return Map.of(
                "intent", classification.intent(),
                "searchText", classification.searchText()
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
        // Handle empty product list results
        // ----------------------------------------------

        if (toolResult instanceof List<?> results
                && results.isEmpty()) {

            if ("LOW_STOCK_PRODUCTS".equals(intent)) {

                return Map.of(
                        "finalResponse",
                        "There are currently no active products below their minimum stock level."
                );
            }

            return Map.of(
                    "finalResponse",
                    "I couldn't find any matching products in the ERP system."
            );
        }

        // ----------------------------------------------
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

            log.info(
                    "Active product count response generated."
            );

            return Map.of(
                    "finalResponse",
                    "There are " + total
                            + " active products in the ERP system."
            );
        }

        // ----------------------------------------------
        // Prepare verified ERP tool result
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
        // Call Sarvam AI
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

        String intent = state.text("intent");
        if (intent == null) {
            return "CLARIFICATION";
        }

        return switch (intent) {
            case "PRODUCT_SEARCH",
                 "PRODUCT_DETAILS",
                 "PRODUCT_STOCK",
                 "COUNT_PRODUCTS",
                 "LOW_STOCK_PRODUCTS" -> "TOOL";

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
