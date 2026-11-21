package com.autoparts.erp.common.exception;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import com.autoparts.erp.ai.exception.AiRateLimitExceededException;

class AiRateLimitExceptionHandlerTest {

    @Test
    void returns429AndRetryAfterHeader() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/ai/chat");

        var response = handler.handleAiRateLimitExceeded(
                new AiRateLimitExceededException("Too many requests", 17),
                request);

        assertEquals(429, response.getStatusCode().value());
        assertEquals("17", response.getHeaders().getFirst("Retry-After"));
        assertNotNull(response.getBody());
    }
}
