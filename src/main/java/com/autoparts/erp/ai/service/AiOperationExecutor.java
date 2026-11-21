package com.autoparts.erp.ai.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.autoparts.erp.ai.model.AiIntentPlan;
import com.autoparts.erp.ai.model.AiOperationResult;
import com.autoparts.erp.ai.security.ErpAiExecutionContext;

/**
 * Dispatches validated intent plans to the appropriate ERP tool execution handlers.
 */
@Service
public class AiOperationExecutor {

    private final AiToolExecutionService toolExecutionService;

    @Autowired
    public AiOperationExecutor(AiToolExecutionService toolExecutionService) {
        this.toolExecutionService = toolExecutionService;
    }

    public AiOperationResult execute(AiIntentPlan plan, ErpAiExecutionContext context) {
        return toolExecutionService.executeTool(plan, context);
    }
}
