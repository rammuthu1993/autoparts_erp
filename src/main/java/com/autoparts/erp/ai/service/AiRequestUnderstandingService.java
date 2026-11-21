package com.autoparts.erp.ai.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.autoparts.erp.ai.enums.ClassificationSource;
import com.autoparts.erp.ai.model.AiDomain;
import com.autoparts.erp.ai.model.AiEntityReference;
import com.autoparts.erp.ai.model.AiIntentPlan;
import com.autoparts.erp.ai.model.AiOperation;
import com.autoparts.erp.ai.security.ErpAiExecutionContext;

/**
 * Coordinates hybrid request understanding across deterministic rules,
 * conversation context resolution, pending clarifications, and Spring AI structured parsing.
 */
@Service
public class AiRequestUnderstandingService {

    private static final Logger log = LoggerFactory.getLogger(AiRequestUnderstandingService.class);

    private static final Pattern MUTATION_PATTERN = Pattern.compile(
            "\\b(?:create|make|record|add|update|delete|generate|register|process|open|close|post)\\s+"
                    + "(?:a\\s+)?(?:new\\s+)?(?:purchase|sale|sales|invoice|customer|supplier|vendor|return|refund|credit|expense|register|session|closing|product|stock)\\b",
            Pattern.CASE_INSENSITIVE);

    private final ErpAiIntentClassifier deterministicClassifier;
    private final AiConversationContextService contextService;
    private final ErpAiPendingClarificationStore clarificationStore;
    private final AiStructuredIntentParser structuredParser;
    private final AiEntityExtractionService entityExtractor;
    private final AiIntentPlanValidator validator;

    @Autowired
    public AiRequestUnderstandingService(
            ErpAiIntentClassifier deterministicClassifier,
            AiConversationContextService contextService,
            ErpAiPendingClarificationStore clarificationStore,
            AiStructuredIntentParser structuredParser,
            AiEntityExtractionService entityExtractor,
            AiIntentPlanValidator validator
    ) {
        this.deterministicClassifier = deterministicClassifier != null ? deterministicClassifier : new ErpAiIntentClassifier();
        this.contextService = contextService != null ? contextService : new AiConversationContextService();
        this.clarificationStore = clarificationStore != null ? clarificationStore : new ErpAiPendingClarificationStore();
        this.structuredParser = structuredParser;
        this.entityExtractor = entityExtractor != null ? entityExtractor : new AiEntityExtractionService();
        this.validator = validator != null ? validator : new AiIntentPlanValidator();
    }

