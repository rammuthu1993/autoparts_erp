package com.autoparts.erp.ai.service;

import org.bsc.async.AsyncGenerator;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.RunnableConfig;

/**
 * Execution abstraction over the LangGraph4j compiled graph.
 *
 * <p>Decouples graph stream execution from the concrete final {@link CompiledGraph} class,
 * providing clean mockability in concurrency tests across all JVMs and test engines
 * without requiring runtime bytecode manipulation of final classes.</p>
 */
@FunctionalInterface
public interface ErpAiGraphWorkflow {

    AsyncGenerator.Cancellable<NodeOutput<ErpAiGraphState>> stream(
            GraphInput input,
            RunnableConfig config
    );

    static ErpAiGraphWorkflow from(CompiledGraph<ErpAiGraphState> compiledGraph) {
        if (compiledGraph == null) {
            return null;
        }
        return compiledGraph::stream;
    }
}
