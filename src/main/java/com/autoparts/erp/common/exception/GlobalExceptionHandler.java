
package com.autoparts.erp.common.exception;

import java.time.Instant;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import com.autoparts.erp.ai.exception.AiExecutionInProgressException;
import com.autoparts.erp.ai.exception.AiProviderResponseException;
import com.autoparts.erp.ai.exception.AiProviderTimeoutException;
import com.autoparts.erp.ai.exception.AiRateLimitExceededException;
import org.springframework.http.HttpHeaders;
import com.autoparts.erp.common.response.ApiError;
import java.util.concurrent.CancellationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthenticationException(
            AuthenticationException exception,
            HttpServletRequest request) {

        ApiError error = new ApiError(
                Instant.now(),
                HttpStatus.UNAUTHORIZED.value(),
                "Unauthorized",
                "Invalid username or password.",
                request.getRequestURI()
        );

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationException(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {

        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(fieldError -> fieldError.getDefaultMessage())
                .orElse("Request validation failed.");

        ApiError error = new ApiError(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                message,
                request.getRequestURI()
        );

        return ResponseEntity
                .badRequest()
                .body(error);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatusException(
            ResponseStatusException exception,
            HttpServletRequest request) {

        HttpStatus status = HttpStatus.resolve(
                exception.getStatusCode().value()
        );
        boolean serverError = exception.getStatusCode().is5xxServerError();
        String message = serverError || exception.getReason() == null
                ? "Request failed."
                : exception.getReason();

        ApiError error = new ApiError(
                Instant.now(),
                exception.getStatusCode().value(),
                status == null ? "HTTP Error" : status.getReasonPhrase(),
                message,
                request.getRequestURI()
        );

        return ResponseEntity
                .status(exception.getStatusCode())
                .body(error);
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(
            ApiException ex,
            HttpServletRequest request) {

        ApiErrorCodeDefinition error = ex.getError();

        ApiError response = new ApiError(
                Instant.now(),
                error.getStatus().value(),
                error.name(),
                ex.getMessage(),
                request.getRequestURI()
        );

        return ResponseEntity
                .status(error.getStatus())
                .body(response);
    }
    
    @ExceptionHandler(LockedException.class)
    public ResponseEntity<ApiError> handleLockedException(
            LockedException exception,
            HttpServletRequest request) {

        ApiError error = new ApiError(
                Instant.now(),
                HttpStatus.UNAUTHORIZED.value(),
                "Account Locked",
                "Your account is locked. Please wait 15 minutes before trying again.",
                request.getRequestURI()
        );

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(error);
    }
    
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentials(
            BadCredentialsException exception,
            HttpServletRequest request
    ) {

        ApiError error = new ApiError(
                Instant.now(),
                HttpStatus.UNAUTHORIZED.value(),
                "Unauthorized",
                "The refresh token is invalid or expired.",
                request.getRequestURI()
        );

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(error);
    }
    
    @ExceptionHandler(AiProviderTimeoutException.class)
    public ResponseEntity<ApiError> handleAiProviderTimeout(
            AiProviderTimeoutException exception,
            HttpServletRequest request) {

        ApiError error = new ApiError(
                Instant.now(),
                HttpStatus.GATEWAY_TIMEOUT.value(),
                "Gateway Timeout",
                "The AI provider did not respond within the allowed time.",
                request.getRequestURI()
        );

        return ResponseEntity
                .status(HttpStatus.GATEWAY_TIMEOUT)
                .body(error);
    }

    @ExceptionHandler(AiRateLimitExceededException.class)
    public ResponseEntity<ApiError> handleAiRateLimitExceeded(
            AiRateLimitExceededException exception,
            HttpServletRequest request) {

        ApiError error = new ApiError(
                Instant.now(),
                HttpStatus.TOO_MANY_REQUESTS.value(),
                "Too Many Requests",
                exception.getMessage(),
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER,
                        Long.toString(exception.retryAfterSeconds()))
                .body(error);
    }

    @ExceptionHandler(AiExecutionInProgressException.class)
    public ResponseEntity<ApiError> handleAiExecutionInProgress(
            AiExecutionInProgressException exception,
            HttpServletRequest request) {

        ApiError error = new ApiError(
                Instant.now(),
                HttpStatus.CONFLICT.value(),
                "AI Execution In Progress",
                "An AI request is already running for this conversation.",
                request.getRequestURI()
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(error);
    }

    @ExceptionHandler(AiProviderResponseException.class)
    public ResponseEntity<ApiError> handleAiProviderResponse(
            AiProviderResponseException exception,
            HttpServletRequest request) {

        ApiError error = new ApiError(
                Instant.now(),
                HttpStatus.BAD_GATEWAY.value(),
                "AI Provider Error",
                "The AI provider could not complete the request.",
                request.getRequestURI()
        );

        return ResponseEntity
                .status(HttpStatus.BAD_GATEWAY)
                .body(error);
    }
    
    @ExceptionHandler(CancellationException.class)
    public ResponseEntity<ApiError> handleCancellationException(
            CancellationException exception,
            HttpServletRequest request) {

        ApiError error = new ApiError(
                Instant.now(),
                HttpStatus.CONFLICT.value(),
                "AI Execution Cancelled",
                "The AI agent execution was cancelled.",
                request.getRequestURI()
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request) {

        String incidentId = java.util.UUID.randomUUID().toString();
        log.error("Unhandled request failure. incidentId={}, path={}",
                incidentId, request.getRequestURI(), exception);

        ApiError error = new ApiError(
                Instant.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                "The request could not be completed. Reference: " + incidentId,
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(
            AccessDeniedException exception, HttpServletRequest request) {
        return buildError(HttpStatus.FORBIDDEN, "Forbidden",
                "You do not have permission to perform this action.", request);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<ApiError> handleMalformedRequest(
            Exception exception, HttpServletRequest request) {
        return buildError(HttpStatus.BAD_REQUEST, "Bad Request",
                "The request contains invalid or missing values.", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataConflict(
            DataIntegrityViolationException exception, HttpServletRequest request) {
        log.warn("Database constraint rejected request. path={}", request.getRequestURI());
        return buildError(HttpStatus.CONFLICT, "Conflict",
                "The operation conflicts with existing data.", request);
    }

    private ResponseEntity<ApiError> buildError(
            HttpStatus status, String errorName, String message,
            HttpServletRequest request) {
        ApiError error = new ApiError(Instant.now(), status.value(),
                errorName, message, request.getRequestURI());
        return ResponseEntity.status(status).body(error);
    }
}