    public AiIntentPlan understand(
            String currentMessage,
            List<Map<String, String>> history,
            UUID conversationId,
            ErpAiExecutionContext executionContext
    ) {
        if (!StringUtils.hasText(currentMessage)) {
            return planForClarification("Please tell me what ERP information you need.", currentMessage);
        }

        String userSubject = executionContext != null ? executionContext.userSubject() : null;

        // Gate 0: Prohibited mutation check
        if (MUTATION_PATTERN.matcher(currentMessage).find()) {
            log.info("Prohibited mutation operation detected. Rejecting as UNSUPPORTED.");
            return planForUnsupported(currentMessage);
        }

        // Gate 1: Pending clarification resolution
        if (conversationId != null && userSubject != null) {
            Optional<ErpAiPendingClarificationStore.PendingSlot> pendingOpt =
                    clarificationStore.findAndValidate(conversationId, userSubject);
            if (pendingOpt.isPresent()) {
                ErpAiPendingClarificationStore.PendingSlot pending = pendingOpt.get();
                clarificationStore.remove(conversationId, userSubject);
                String resolvedQuery = currentMessage.trim() + " (" + pending.pendingSlotName() + ")";
                ErpAiIntentResult result = deterministicClassifier.classify(resolvedQuery);
                return validator.validate(toPlan(result, currentMessage, ClassificationSource.CONTEXT_RESOLVED, true));
            }
        }

        // Gate 2: Context resolution (resolves follow-ups, anaphora, "what is minimum stock", etc.)
        AiConversationContextService.ResolvedContext contextResolution =
                contextService.resolveContext(currentMessage, history);

        if (contextResolution.requiresClarification()) {
            return planForClarification(contextResolution.clarificationMessage(), currentMessage);
        }

        String effectiveMessage = contextResolution.resolved()
                ? contextResolution.resolvedMessage()
                : currentMessage;
        boolean isFollowUp = contextResolution.resolved();

        // Gate 3: Deterministic classification
        ErpAiIntentResult deterministic = deterministicClassifier.classify(effectiveMessage);

        ClassificationSource source = isFollowUp ? ClassificationSource.CONTEXT_RESOLVED : ClassificationSource.DETERMINISTIC;
        AiIntentPlan plan = toPlan(deterministic, currentMessage, source, isFollowUp);

        // Populate entity references from context resolution if available
        if (contextResolution.entityReference() != null) {
            List<AiEntityReference> entities = new ArrayList<>(plan.entityReferences());
            if (!entities.contains(contextResolution.entityReference())) {
                entities.add(0, contextResolution.entityReference());
            }
            plan = new AiIntentPlan(
                    plan.domain(), plan.operation(), plan.originalMessage(), plan.normalizedQuery(),
                    entities, contextResolution.entityReference().identifier(),
                    contextResolution.entityReference().partNumber(),
                    plan.customerIdentifier(), plan.supplierIdentifier(),
                    plan.requestedFields(), plan.filters(), plan.startDate(), plan.endDate(),
                    plan.limit(), plan.missingParameters(), plan.followUp(), plan.source(),
                    plan.confidence(), plan.isMultiIntent(), plan.secondaryIntent(),
                    plan.clarificationMessage()
            );
        }

        // Gate 4: If deterministic classification is GENERAL or uncertain, attempt structured LLM fallback
        if (plan.operation() == AiOperation.GENERAL && structuredParser != null) {
            Optional<AiIntentPlan> parsed = structuredParser.parse(currentMessage, history);
            if (parsed.isPresent()) {
                plan = parsed.get();
            }
        }

        // Gate 5: Validate intent plan
        return validator.validate(plan);
    }

    private AiIntentPlan toPlan(
            ErpAiIntentResult result,
            String originalMessage,
            ClassificationSource source,
            boolean followUp
    ) {
        AiOperation operation = AiOperation.fromIntent(result.intent());
        AiDomain domain = mapDomain(operation);
        List<AiEntityReference> entities = new ArrayList<>();
        String productCode = null;
        String partNumber = null;

        if (result.productIdentifiers() != null && !result.productIdentifiers().isEmpty()) {
            for (String pid : result.productIdentifiers()) {
                entities.add(AiEntityReference.ofProduct(pid));
            }
            productCode = result.productIdentifiers().get(0);
        } else if (StringUtils.hasText(result.searchText()) && isProductOperation(operation)) {
            productCode = result.searchText();
            entities.add(AiEntityReference.ofProduct(productCode));
        }

        List<String> requestedFields = new ArrayList<>();
        if (operation == AiOperation.PRODUCT_MINIMUM_STOCK) {
            requestedFields.add("minimum_stock");
        } else if (operation == AiOperation.PRODUCT_AVAILABLE_QUANTITY) {
            requestedFields.add("qty");
        }

        return new AiIntentPlan(
                domain,
                operation,
                originalMessage,
                result.searchText(),
                entities,
                productCode,
                partNumber,
                null,
                null,
                requestedFields,
                Map.of(),
                result.startDate(),
                result.endDate(),
                result.limit(),
                List.of(),
                followUp,
                source,
                result.confidence(),
                result.isMultiIntent(),
                result.secondaryIntent(),
                result.clarificationMessage()
        );
    }

    private boolean isProductOperation(AiOperation op) {
        return op == AiOperation.PRODUCT_STOCK
                || op == AiOperation.PRODUCT_MINIMUM_STOCK
                || op == AiOperation.PRODUCT_AVAILABLE_QUANTITY
                || op == AiOperation.PRODUCT_DETAILS
                || op == AiOperation.PRODUCT_MOVEMENT_HISTORY
                || op == AiOperation.PRODUCT_MOVEMENT_SUMMARY;
    }

