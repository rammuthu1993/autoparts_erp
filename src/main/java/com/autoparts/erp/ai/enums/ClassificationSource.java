package com.autoparts.erp.ai.enums;

/**
 * Indicates the originating mechanism of an ERP AI intent classification.
 */
public enum ClassificationSource {
    /**
     * Classified via deterministic fast-path regex and keyword pattern matching.
     */
    DETERMINISTIC,

    /**
     * Classified via structured LLM fallback when deterministic matching was uncertain.
     */
    LLM_FALLBACK,

    /**
     * Resolved from prior multi-turn conversation context or pending clarification slot.
     */
    CONTEXT_RESOLVED
}
