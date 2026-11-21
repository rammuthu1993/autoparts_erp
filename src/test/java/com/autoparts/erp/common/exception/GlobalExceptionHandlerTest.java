
package com.autoparts.erp.common.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.CancellationException;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import com.autoparts.erp.ai.exception.AiExecutionInProgressException;
import com.autoparts.erp.ai.exception.AiProviderResponseException;
import com.autoparts.erp.ai.exception.AiProviderTimeoutException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler();

    private MockHttpServletRequest request() {
        return new MockHttpServletRequest(
                "POST",
                "/api/v1/ai/chat"
        );
    }

    @Test
    void mapsDuplicateConversationExecutionToConflict() {
        var response = handler.handleAiExecutionInProgress(
                new AiExecutionInProgressException(
                        "An AI execution is already active for this conversation"
                ),
                request()
        );

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());

        assertEquals(
                "An AI request is already running for this conversation.",
                response.getBody().message()
        );
    }

    @Test
    void mapsEmptyProviderResponseToBadGateway() {
        var response = handler.handleAiProviderResponse(
                new AiProviderResponseException(
                        "AI provider returned an empty response. Please try again."
                ),
                request()
        );

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());

        assertEquals(
                "The AI provider could not complete the request.",
                response.getBody().message()
        );
    }

    @Test
    void mapsProviderTimeoutToGatewayTimeout() {
        var response = handler.handleAiProviderTimeout(
                new AiProviderTimeoutException(
                        "AI provider request timed out.",
                        null
                ),
                request()
        );

        assertEquals(
                HttpStatus.GATEWAY_TIMEOUT,
                response.getStatusCode()
        );

        assertEquals(
                "The AI provider did not respond within the allowed time.",
                response.getBody().message()
        );
    }

    @Test
    void mapsCancellationToConflict() {
        var response = handler.handleCancellationException(
                new CancellationException(
                        "AI agent execution was cancelled"
                ),
                request()
        );

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());

        assertEquals(
                "The AI agent execution was cancelled.",
                response.getBody().message()
        );
    }

    @Test
    void unexpectedExceptionDoesNotExposeInternalMessage() {
        var response = handler.handleUnexpectedException(
                new IllegalStateException("database password=secret-value"),
                request()
        );

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        org.junit.jupiter.api.Assertions.assertFalse(
                response.getBody().message().contains("secret-value"));
        org.junit.jupiter.api.Assertions.assertTrue(
                response.getBody().message().contains("Reference:"));
    }
}
