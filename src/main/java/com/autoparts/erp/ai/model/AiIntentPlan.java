package com.autoparts.erp.ai.model;

import java.util.List;
import java.util.Map;

import com.autoparts.erp.ai.enums.ClassificationSource;

/**
 * Validated, typed execution plan describing the operation to be performed.
 */
public record AiIntentPlan(
        AiDomain domain,
        AiOperation operation,
        String originalMessage,
        String normalizedQuery,
        List<AiEntityReference> entityReferences,
        String productCode,
        String partNumber,
        String customerIdentifier,
        String supplierIdentifier,
        List<String> requestedFields,
        Map<String, Object> filters,
        String startDate,
        String endDate,
        Integer limit,
        List<AiMissingParameter> missingParameters,
        boolean followUp,
        ClassificationSource source,
        double confidence,
        boolean isMultiIntent,
        String secondaryIntent,
        String clarificationMessage
) {
    public boolean isValid() {
        return missingParameters == null || missingParameters.isEmpty();
    }

    public boolean requiresClarification() {
        return operation == AiOperation.CLARIFICATION || (missingParameters != null && !missingParameters.isEmpty());
    }

    public String effectiveSearchText() {
        if (productCode != null && !productCode.isBlank()) {
            return productCode;
        }
        if (partNumber != null && !partNumber.isBlank()) {
            return partNumber;
        }
        if (customerIdentifier != null && !customerIdentifier.isBlank()) {
            return customerIdentifier;
        }
        if (supplierIdentifier != null && !supplierIdentifier.isBlank()) {
            return supplierIdentifier;
        }
        return normalizedQuery != null ? normalizedQuery : originalMessage;
    }
}
