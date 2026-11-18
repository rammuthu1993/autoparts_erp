package com.autoparts.erp.ai.service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

import com.autoparts.erp.ai.enums.ClassificationSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Structured LLM-assisted intent classifier for Auto Parts ERP AI assistant.
 *
 * <p><strong>Design Principles:</strong>
 * <ul>
 *   <li>Invoked as a secondary fallback when deterministic regex classification is uncertain.</li>
 *   <li>The allowed intent list is dynamically derived from {@link ErpAiIntentResult#SUPPORTED_INTENTS}.</li>
 *   <li>LLM confidence score is strictly <em>advisory</em>; all outputs are post-validated in Java.</li>
 *   <li>Non-finite confidence scores (NaN, Infinity) are strictly rejected.</li>
 *   <li>Fail-safe: On any model failure, provider timeout, or malformed JSON, returns {@link Optional#empty()}
 *       so the coordinator router can fail-closed safely.</li>
 * </ul>
 * </p>
 */
@Component
public class ErpAiLlmClassifier {

    private static final Logger log =
            LoggerFactory.getLogger(ErpAiLlmClassifier.class);

    private static final Pattern JSON_EXTRACTOR =
            Pattern.compile("\\{.*\\}", Pattern.DOTALL);

    private static final Pattern MUTATION_VERBS = Pattern.compile(
            "\\b(?:create|make|record|add|update|delete|generate|register|process|open|close|post)\\s+"
                    + "(?:a\\s+)?(?:new\\s+)?(?:purchase|sale|sales|invoice|customer|supplier|vendor|return|refund|credit|expense|register|session|closing)\\b",
            Pattern.CASE_INSENSITIVE);

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final String classifierPrompt;

    @Autowired
    public ErpAiLlmClassifier(ChatModel chatModel, ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.classifierPrompt = buildClassifierPrompt();
    }

    public Optional<ErpAiIntentResult> classify(
            String currentMessage,
            List<Map<String, String>> history
    ) {
        if (!StringUtils.hasText(currentMessage) || chatModel == null) {
            return Optional.empty();
        }

        try {
            List<Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(classifierPrompt));

            if (history != null) {
                for (Map<String, String> item : history) {
                    String role = item.get("role");
                    String content = item.get("content");
                    if (StringUtils.hasText(content) && StringUtils.hasText(role)) {
                        if ("USER".equalsIgnoreCase(role)) {
                            messages.add(new UserMessage(content));
                        } else if ("ASSISTANT".equalsIgnoreCase(role)) {
                            messages.add(new AssistantMessage(content));
                        }
                    }
                }
            }

            messages.add(new UserMessage("User Message: " + currentMessage));

            var response = chatModel.call(new Prompt(messages));
            if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
                log.warn("LLM intent classifier received null response from provider.");
                return Optional.empty();
            }

            String outputText = response.getResult().getOutput().getText();
            if (!StringUtils.hasText(outputText)) {
                log.warn("LLM intent classifier received empty text from provider.");
                return Optional.empty();
            }

            return parseAndValidate(outputText);

        } catch (Exception ex) {
            log.warn("LLM intent classification failed due to provider error or timeout: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    Optional<ErpAiIntentResult> parseAndValidate(String outputText) {
        Matcher matcher = JSON_EXTRACTOR.matcher(outputText);
        if (!matcher.find()) {
            log.warn("No JSON object found in LLM classifier output: {}", outputText);
            return Optional.empty();
        }

        String json = matcher.group();
        try {
            JsonNode root = objectMapper.readTree(json);
            String rawIntent = root.path("intent").asText("");
            if (!StringUtils.hasText(rawIntent)) {
                return Optional.empty();
            }

            String intent = rawIntent.trim().toUpperCase(Locale.ROOT);
            if (!ErpAiIntentResult.isSupported(intent)) {
                log.warn("LLM suggested unsupported intent: '{}'. Rejecting.", intent);
                return Optional.empty();
            }

            // Confidence must be a JSON number. Do not coerce strings such as
            // "NaN" or "Infinity" to the default value (0.0).
            JsonNode confidenceNode = root.path("confidence");
            if (!confidenceNode.isNumber()) {
                log.warn("Missing or non-numeric confidence value received from LLM. Rejecting.");
                return Optional.empty();
            }

            double rawConfidence = confidenceNode.doubleValue();
            if (!Double.isFinite(rawConfidence)) {
                log.warn("Non-finite confidence value received from LLM: {}. Rejecting.", rawConfidence);
                return Optional.empty();
            }
            double confidence = Math.max(0.0, Math.min(1.0, rawConfidence));

            String searchText = root.path("searchText").asText("").trim();
            String startDate = validateDate(root.path("startDate").asText(""));
            String endDate = validateDate(root.path("endDate").asText(""));
            Integer limit = root.has("limit") && !root.path("limit").isNull()
                    ? Math.max(1, Math.min(50, root.path("limit").asInt(10)))
                    : null;
            String clarificationMessage = root.path("clarificationQuestion").asText("").trim();

            List<String> productIdentifiers = new ArrayList<>();
            if (root.has("productIdentifiers") && root.path("productIdentifiers").isArray()) {
                for (JsonNode item : root.path("productIdentifiers")) {
                    if (item.isTextual() && StringUtils.hasText(item.asText())) {
                        productIdentifiers.add(item.asText().trim().toUpperCase(Locale.ROOT));
                    }
                }
            }

            boolean isMultiIntent = root.path("isMultiIntent").asBoolean(false);
            String secondaryIntent = root.path("secondaryIntent").asText("").trim().toUpperCase(Locale.ROOT);
            if (!ErpAiIntentResult.isSupported(secondaryIntent)) {
                secondaryIntent = "";
                isMultiIntent = false;
            }

            // Check if secondary intent indicates a prohibited mutation
            if (isMultiIntent && (MUTATION_VERBS.matcher(secondaryIntent).find() || "UNSUPPORTED".equals(secondaryIntent))) {
                intent = "UNSUPPORTED";
            }

            // Intent-specific validation: PRODUCT_COMPARISON requires 2..5 identifiers
            if ("PRODUCT_COMPARISON".equals(intent) && (productIdentifiers.size() < 2 || productIdentifiers.size() > 5)) {
                intent = "CLARIFICATION";
                clarificationMessage = "Please provide at least two product codes or part numbers to compare.";
            }

            return Optional.of(new ErpAiIntentResult(
                    intent,
                    searchText,
                    startDate,
                    endDate,
                    limit,
                    clarificationMessage,
                    productIdentifiers,
                    confidence,
                    ClassificationSource.LLM_FALLBACK,
                    isMultiIntent,
                    secondaryIntent,
                    ""
            ));

        } catch (Exception ex) {
            log.warn("Failed to parse JSON response from LLM classifier: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private String validateDate(String rawDate) {
        if (!StringUtils.hasText(rawDate)) {
            return "";
        }
        try {
            LocalDate parsed = LocalDate.parse(rawDate.trim());
            return parsed.toString();
        } catch (DateTimeParseException ex) {
            return "";
        }
    }

    private String buildClassifierPrompt() {
        String allowedIntents = String.join(", ", ErpAiIntentResult.supportedIntents());

        return """
                You are a precise Intent Classifier for an Auto Parts ERP system.
                Classify the user's ERP query into exactly ONE valid intent from the following allowed set:

                ALLOWED INTENTS:
                %s

                RULES:
                1. If the request is asking to CREATE, ADD, UPDATE, DELETE, RECORD, or MODIFY transactions (e.g. create purchase, make sale, adjust stock), output "intent": "UNSUPPORTED".
                2. If the user is asking for a specific ERP report or transaction but a key parameter (e.g. date for daily closing) is missing or ambiguous, output "intent": "CLARIFICATION" and provide a helpful "clarificationQuestion".
                3. If the user is asking general questions, greeting, or non-ERP conversational queries, output "intent": "GENERAL".
                4. If the user asks a compound question with multiple requests, set "isMultiIntent": true, set "intent" to the primary/first request, and "secondaryIntent" to the second request.
                5. Output strictly a JSON object with no markdown wrappers or other text:
                {
                  "intent": "<ONE_OF_ALLOWED_INTENTS>",
                  "confidence": <FLOAT_BETWEEN_0.0_AND_1.0>,
                  "searchText": "<EXTRACTED_ENTITY_OR_QUERY>",
                  "startDate": "<YYYY-MM-DD_OR_EMPTY>",
                  "endDate": "<YYYY-MM-DD_OR_EMPTY>",
                  "limit": <INTEGER_OR_NULL>,
                  "productIdentifiers": ["<ID1>", "<ID2>"],
                  "clarificationQuestion": "<QUESTION_IF_NEEDED>",
                  "isMultiIntent": <TRUE_OR_FALSE>,
                  "secondaryIntent": "<SECONDARY_INTENT_OR_EMPTY>"
                }
                """.formatted(allowedIntents);
    }
}
