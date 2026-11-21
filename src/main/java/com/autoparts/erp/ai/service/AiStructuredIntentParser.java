package com.autoparts.erp.ai.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.autoparts.erp.ai.enums.ClassificationSource;
import com.autoparts.erp.ai.model.AiDomain;
import com.autoparts.erp.ai.model.AiEntityReference;
import com.autoparts.erp.ai.model.AiIntentPlan;
import com.autoparts.erp.ai.model.AiOperation;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Structured LLM-assisted intent parser utilizing Spring AI ChatModel.
 *
 * <p>Invoked as a secondary semantic understanding fallback when deterministic
 * fast-path regex classification is uncertain. LLM confidence is strictly advisory
 * and all extracted fields are post-validated in Java.</p>
 */
@Service
public class AiStructuredIntentParser {

    private static final Logger log = LoggerFactory.getLogger(AiStructuredIntentParser.class);

    private static final Pattern JSON_EXTRACTOR = Pattern.compile("\\{.*\\}", Pattern.DOTALL);

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final AiEntityExtractionService entityExtractor;

    @Value("${spring.ai.openai.chat.options.model:sarvam-105b}")
    private String model = "sarvam-105b";

    @Autowired
    public AiStructuredIntentParser(
            ChatModel chatModel,
            ObjectMapper objectMapper,
            AiEntityExtractionService entityExtractor
    ) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.entityExtractor = entityExtractor != null ? entityExtractor : new AiEntityExtractionService();
    }

    public Optional<AiIntentPlan> parse(String currentMessage, List<Map<String, String>> history) {
        if (!StringUtils.hasText(currentMessage) || chatModel == null) {
            return Optional.empty();
        }

        try {
            String systemInstructions = """
                    You are an Auto Parts ERP intent classifier. Return ONLY a valid JSON object.
                    Supported operations:
                    - PRODUCT_SEARCH, PRODUCT_COUNT, PRODUCT_DETAILS, PRODUCT_STOCK, PRODUCT_AVAILABLE_QUANTITY, PRODUCT_MINIMUM_STOCK, PRODUCT_LOW_STOCK
                    - INVENTORY_LOOKUP, PRODUCT_MOVEMENT_HISTORY, PRODUCT_MOVEMENT_SUMMARY
                    - CUSTOMER_LOOKUP, SUPPLIER_LOOKUP, SALES_LOOKUP, PURCHASE_LOOKUP, FINANCIAL_LOOKUP, REPORT_LOOKUP, AUDIT_LOOKUP
                    - KNOWLEDGE_QUERY, CLARIFICATION, UNSUPPORTED, GENERAL

                    JSON schema:
                    {
                      "operation": "OPERATION_NAME",
                      "domain": "PRODUCT|INVENTORY|PURCHASE|SALE|CUSTOMER|SUPPLIER|RETURN|FINANCIAL|REPORT|AUDIT|KNOWLEDGE|GENERAL",
                      "productCode": "optional code",
                      "partNumber": "optional part",
                      "searchText": "search query or entity",
                      "confidence": 0.0 to 1.0
                    }
                    """;

            var prompt = new Prompt(
                    List.of(
                            new SystemMessage(systemInstructions),
                            new UserMessage("User query: " + currentMessage)
                    ),
                    OpenAiChatOptions.builder()
                            .model(model)
                            .maxTokens(256)
                            .build()
            );

            var response = chatModel.call(prompt);
            if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
                return Optional.empty();
            }

            String content = response.getResult().getOutput().getText();
            if (!StringUtils.hasText(content)) {
                return Optional.empty();
            }

            Matcher matcher = JSON_EXTRACTOR.matcher(content);
            if (!matcher.find()) {
                return Optional.empty();
            }

            JsonNode root = objectMapper.readTree(matcher.group());
            String opStr = root.path("operation").asText(null);
            if (opStr == null) {
                return Optional.empty();
            }

            AiOperation op;
            try {
                op = AiOperation.valueOf(opStr.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                op = AiOperation.fromIntent(opStr);
            }

            String domainStr = root.path("domain").asText("GENERAL");
            AiDomain domain;
            try {
                domain = AiDomain.valueOf(domainStr.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                domain = AiDomain.GENERAL;
            }

            String productCode = root.path("productCode").asText(null);
            String partNumber = root.path("partNumber").asText(null);
            String searchText = root.path("searchText").asText(currentMessage);
            double confidence = root.path("confidence").asDouble(0.85);
            if (Double.isNaN(confidence) || Double.isInfinite(confidence)) {
                confidence = 0.5;
            }

            List<AiEntityReference> entities = new ArrayList<>();
            if (StringUtils.hasText(productCode)) {
                entities.add(AiEntityReference.ofProduct(productCode, null, partNumber));
            } else {
                entities.addAll(entityExtractor.extractEntities(currentMessage));
            }

            return Optional.of(new AiIntentPlan(
                    domain,
                    op,
                    currentMessage,
                    searchText,
                    entities,
                    productCode,
                    partNumber,
                    null,
                    null,
                    List.of(),
                    Map.of(),
                    null,
                    null,
                    null,
                    List.of(),
                    false,
                    ClassificationSource.LLM_FALLBACK,
                    confidence,
                    false,
                    null,
                    null
            ));

        } catch (Exception ex) {
            log.warn("Structured intent parsing failed safely: {}", ex.getMessage());
            return Optional.empty();
        }
    }
}
