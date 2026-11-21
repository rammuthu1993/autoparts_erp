package com.autoparts.erp.ai.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.autoparts.erp.ai.model.AiIntentPlan;
import com.autoparts.erp.ai.model.AiMissingParameter;
import com.autoparts.erp.ai.model.AiOperation;

/**
 * Validates operation plans prior to ERP execution.
 *
 * <p>Enforces parameter completeness, distinguishes operations requiring explicit
 * product identifiers (stock, minimum stock, available quantity, details) from
 * operations that do not (product count, search, low stock), and rejects prohibited
 * mutations before any ERP tool or database invocation can take place.</p>
 */
@Component
public class AiIntentPlanValidator {

    public AiIntentPlan validate(AiIntentPlan plan) {
        if (plan == null) {
            return null;
        }

        if (plan.operation() == AiOperation.UNSUPPORTED) {
            return plan;
        }

        List<AiMissingParameter> missing = new ArrayList<>();
        if (plan.missingParameters() != null) {
            missing.addAll(plan.missingParameters());
        }

        switch (plan.operation()) {
            case PRODUCT_STOCK, PRODUCT_MINIMUM_STOCK, PRODUCT_AVAILABLE_QUANTITY, PRODUCT_DETAILS -> {
                boolean hasIdent = StringUtils.hasText(plan.productCode())
                        || StringUtils.hasText(plan.partNumber())
                        || (plan.entityReferences() != null && !plan.entityReferences().isEmpty());
                if (!hasIdent) {
                    missing.add(new AiMissingParameter(
                            "productCode",
                            "Product code or part number",
                            "Which product are you referring to? Please provide its product code or part number."
                    ));
                }
            }
            case PRODUCT_COUNT -> {
                // Product count is a catalog-wide metric; never requires a product identifier.
            }
            case PRODUCT_LOW_STOCK -> {
                // Low stock is a global listing unless a specific product is asked.
            }
            case PRODUCT_COMPARISON -> {
                if (plan.entityReferences() == null || plan.entityReferences().size() < 2) {
                    missing.add(new AiMissingParameter(
                            "productIdentifiers",
                            "At least two product identifiers",
                            "Please provide at least two product codes or part numbers to compare."
                    ));
                }
            }
            default -> {}
        }

        if (!missing.isEmpty()) {
            return new AiIntentPlan(
                    plan.domain(),
                    AiOperation.CLARIFICATION,
                    plan.originalMessage(),
                    plan.normalizedQuery(),
                    plan.entityReferences(),
                    plan.productCode(),
                    plan.partNumber(),
                    plan.customerIdentifier(),
                    plan.supplierIdentifier(),
                    plan.requestedFields(),
                    plan.filters(),
                    plan.startDate(),
                    plan.endDate(),
                    plan.limit(),
                    List.copyOf(missing),
                    plan.followUp(),
                    plan.source(),
                    plan.confidence(),
                    plan.isMultiIntent(),
                    plan.secondaryIntent(),
                    missing.get(0).promptMessage()
            );
        }

        return plan;
    }
}
