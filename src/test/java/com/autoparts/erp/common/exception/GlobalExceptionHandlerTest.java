
package com.autoparts.erp.common.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void invalidReportDateRangeReturnsHttp400() {
        var response = handler.handleClientValidationException(
                new ClientValidationException("fromDate must be before or equal to toDate"),
                request()
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("fromDate must be before or equal to toDate", response.getBody().message());
        assertEquals("Bad Request", response.getBody().error());
    }

    @Test
    void invalidReportTargetInputReturnsHttp400() {
        var response = handler.handleClientValidationException(
                new ClientValidationException("Target dimensions do not match the selected metric"),
                request()
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Target dimensions do not match the selected metric", response.getBody().message());
        assertEquals("Bad Request", response.getBody().error());
    }

    @Test
    void invalidCashMovementInputReturnsHttp400() {
        var response = handler.handleClientValidationException(
                new ClientValidationException("Cash movement amount must be greater than zero"),
                request()
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Cash movement amount must be greater than zero", response.getBody().message());
        assertEquals("Bad Request", response.getBody().error());
    }

    @Test
    void invalidExpensePaginationInputReturnsHttp400() {
        var response = handler.handleClientValidationException(
                new ClientValidationException("Page size must be between 1 and 100"),
                request()
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Page size must be between 1 and 100", response.getBody().message());
        assertEquals("Bad Request", response.getBody().error());
    }

    @Test
    void invalidKnowledgeDocumentCodeReturnsHttp400() {
        var response = handler.handleClientValidationException(
                new ClientValidationException("documentCode must be 3-100 alphanumeric, underscore, or hyphen characters"),
                request()
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("documentCode must be 3-100 alphanumeric, underscore, or hyphen characters", response.getBody().message());
        assertEquals("Bad Request", response.getBody().error());
    }

    @Test
    void invalidChatMessageLengthReturnsHttp400() {
        var response = handler.handleClientValidationException(
                new ClientValidationException("Chat message cannot exceed 8000 characters"),
                request()
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Chat message cannot exceed 8000 characters", response.getBody().message());
        assertEquals("Bad Request", response.getBody().error());
    }

    @Test
    void invalidToolDateRangeReturnsHttp400() {
        var response = handler.handleClientValidationException(
                new ClientValidationException("End date must not be before start date."),
                request()
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("End date must not be before start date.", response.getBody().message());
        assertEquals("Bad Request", response.getBody().error());
    }

    @Test
    void mapsBlankClientValidationExceptionToDefaultBadRequest() {
        var response = handler.handleClientValidationException(
                new ClientValidationException(""),
                request()
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Invalid request arguments.", response.getBody().message());
        assertEquals("Bad Request", response.getBody().error());
    }

    @Test
    void unexpectedIllegalArgumentExceptionDoesNotLeakInternalMessage() {
        var response = handler.handleUnexpectedException(
                new IllegalArgumentException("internal invariant failed: column_index=-1"),
                request()
        );

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertFalse(response.getBody().message().contains("column_index"));
        assertTrue(response.getBody().message().contains("Reference:"));
    }

    @Test
    void unexpectedExceptionDoesNotExposeInternalMessage() {
        var response = handler.handleUnexpectedException(
                new IllegalStateException("database password=secret-value"),
                request()
        );

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertFalse(
                response.getBody().message().contains("secret-value"));
        assertTrue(
                response.getBody().message().contains("Reference:"));
    }
}
