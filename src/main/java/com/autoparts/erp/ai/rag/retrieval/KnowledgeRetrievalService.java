package com.autoparts.erp.ai.rag.retrieval;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.autoparts.erp.ai.rag.model.RetrievedChunk;
import com.autoparts.erp.ai.security.ErpAiExecutionContext;
import com.autoparts.erp.ai.security.ToolAuthorizationException;

/**
 * Access-controlled retrieval service for the ERP knowledge base.
 *
 * <p>Enforces zero-trust document security by mapping immutable authenticated authorities
 * to allowed document roles and pre-filtering retrieval queries at the SQL boundary.
 * Never relies on ThreadLocal security contexts or prompt-supplied roles.</p>
 */
@Service
public class KnowledgeRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeRetrievalService.class);
    public static final int DEFAULT_TOP_K = 4;

    private final KnowledgeRetrievalEngine retrievalEngine;

    @Autowired
    public KnowledgeRetrievalService(KnowledgeRetrievalEngine retrievalEngine) {
        this.retrievalEngine = retrievalEngine;
    }

    /**
     * Executes server-side authorized knowledge retrieval.
     *
     * @param query natural language question or search text
     * @param context immutable execution context captured on the HTTP request thread
     * @return top-K authorized retrieved chunks
     */
    public List<RetrievedChunk> retrieveKnowledge(String query, ErpAiExecutionContext context) {
        return retrieveKnowledge(query, context, DEFAULT_TOP_K);
    }

    /**
     * Executes server-side authorized knowledge retrieval with a custom limit.
     *
     * @param query natural language question or search text
     * @param context immutable execution context captured on the HTTP request thread
     * @param limit maximum number of chunks to return
     * @return top-K authorized retrieved chunks
     */
    public List<RetrievedChunk> retrieveKnowledge(String query, ErpAiExecutionContext context, int limit) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }

        if (context == null || context.authorities() == null || context.authorities().isEmpty()) {
            log.warn("Knowledge retrieval rejected: missing or unauthenticated execution context (fail-closed)");
            throw new ToolAuthorizationException("Unauthenticated request: valid execution context is required for knowledge retrieval.");
        }

        Set<String> allowedRoles = resolveAllowedRoles(context.authorities());
        if (allowedRoles.isEmpty()) {
            log.warn("Knowledge retrieval rejected: no authorized document roles resolved for authorities: {}", context.authorities());
            throw new ToolAuthorizationException("Access denied: no authorized document roles granted for current user.");
        }

        log.debug("Executing knowledge retrieval for user '{}' with allowed roles: {}", context.userSubject(), allowedRoles);
        return retrievalEngine.retrieve(query.trim(), allowedRoles, Math.max(1, limit));
    }

    /**
     * Maps granted user authorities to accessible document roles.
     *
     * <p>Role hierarchy:
     * <ul>
     *   <li>{@code ROLE_ADMIN} -> ADMIN, MANAGER, CASHIER, PUBLIC</li>
     *   <li>{@code ROLE_MANAGER} -> MANAGER, CASHIER, PUBLIC</li>
     *   <li>{@code ROLE_CASHIER} -> CASHIER, PUBLIC</li>
     *   <li>Other authenticated user -> PUBLIC</li>
     * </ul>
     * </p>
     */
    public static Set<String> resolveAllowedRoles(Set<String> authorities) {
        if (authorities == null || authorities.isEmpty()) {
            return Set.of();
        }

        Set<String> allowed = new HashSet<>();
        allowed.add("PUBLIC"); // All authenticated users can read public documents

        if (authorities.contains("ROLE_ADMIN")) {
            allowed.add("ROLE_ADMIN");
            allowed.add("ROLE_MANAGER");
            allowed.add("ROLE_CASHIER");
        } else if (authorities.contains("ROLE_MANAGER")) {
            allowed.add("ROLE_MANAGER");
            allowed.add("ROLE_CASHIER");
        } else if (authorities.contains("ROLE_CASHIER")) {
            allowed.add("ROLE_CASHIER");
        }

        return Set.copyOf(allowed);
    }

    public KnowledgeRetrievalEngine getRetrievalEngine() {
        return retrievalEngine;
    }
}
