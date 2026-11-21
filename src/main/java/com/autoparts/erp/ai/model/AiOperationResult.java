package com.autoparts.erp.ai.model;

import java.util.List;
import java.util.Map;

/**
 * Result of an executed ERP operation.
 */
public record AiOperationResult(
        AiOperation operation,
        boolean success,
        Object data,
        String errorMessage,
        List<AiEntityReference> entities,
        Map<String, Object> metadata,
        boolean accessDenied
) {
    public static AiOperationResult success(AiOperation op, Object data) {
        return new AiOperationResult(op, true, data, null, List.of(), Map.of(), false);
    }

    public static AiOperationResult success(AiOperation op, Object data, List<AiEntityReference> entities) {
        return new AiOperationResult(op, true, data, null, entities != null ? entities : List.of(), Map.of(), false);
    }

    public static AiOperationResult success(AiOperation op, Object data, List<AiEntityReference> entities, Map<String, Object> metadata) {
        return new AiOperationResult(op, true, data, null, entities != null ? entities : List.of(), metadata != null ? metadata : Map.of(), false);
    }

    public static AiOperationResult failure(AiOperation op, String error) {
        return new AiOperationResult(op, false, null, error, List.of(), Map.of(), false);
    }

    public static AiOperationResult accessDenied(AiOperation op, String message) {
        return new AiOperationResult(op, false, null, message, List.of(), Map.of(), true);
    }
}