    private AiDomain mapDomain(AiOperation op) {
        if (op == null) return AiDomain.GENERAL;
        return switch (op) {
            case PRODUCT_SEARCH, PRODUCT_COUNT, PRODUCT_DETAILS, PRODUCT_STOCK,
                    PRODUCT_AVAILABLE_QUANTITY, PRODUCT_MINIMUM_STOCK, PRODUCT_LOW_STOCK,
                    PRODUCT_COMPARISON -> AiDomain.PRODUCT;
            case INVENTORY_LOOKUP, PRODUCT_MOVEMENT_HISTORY, PRODUCT_MOVEMENT_SUMMARY,
                    PRODUCT_MOVEMENTS_BY_DATE_RANGE, RECENT_INVENTORY_MOVEMENTS,
                    INVENTORY_ADJUSTMENTS -> AiDomain.INVENTORY;
            case CUSTOMER_LOOKUP, CUSTOMER_SEARCH, CUSTOMER_DETAILS,
                    CUSTOMER_SALES_HISTORY, CUSTOMER_OUTSTANDING -> AiDomain.CUSTOMER;
            case SUPPLIER_LOOKUP, SUPPLIER_SEARCH, SUPPLIER_DETAILS,
                    SUPPLIER_PURCHASE_HISTORY, SUPPLIER_SUMMARY -> AiDomain.SUPPLIER;
            case SALES_LOOKUP, SALES_SEARCH, SALE_DETAILS, SALES_DATE_RANGE,
                    SALES_BY_CUSTOMER, SALES_SUMMARY, SALE_STATUS -> AiDomain.SALE;
            case PURCHASE_LOOKUP, PURCHASE_SEARCH, PURCHASE_DETAILS, PURCHASE_DATE_RANGE,
                    PURCHASES_BY_SUPPLIER, PURCHASE_SUMMARY, PURCHASE_STATUS -> AiDomain.PURCHASE;
            case RETURN_SUMMARY, SALES_RETURN_SEARCH, SALES_RETURN_DETAILS,
                    PURCHASE_RETURN_SEARCH, PURCHASE_RETURN_DETAILS -> AiDomain.RETURN;
            case FINANCIAL_LOOKUP, CASH_REGISTER_STATUS, OPEN_CASH_REGISTERS,
                    EXPENSE_SUMMARY, EXPENSE_BY_CATEGORY, EXPENSES_BY_CATEGORY,
                    DAILY_CLOSING, CASH_DISCREPANCIES -> AiDomain.FINANCIAL;
            case REPORT_LOOKUP, KPI_SUMMARY, INVENTORY_ABC_XYZ, INVENTORY_ABC_ANALYSIS,
                    CUSTOMER_RFM, CUSTOMER_RFM_ANALYSIS, RECONCILIATION_DISCREPANCIES,
                    COGS_PROFIT_EXPLANATION -> AiDomain.REPORT;
            case AUDIT_LOOKUP, AUDIT_LOG_SEARCH, AI_TOOL_RUNS_LOG -> AiDomain.AUDIT;
            case KNOWLEDGE_QUERY -> AiDomain.KNOWLEDGE;
            case CLARIFICATION, UNSUPPORTED -> AiDomain.SYSTEM;
            default -> AiDomain.GENERAL;
        };
    }

    private AiIntentPlan planForClarification(String message, String original) {
        return new AiIntentPlan(
                AiDomain.SYSTEM,
                AiOperation.CLARIFICATION,
                original,
                "",
                List.of(),
                null, null, null, null,
                List.of(), Map.of(), null, null, null,
                List.of(), false, ClassificationSource.DETERMINISTIC, 1.0,
                false, null, message
        );
    }

    private AiIntentPlan planForUnsupported(String original) {
        return new AiIntentPlan(
                AiDomain.SYSTEM,
                AiOperation.UNSUPPORTED,
                original,
                "",
                List.of(),
                null, null, null, null,
                List.of(), Map.of(), null, null, null,
                List.of(), false, ClassificationSource.DETERMINISTIC, 1.0,
                false, null, null
        );
    }
}
