package com.autoparts.erp.ai.service;

import java.util.List;
import java.util.Map;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;

public class ErpAiGraphState extends AgentState {

    public static final Map<String, Channel<?>> SCHEMA = Map.of();

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
}
