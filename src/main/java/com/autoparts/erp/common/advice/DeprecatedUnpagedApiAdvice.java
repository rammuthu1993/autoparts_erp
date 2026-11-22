package com.autoparts.erp.common.advice;

import com.autoparts.erp.common.annotation.DeprecatedUnpagedEndpoint;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Controller advice that inspects responses from legacy unpaged endpoints annotated with
 * {@link DeprecatedUnpagedEndpoint} or {@link Deprecated}.
 *
 * <p>Communicates clearly to API clients/frontend whether results are partial or capped by the safety ceiling,
 * providing accurate RFC 9745 Deprecation, RFC 8594 Sunset, X-Result-*, and alternative /paged Link headers.
 * Truncation is determined via limit-plus-one extra-row detection, rather than inferring limits from result size.
 */
@RestControllerAdvice
public class DeprecatedUnpagedApiAdvice implements ResponseBodyAdvice<Object> {

    /**
     * Strict IMF-fixdate formatter per RFC 9110 Section 5.6.7.
     * Enforces two-digit day-of-month ("dd", 01-31), 3-letter month, 4-digit year, 24-hour time, and GMT zone.
     * (Replaces RFC 1123 which allowed single-digit days).
     */
    private static final DateTimeFormatter IMF_FIXDATE_FORMATTER =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
                    .withZone(ZoneOffset.UTC);

    @Override
    public boolean supports(
            MethodParameter returnType,
            Class<? extends HttpMessageConverter<?>> converterType
    ) {
        return returnType.hasMethodAnnotation(DeprecatedUnpagedEndpoint.class)
                || returnType.hasMethodAnnotation(Deprecated.class);
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response
    ) {
        if (!(body instanceof Collection<?> collection)) {
            return body;
        }

        DeprecatedUnpagedEndpoint endpointMeta = returnType.getMethodAnnotation(DeprecatedUnpagedEndpoint.class);
        int configuredCeiling = (endpointMeta != null) ? endpointMeta.limit() : 500;
        int effectiveLimit = resolveEffectiveLimit(configuredCeiling, request);
        String pagedUrl = (endpointMeta != null) ? endpointMeta.pagedUrl() : "";

        int fetchedCount = collection.size();
        boolean extraRowExists = fetchedCount > effectiveLimit;
        boolean isTruncated = extraRowExists;

        int returnCount;
        Object responseBody = body;

        if (extraRowExists) {
            // Trim to effective limit
            if (body instanceof List<?> list) {
                responseBody = new ArrayList<>(list.subList(0, effectiveLimit));
            } else {
                responseBody = collection.stream().limit(effectiveLimit).toList();
            }
            returnCount = effectiveLimit;
        } else {
            returnCount = fetchedCount;
        }

        HttpHeaders headers = response.getHeaders();
        headers.set("X-Result-Count", String.valueOf(returnCount));
        headers.set("X-Result-Limit", String.valueOf(effectiveLimit));
        headers.set("X-Result-Truncated", String.valueOf(isTruncated));

        // RFC 9745 Deprecation header (only emitted if a valid configured date is present)
        if (endpointMeta != null) {
            String deprecationValue = formatRfc9745Deprecation(endpointMeta.deprecationDate());
            if (deprecationValue != null) {
                headers.set("Deprecation", deprecationValue);
            }

            // RFC 8594 Sunset header (only emitted if a valid future sunset date is present)
            String sunsetValue = formatRfc8594Sunset(endpointMeta.sunsetDate());
            if (sunsetValue != null) {
                headers.set("Sunset", sunsetValue);
            }
        }

        // Link header: emitted ONLY if a valid replacement paged URL exists
        if (pagedUrl != null && !pagedUrl.isBlank()) {
            String link = resolvePagedLink(pagedUrl, request);
            headers.set("Link", "<" + link + ">; rel=\"alternate\"");
        }

        return responseBody;
    }

    private int resolveEffectiveLimit(int ceiling, ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            String limitParam = servletRequest.getServletRequest().getParameter("limit");
            if (limitParam != null && !limitParam.isBlank()) {
                try {
                    int parsed = Integer.parseInt(limitParam.trim());
                    if (parsed > 0) {
                        return Math.min(ceiling, parsed);
                    }
                } catch (NumberFormatException ignored) {
                    return ceiling;
                }
            }
        }
        if (request != null && request.getURI() != null) {
            String query = request.getURI().getQuery();
            if (query != null && query.contains("limit=")) {
                for (String param : query.split("&")) {
                    if (param.startsWith("limit=")) {
                        try {
                            int parsed = Integer.parseInt(param.substring(6));
                            if (parsed > 0) {
                                return Math.min(ceiling, parsed);
                            }
                        } catch (NumberFormatException ignored) {
                            return ceiling;
                        }
                    }
                }
            }
        }
        return ceiling;
    }

    private String resolvePagedLink(String pagedUrl, ServerHttpRequest request) {
        if (pagedUrl.contains("{") && request != null && request.getURI() != null) {
            String path = request.getURI().getPath();
            return path.endsWith("/") ? path + "paged" : path + "/paged";
        }
        return pagedUrl;
    }

    /**
     * Formats configured deprecation date string into RFC 9745 structured date {@code @<seconds>}.
     * Returns null if missing, empty, or invalid.
     */
    String formatRfc9745Deprecation(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        String trimmed = dateStr.trim();
        if (trimmed.startsWith("@")) {
            String num = trimmed.substring(1);
            if (num.matches("\\d+")) {
                return "@" + num;
            }
            return null;
        }
        if (trimmed.matches("\\d+")) {
            return "@" + trimmed;
        }
        try {
            Instant instant;
            if (trimmed.length() == 10 && trimmed.charAt(4) == '-' && trimmed.charAt(7) == '-') {
                instant = LocalDate.parse(trimmed).atStartOfDay(ZoneOffset.UTC).toInstant();
            } else {
                instant = Instant.parse(trimmed);
            }
            return "@" + instant.getEpochSecond();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    /**
     * Formats configured sunset date string into RFC 8594 / RFC 9110 HTTP-date (IMF-fixdate).
     * Returns null if missing, empty, or invalid.
     */
    String formatRfc8594Sunset(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        String trimmed = dateStr.trim();
        try {
            Instant instant;
            if (trimmed.startsWith("@")) {
                instant = Instant.ofEpochSecond(Long.parseLong(trimmed.substring(1)));
            } else if (trimmed.matches("\\d+")) {
                instant = Instant.ofEpochSecond(Long.parseLong(trimmed));
            } else if (trimmed.length() == 10 && trimmed.charAt(4) == '-' && trimmed.charAt(7) == '-') {
                instant = LocalDate.parse(trimmed).atStartOfDay(ZoneOffset.UTC).toInstant();
            } else {
                instant = Instant.parse(trimmed);
            }
            return IMF_FIXDATE_FORMATTER.format(instant);
        } catch (Exception ignored) {
            return null;
        }
    }
}
