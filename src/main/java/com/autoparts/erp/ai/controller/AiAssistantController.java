package com.autoparts.erp.ai.controller;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CancellationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.autoparts.erp.ai.dto.AiChatRequest;
import com.autoparts.erp.ai.dto.AiChatResponse;
import com.autoparts.erp.ai.dto.AiConversationResponse;
import com.autoparts.erp.ai.dto.AiConversationMessagesResponse;
import com.autoparts.erp.ai.dto.AiConversationTitleRequest;
import com.autoparts.erp.ai.service.AiAssistantService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/ai")
public class AiAssistantController {

    private static final Logger log =
            LoggerFactory.getLogger(AiAssistantController.class);

    private final AiAssistantService aiAssistantService;

    public AiAssistantController(
            AiAssistantService aiAssistantService
    ) {
        this.aiAssistantService = aiAssistantService;
    }

    // ---------------------------------------------
    // CHAT
    // ---------------------------------------------

    @PostMapping("/chat")
    public ResponseEntity<AiChatResponse> chat(
            @Valid @RequestBody AiChatRequest request,
            Authentication authentication
    ) {
        long startedAtNanos = System.nanoTime();
        String executionStatus = "FAILED";

        try {
            String userSubject = getAuthenticatedUser(authentication);

            AiChatResponse response = aiAssistantService.chat(
                    request.conversationId(),
                    request.message(),
                    userSubject
            );

            executionStatus = "COMPLETED";
            return ResponseEntity.ok(response);
        } catch (CancellationException exception) {
            executionStatus = "CANCELLED";
            throw exception;
        } catch (RuntimeException | Error exception) {
            executionStatus = "FAILED";
            throw exception;
        } finally {
            log.info(
                    "AI HTTP chat handler completed. conversationId={}, durationMs={}, executionStatus={}",
                    request.conversationId(),
                    (System.nanoTime() - startedAtNanos) / 1_000_000,
                    executionStatus);
        }
    }

    // ---------------------------------------------
    // GET ALL CONVERSATIONS
    // ---------------------------------------------

    @GetMapping("/conversations")
    public ResponseEntity<List<AiConversationResponse>> getConversations(
            Authentication authentication
    ) {

        String userSubject = getAuthenticatedUser(authentication);

        return ResponseEntity.ok(
                aiAssistantService.getConversations(userSubject)
        );
    }

    // ---------------------------------------------
    // GET CONVERSATION MESSAGES
    // ---------------------------------------------

    @GetMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<AiConversationMessagesResponse> getMessages(
            @PathVariable UUID conversationId,
            Authentication authentication
    ) {

        String userSubject = getAuthenticatedUser(authentication);

        return ResponseEntity.ok(
                aiAssistantService.getConversationMessages(
                        conversationId,
                        userSubject
                )
        );
    }

    // ---------------------------------------------
    // RENAME CONVERSATION
    // ---------------------------------------------

    @PatchMapping("/conversations/{conversationId}/title")
    public ResponseEntity<AiConversationResponse> renameConversation(
            @PathVariable UUID conversationId,
            @Valid @RequestBody AiConversationTitleRequest request,
            Authentication authentication
    ) {

        String userSubject = getAuthenticatedUser(authentication);

        AiConversationResponse response =
                aiAssistantService.renameConversation(
                        conversationId,
                        userSubject,
                        request.title()
                );

        return ResponseEntity.ok(response);
    }

    // ---------------------------------------------
    // AUTHENTICATED USER
    // ---------------------------------------------

    private String getAuthenticatedUser(
            Authentication authentication
    ) {

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {

            throw new AccessDeniedException(
                    "Authentication is required"
            );
        }

        return authentication.getName();
    }
    
    @DeleteMapping("/conversations/{conversationId}")
    public ResponseEntity<Void> archiveConversation(
            @PathVariable UUID conversationId,
            Authentication authentication
    ) {

        String userSubject = getAuthenticatedUser(authentication);

        aiAssistantService.archiveConversation(
                conversationId,
                userSubject
        );

        return ResponseEntity.noContent().build();
    }
    
    @PostMapping("/conversations/{conversationId}/cancel")
    public ResponseEntity<Void> cancel(
            @PathVariable UUID conversationId,
            Authentication authentication
    ) {

        String userSubject = getAuthenticatedUser(authentication);

        boolean cancelled = aiAssistantService.cancel(
                conversationId,
                userSubject
        );

        if (!cancelled) {
            return ResponseEntity
                    .status(409)
                    .build();
        }

        return ResponseEntity.accepted().build();
    }
}