package com.autoparts.erp.ai.model;

/**
 * Details a missing required parameter for an ERP operation.
 */
public record AiMissingParameter(
        String parameterName,
        String description,
        String promptMessage
) {}
