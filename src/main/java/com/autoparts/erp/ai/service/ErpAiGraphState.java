package com.autoparts.erp.ai.service;

import java.util.List;
import java.util.Map;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

/**
 * Typed LangGraph4j state for Auto Parts ERP AI assistant.
 *
 * Defines explicit channels for all conversation and node exchange channels.
 * Note: Under DEC-004, ErpAiExecutionContext is propagated securely via RunnableConfig metadata,
 * NEVER via mutable graph state or client-facing channels.
 */
public class ErpAiGraphState extends AgentState {

    public static final Map<String, Channel<?>> SCHEMA = Map.ofEntries(
            Map.entry("conversationId", Channels.<String>base((o, n) -> n)),
            Map.entry("currentMessage", Channels.<String>base((o, n) -> n)),
            Map.entry("history", Channels.<List<Map<String, String>>>base((o, n) -> n)),
            Map.entry("intent", Channels.<String>base((o, n) -> n)),
            Map.entry("searchText", Channels.<String>base((o, n) -> n)),
            Map.entry("startDate", Channels.<String>base((o, n) -> n)),
            Map.entry("endDate", Channels.<String>base((o, n) -> n)),
            Map.entry("limit", Channels.<Integer>base((o, n) -> n)),
            Map.entry("clarificationMessage", Channels.<String>base((o, n) -> n)),
            Map.entry("productIdentifiers", Channels.<List<String>>base((o, n) -> n)),
            Map.entry("toolResult", Channels.<Object>base((o, n) -> n)),
            Map.entry("toolError", Channels.<String>base((o, n) -> n)),
            Map.entry("finalResponse", Channels.<String>base((o, n) -> n)),
            Map.entry("validationError", Channels.<String>base((o, n) -> n)),
            Map.entry("confidence", Channels.<Double>base((o, n) -> n)),
            Map.entry("source", Channels.<String>base((o, n) -> n)),
            Map.entry("isMultiIntent", Channels.<Boolean>base((o, n) -> n)),
            Map.entry("secondaryIntent", Channels.<String>base((o, n) -> n)),
            Map.entry("pendingSlotName", Channels.<String>base((o, n) -> n)),
            Map.entry("retrievedChunks", Channels.<List<com.autoparts.erp.ai.rag.model.RetrievedChunk>>base((o, n) -> n)),
            Map.entry("hybridOutcome", Channels.<String>base((o, n) -> n))
    );

    public ErpAiGraphState(Map<String, Object> initData) {
        super(initData);
    }

    public String text(String key) {
        return value(key)
                .map(String::valueOf)
                .orElse("");
    }

    public Object object(String key) {
        return value(key).orElse(null);
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, String>> history() {
        Object history = object("history");

        if (history instanceof List<?> list) {
            return (List<Map<String, String>>) list;
        }

        return List.of();
    }

    public String finalResponse() {
        return text("finalResponse");
    }

    public String hybridOutcome() {
        return text("hybridOutcome");
    }

    public String conversationId() {
        return text("conversationId");
    }

    public String currentMessage() {
        return text("currentMessage");
    }

    public String intent() {
        return text("intent");
    }

    public String searchText() {
        return text("searchText");
    }

    public String startDate() {
        return text("startDate");
    }

    public String endDate() {
        return text("endDate");
    }

    public String toolError() {
        return text("toolError");
    }

    public Object toolResult() {
        return object("toolResult");
    }

    public Double confidence() {
        return value("confidence").map(Double.class::cast).orElse(1.0);
    }

    public String source() {
        return text("source");
    }

    public boolean isMultiIntent() {
        return value("isMultiIntent").map(Boolean.class::cast).orElse(false);
    }

    public String secondaryIntent() {
        return text("secondaryIntent");
    }

    public String pendingSlotName() {
        return text("pendingSlotName");
    }

    @SuppressWarnings("unchecked")
    public List<com.autoparts.erp.ai.rag.model.RetrievedChunk> retrievedChunks() {
        Object chunks = object("retrievedChunks");
        if (chunks instanceof List<?> list) {
            return (List<com.autoparts.erp.ai.rag.model.RetrievedChunk>) list;
        }
        return List.of();
    }
}
