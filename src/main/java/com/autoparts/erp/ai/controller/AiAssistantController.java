
package com.autoparts.erp.ai.controller;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import com.autoparts.erp.ai.dto.AiChatRequest;
import com.autoparts.erp.ai.dto.AiChatResponse;
import com.autoparts.erp.ai.dto.AiConversationResponse;
import com.autoparts.erp.ai.dto.AiConversationMessagesResponse;
import com.autoparts.erp.ai.dto.AiConversationTitleRequest;
import com.autoparts.erp.ai.security.ErpAiExecutionContext;
import com.autoparts.erp.ai.service.AiAssistantService;
import com.autoparts.erp.auth.security.annotation.RequiresPermission;
import com.autoparts.erp.common.annotation.DeprecatedUnpagedEndpoint;
import com.autoparts.erp.common.dto.PageResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/ai")
@RequiresPermission("AI_ASSISTANT.USE")
public class AiAssistantController {

	private final AiAssistantService aiAssistantService;

	public AiAssistantController(AiAssistantService aiAssistantService) {
		this.aiAssistantService = aiAssistantService;
	}

	@PostMapping("/chat")
	public ResponseEntity<AiChatResponse> chat(@Valid @RequestBody AiChatRequest request,
			Authentication authentication) {

		String userSubject = getAuthenticatedUser(authentication);

		// Extract authorities exclusively from Spring Security.
		Set<String> authorities = authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority)
				.collect(Collectors.toUnmodifiableSet());

		// ErpAiExecutionContext requires a non-null conversation ID.
		// For a new conversation, AiAssistantService will replace
		// this temporary ID with its server-generated conversation ID.
		UUID contextConversationId = request.conversationId() != null ? request.conversationId() : UUID.randomUUID();

		ErpAiExecutionContext executionContext = ErpAiExecutionContext.create(contextConversationId, userSubject,
				authorities);

		AiChatResponse response = aiAssistantService.chat(request.conversationId(), request.message(), userSubject,
				executionContext);

		return ResponseEntity.ok(response);
	}

	@Deprecated(since = "2.0")
	@DeprecatedUnpagedEndpoint(limit = 100, pagedUrl = "/api/v1/ai/conversations/paged", deprecationDate = "2026-11-22T00:00:00Z")
	@GetMapping("/conversations")
	public ResponseEntity<List<AiConversationResponse>> getConversations(Authentication authentication) {

		String userSubject = getAuthenticatedUser(authentication);

		return ResponseEntity.ok(aiAssistantService.getConversations(userSubject));
	}

	@GetMapping("/conversations/paged")
	public ResponseEntity<PageResponse<AiConversationResponse>> getConversationsPaged(
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
			@RequestParam(defaultValue = "updatedAt") String sortBy,
			@RequestParam(defaultValue = "desc") String sortDir, Authentication authentication) {

		String userSubject = getAuthenticatedUser(authentication);

		return ResponseEntity.ok(aiAssistantService.getConversationsPaged(userSubject, page, size, sortBy, sortDir));
	}

	@GetMapping("/conversations/{conversationId}/messages")
	public ResponseEntity<AiConversationMessagesResponse> getMessages(@PathVariable UUID conversationId,
			Authentication authentication) {

		String userSubject = getAuthenticatedUser(authentication);

		return ResponseEntity.ok(aiAssistantService.getConversationMessages(conversationId, userSubject));
	}

	@PatchMapping("/conversations/{conversationId}/title")
	public ResponseEntity<AiConversationResponse> renameConversation(@PathVariable UUID conversationId,
			@Valid @RequestBody AiConversationTitleRequest request, Authentication authentication) {

		String userSubject = getAuthenticatedUser(authentication);

		return ResponseEntity.ok(aiAssistantService.renameConversation(conversationId, userSubject, request.title()));
	}

	@DeleteMapping("/conversations/{conversationId}")
	public ResponseEntity<Void> archiveConversation(@PathVariable UUID conversationId, Authentication authentication) {

		String userSubject = getAuthenticatedUser(authentication);

		aiAssistantService.archiveConversation(conversationId, userSubject);

		return ResponseEntity.noContent().build();
	}

	@PostMapping("/conversations/{conversationId}/cancel")
	public ResponseEntity<Void> cancel(@PathVariable UUID conversationId, Authentication authentication) {

		String userSubject = getAuthenticatedUser(authentication);

		boolean cancelled = aiAssistantService.cancel(conversationId, userSubject);

		if (!cancelled) {
			return ResponseEntity.status(409).build();
		}

		return ResponseEntity.accepted().build();
	}

	private String getAuthenticatedUser(Authentication authentication) {

		if (authentication == null || !authentication.isAuthenticated()
				|| authentication instanceof AnonymousAuthenticationToken) {

			throw new AccessDeniedException("Authentication is required");
		}

		return authentication.getName();
	}
}
