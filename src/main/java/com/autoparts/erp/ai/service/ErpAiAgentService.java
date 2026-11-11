package com.autoparts.erp.ai.service;

import java.util.Map;

import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.spring.ai.agentexecutor.AgentExecutor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import com.autoparts.erp.ai.tools.ProductLookupTool;
import org.bsc.langgraph4j.GraphStateException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;
import java.util.ArrayList;
import java.util.List;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.RunnableConfig;

import org.springframework.ai.chat.messages.Message;

@Service
public class ErpAiAgentService {

    private final CompiledGraph<AgentExecutor.State> workflow;

    public ErpAiAgentService(
            ChatModel chatModel,
            ProductLookupTool productLookupTool) {

        try {
        	var graph = AgentExecutor.builder()
        	        .chatModel(chatModel)
        	        .defaultSystem(loadSystemPrompt())
        	        .toolsFromObject(productLookupTool)
        	        .build();

            this.workflow = graph.compile();

        } catch (GraphStateException exception) {
            throw new IllegalStateException(
                    "Failed to initialize the ERP AI agent graph",
                    exception
            );
        }
    }


	public String chat(List<Message> messages) {
	
	    if (messages == null || messages.isEmpty()) {
	        throw new IllegalArgumentException(
	                "Conversation messages cannot be empty"
	        );
	    }
	
	    var config = RunnableConfig.builder()
	            .threadId("thread-1")
	            .build();

	    var result = workflow.stream(
	            GraphInput.args(Map.of("messages", messages)),
	            config
	    );

	
	    var state = result.stream()
	            .reduce((first, second) -> second)
	            .map(NodeOutput::state)
	            .orElseThrow(() ->
	                    new IllegalStateException(
	                            "Agent returned no result"
	                    )
	            );

    return state.lastMessage()
            .map(AssistantMessage.class::cast)
            .map(AssistantMessage::getText)
            .orElseThrow(() ->
                    new IllegalStateException(
                            "Agent returned no assistant response"
                    )
            );
}

    
    private String loadSystemPrompt() {

        try {
            ClassPathResource resource =
                    new ClassPathResource("prompts/erp-system-prompt.st");

            try (var inputStream = resource.getInputStream()) {
                return StreamUtils.copyToString(
                        inputStream,
                        StandardCharsets.UTF_8
                );
            }

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to load ERP AI system prompt.",
                    exception
            );
        }
    }
}