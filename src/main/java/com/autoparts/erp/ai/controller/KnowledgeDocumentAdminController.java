package com.autoparts.erp.ai.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.autoparts.erp.ai.rag.ingestion.KnowledgeIngestionService;
import com.autoparts.erp.ai.security.ErpAiExecutionContext;

/**
 * Administrative controller for knowledge document approval lifecycle.
 * Restricted to authenticated ROLE_ADMIN and ROLE_MANAGER.
 */
@RestController
@RequestMapping("/api/v1/ai/knowledge/documents")
public class KnowledgeDocumentAdminController {

    private final KnowledgeIngestionService ingestionService;

    public KnowledgeDocumentAdminController(KnowledgeIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping("/{documentId}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Map<String, Object>> approveDocument(
            @PathVariable UUID documentId,
            Authentication authentication
    ) {
        ErpAiExecutionContext context = buildContext(authentication);
        ingestionService.approveDocument(documentId, context);
        return ResponseEntity.ok(Map.of(
                "documentId", documentId,
                "status", "APPROVED",
                "approvedBy", context.userSubject()
        ));
    }

    @PostMapping("/{documentId}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Map<String, Object>> rejectDocument(
            @PathVariable UUID documentId,
            Authentication authentication
    ) {
        ErpAiExecutionContext context = buildContext(authentication);
        ingestionService.rejectDocument(documentId, context);
        return ResponseEntity.ok(Map.of(
                "documentId", documentId,
                "status", "REJECTED",
                "rejectedBy", context.userSubject()
        ));
    }

    @PostMapping("/code/{documentCode}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Map<String, Object>> approveDocumentByCode(
            @PathVariable String documentCode,
            Authentication authentication
    ) {
        ErpAiExecutionContext context = buildContext(authentication);
        ingestionService.approveDocumentByCode(documentCode, context);
        return ResponseEntity.ok(Map.of(
                "documentCode", documentCode,
                "status", "APPROVED",
                "approvedBy", context.userSubject()
        ));
    }

    @PostMapping("/code/{documentCode}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Map<String, Object>> rejectDocumentByCode(
            @PathVariable String documentCode,
            Authentication authentication
    ) {
        ErpAiExecutionContext context = buildContext(authentication);
        ingestionService.rejectDocumentByCode(documentCode, context);
        return ResponseEntity.ok(Map.of(
                "documentCode", documentCode,
                "status", "REJECTED",
                "rejectedBy", context.userSubject()
        ));
    }

    private ErpAiExecutionContext buildContext(Authentication authentication) {
        String username = (authentication != null && authentication.getName() != null)
                ? authentication.getName()
                : "anonymous";
        List<String> authorities = (authentication != null && authentication.getAuthorities() != null)
                ? authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList()
                : List.of();
        return ErpAiExecutionContext.create(UUID.randomUUID(), username, authorities);
    }
}
