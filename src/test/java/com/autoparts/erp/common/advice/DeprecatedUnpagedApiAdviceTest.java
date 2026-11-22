package com.autoparts.erp.common.advice;

import com.autoparts.erp.common.annotation.DeprecatedUnpagedEndpoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("DeprecatedUnpagedApiAdvice: Accurate Safety Limit, RFC 9745 Deprecation & Truncation Headers")
class DeprecatedUnpagedApiAdviceTest {

    private final DeprecatedUnpagedApiAdvice advice = new DeprecatedUnpagedApiAdvice();

    @Deprecated(since = "2.0")
    @DeprecatedUnpagedEndpoint(
            limit = 50,
            pagedUrl = "/api/v1/sample/limit50/paged",
            deprecationDate = "2026-11-22T00:00:00Z"
    )
    public List<String> limit50Method() {
        return List.of();
    }

    @Deprecated(since = "2.0")
    @DeprecatedUnpagedEndpoint(
            limit = 100,
            pagedUrl = "/api/v1/sample/limit100/paged",
            deprecationDate = "2026-11-22T00:00:00Z"
    )
    public List<String> limit100Method() {
        return List.of();
    }

    @Deprecated(since = "2.0")
    @DeprecatedUnpagedEndpoint(
            limit = 500,
            pagedUrl = "/api/v1/sample/limit500/paged",
            deprecationDate = "2026-11-22T00:00:00Z"
    )
    public List<String> limit500Method() {
        return List.of();
    }

    @Deprecated(since = "2.0")
    @DeprecatedUnpagedEndpoint(limit = 50, pagedUrl = "")
    public List<String> limit50NoPagedRouteMethod() {
        return List.of();
    }

    @Deprecated(since = "2.0")
    @DeprecatedUnpagedEndpoint(limit = 500, pagedUrl = "/api/v1/cash-registers/sessions/{sessionUuid}/movements/paged")
    public List<String> parameterizedPagedRouteMethod() {
        return List.of();
    }

    @Deprecated(since = "2.0")
    @DeprecatedUnpagedEndpoint(
            limit = 50,
            pagedUrl = "/api/v1/admin/audit-logs/paged",
            deprecationDate = "2026-11-22T00:00:00Z"
    )
    public List<String> auditLogEndpointMethod() {
        return List.of();
    }

    @Deprecated(since = "2.0")
    @DeprecatedUnpagedEndpoint(
            limit = 50,
            deprecationDate = "@1688169599",
            sunsetDate = "2027-06-01T00:00:00Z"
    )
    public List<String> epochDateWithSunsetMethod() {
        return List.of();
    }

    @Deprecated(since = "2.0")
    @DeprecatedUnpagedEndpoint(limit = 50, deprecationDate = "invalid-date-format")
    public List<String> invalidDateFormatMethod() {
        return List.of();
    }

    @Deprecated(since = "2.0")
    public List<String> fallbackDeprecatedMethod() {
        return List.of();
    }

    public List<String> normalMethod() {
        return List.of();
    }

    private List<String> createItems(int count) {
        List<String> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add("item-" + i);
        }
        return list;
    }

    private MethodParameter getMethodParameter(String methodName) throws NoSuchMethodException {
        Method method = getClass().getMethod(methodName);
        return new MethodParameter(method, -1);
    }

    private ServerHttpRequest mockRequestWithQuery(String query) {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        String uriStr = "http://localhost:8080/api/v1/admin/audit-logs" + (query != null ? "?" + query : "");
        when(request.getURI()).thenReturn(URI.create(uriStr));
        return request;
    }

    @Test
    @DisplayName("supports returns true for @DeprecatedUnpagedEndpoint or @Deprecated, false otherwise")
    void testSupports() throws NoSuchMethodException {
        assertTrue(advice.supports(getMethodParameter("limit50Method"), null));
        assertTrue(advice.supports(getMethodParameter("fallbackDeprecatedMethod"), null));
        assertFalse(advice.supports(getMethodParameter("normalMethod"), null));
    }

    // =========================================================================
    // ISSUE 1: AUDIT LOG CONFIGURABLE LIMIT (20, 50, 75, 100, INVALID)
    // =========================================================================

    @Test
    @DisplayName("Issue 1: Audit endpoint with limit=20 requested, 21 rows fetched -> trims to 20, limit=20, truncated=true")
    void testAuditLogLimit20With21Rows() throws NoSuchMethodException {
        MethodParameter param = getMethodParameter("auditLogEndpointMethod");
        List<String> items = createItems(21);
        ServerHttpRequest request = mockRequestWithQuery("limit=20");

        HttpHeaders headers = new HttpHeaders();
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        when(response.getHeaders()).thenReturn(headers);

        Object result = advice.beforeBodyWrite(items, param, MediaType.APPLICATION_JSON, null, request, response);

        assertEquals("20", headers.getFirst("X-Result-Count"), "Client must receive requested limit (20) items");
        assertEquals("20", headers.getFirst("X-Result-Limit"), "Active limit must report requested limit 20");
        assertEquals("true", headers.getFirst("X-Result-Truncated"), "Must report truncated when 21 rows exist for limit 20");
        assertNull(headers.getFirst("Warning"), "Warning header was obsoleted by RFC 9111 and must not be emitted");
        assertEquals(20, ((List<?>) result).size());
    }

    @Test
    @DisplayName("Issue 1: Audit endpoint with limit=20 requested, 20 rows fetched -> complete, count=20, limit=20, truncated=false")
    void testAuditLogLimit20With20Rows() throws NoSuchMethodException {
        MethodParameter param = getMethodParameter("auditLogEndpointMethod");
        List<String> items = createItems(20);
        ServerHttpRequest request = mockRequestWithQuery("limit=20");

        HttpHeaders headers = new HttpHeaders();
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        when(response.getHeaders()).thenReturn(headers);

        Object result = advice.beforeBodyWrite(items, param, MediaType.APPLICATION_JSON, null, request, response);

        assertEquals("20", headers.getFirst("X-Result-Count"));
        assertEquals("20", headers.getFirst("X-Result-Limit"));
        assertEquals("false", headers.getFirst("X-Result-Truncated"), "Exactly 20 rows must not be reported as truncated");
        assertNull(headers.getFirst("Warning"));
        assertEquals(20, ((List<?>) result).size());
    }

    @Test
    @DisplayName("Issue 1: Audit endpoint with limit=50 requested, 51 rows fetched -> trims to 50, limit=50, truncated=true")
    void testAuditLogLimit50With51Rows() throws NoSuchMethodException {
        MethodParameter param = getMethodParameter("auditLogEndpointMethod");
        List<String> items = createItems(51);
        ServerHttpRequest request = mockRequestWithQuery("limit=50");

        HttpHeaders headers = new HttpHeaders();
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        when(response.getHeaders()).thenReturn(headers);

        Object result = advice.beforeBodyWrite(items, param, MediaType.APPLICATION_JSON, null, request, response);

        assertEquals("50", headers.getFirst("X-Result-Count"));
        assertEquals("50", headers.getFirst("X-Result-Limit"));
        assertEquals("true", headers.getFirst("X-Result-Truncated"));
        assertNull(headers.getFirst("Warning"), "Warning header was obsoleted by RFC 9111 and must not be emitted");
        assertEquals(50, ((List<?>) result).size());
    }

    @Test
    @DisplayName("Issue 1: Audit endpoint with limit=75 requested -> clamped to ceiling 50, limit=50")
    void testAuditLogLimit75ClampedToCeiling() throws NoSuchMethodException {
        MethodParameter param = getMethodParameter("auditLogEndpointMethod");
        List<String> items = createItems(51);
        ServerHttpRequest request = mockRequestWithQuery("limit=75");

        HttpHeaders headers = new HttpHeaders();
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        when(response.getHeaders()).thenReturn(headers);

        Object result = advice.beforeBodyWrite(items, param, MediaType.APPLICATION_JSON, null, request, response);

        assertEquals("50", headers.getFirst("X-Result-Count"));
        assertEquals("50", headers.getFirst("X-Result-Limit"), "Limit 75 must be clamped to ceiling 50");
        assertEquals("true", headers.getFirst("X-Result-Truncated"));
        assertEquals(50, ((List<?>) result).size());
    }

    @Test
    @DisplayName("Issue 1: Audit endpoint with limit=100 requested -> clamped to ceiling 50, limit=50")
    void testAuditLogLimit100ClampedToCeiling() throws NoSuchMethodException {
        MethodParameter param = getMethodParameter("auditLogEndpointMethod");
        List<String> items = createItems(51);
        ServerHttpRequest request = mockRequestWithQuery("limit=100");

        HttpHeaders headers = new HttpHeaders();
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        when(response.getHeaders()).thenReturn(headers);

        Object result = advice.beforeBodyWrite(items, param, MediaType.APPLICATION_JSON, null, request, response);

        assertEquals("50", headers.getFirst("X-Result-Count"));
        assertEquals("50", headers.getFirst("X-Result-Limit"), "Limit 100 must be clamped to ceiling 50");
        assertEquals("true", headers.getFirst("X-Result-Truncated"));
        assertEquals(50, ((List<?>) result).size());
    }

    @Test
    @DisplayName("Issue 1: Audit endpoint with invalid limit values (negative, zero, string) -> falls back to ceiling 50")
    void testAuditLogInvalidLimitFallsBackToCeiling() throws NoSuchMethodException {
        MethodParameter param = getMethodParameter("auditLogEndpointMethod");

        // Negative value
        ServerHttpRequest requestNegative = mockRequestWithQuery("limit=-5");
        HttpHeaders headersNeg = new HttpHeaders();
        ServerHttpResponse responseNeg = mock(ServerHttpResponse.class);
        when(responseNeg.getHeaders()).thenReturn(headersNeg);
        advice.beforeBodyWrite(createItems(10), param, MediaType.APPLICATION_JSON, null, requestNegative, responseNeg);
        assertEquals("50", headersNeg.getFirst("X-Result-Limit"));

        // Non-numeric string value
        ServerHttpRequest requestAlpha = mockRequestWithQuery("limit=abc");
        HttpHeaders headersAlpha = new HttpHeaders();
        ServerHttpResponse responseAlpha = mock(ServerHttpResponse.class);
        when(responseAlpha.getHeaders()).thenReturn(headersAlpha);
        advice.beforeBodyWrite(createItems(10), param, MediaType.APPLICATION_JSON, null, requestAlpha, responseAlpha);
        assertEquals("50", headersAlpha.getFirst("X-Result-Limit"));
    }

    // =========================================================================
    // ISSUE 2: RFC 9745 DEPRECATION & RFC 8594 SUNSET HEADERS
    // =========================================================================

    @Test
    @DisplayName("Issue 2: Real ISO-8601 deprecation date emits valid RFC 9745 structured date @<seconds>")
    void testRfc9745DeprecationHeaderFromIsoDate() throws NoSuchMethodException {
        MethodParameter param = getMethodParameter("limit50Method");

        HttpHeaders headers = new HttpHeaders();
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        when(response.getHeaders()).thenReturn(headers);

        advice.beforeBodyWrite(createItems(10), param, MediaType.APPLICATION_JSON, null, null, response);

        // "2026-11-22T00:00:00Z" -> @1795305600
        assertEquals("@1795305600", headers.getFirst("Deprecation"));
        assertFalse(headers.getFirst("Deprecation").contains("since"), "Must not emit Java annotation since value");
    }

    @Test
    @DisplayName("Issue 2: Unix epoch seconds deprecation date emits RFC 9745 @<seconds>")
    void testRfc9745DeprecationHeaderFromEpochSeconds() throws NoSuchMethodException {
        MethodParameter param = getMethodParameter("epochDateWithSunsetMethod");

        HttpHeaders headers = new HttpHeaders();
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        when(response.getHeaders()).thenReturn(headers);

        advice.beforeBodyWrite(createItems(10), param, MediaType.APPLICATION_JSON, null, null, response);

        assertEquals("@1688169599", headers.getFirst("Deprecation"));
    }

    @Test
    @DisplayName("Issue 2: Configured future removal date emits strict RFC 9110 Section 5.6.7 IMF-fixdate (2-digit day)")
    void testRfc8594SunsetHeader() throws NoSuchMethodException {
        MethodParameter param = getMethodParameter("epochDateWithSunsetMethod");

        HttpHeaders headers = new HttpHeaders();
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        when(response.getHeaders()).thenReturn(headers);

        advice.beforeBodyWrite(createItems(10), param, MediaType.APPLICATION_JSON, null, null, response);

        // "2027-06-01T00:00:00Z" -> strict 2-digit day "01", NOT "1"
        assertEquals("Tue, 01 Jun 2027 00:00:00 GMT", headers.getFirst("Sunset"),
                "RFC 9110 Section 5.6.7 requires strict 2DIGIT day of month (e.g. 01 Jun 2027)");
    }

    @Test
    @DisplayName("Issue 2: formatRfc8594Sunset strictly enforces 2DIGIT day for days 1-9 and days 10-31 per RFC 9110")
    void testFormatRfc8594SunsetStrictTwoDigitDay() {
        // Days 1-9 must have leading zero (2DIGIT)
        assertEquals("Tue, 01 Jun 2027 00:00:00 GMT", advice.formatRfc8594Sunset("2027-06-01T00:00:00Z"));
        assertEquals("Tue, 01 Jun 2027 00:00:00 GMT", advice.formatRfc8594Sunset("2027-06-01"));
        assertEquals("Wed, 02 Jun 2027 05:00:00 GMT", advice.formatRfc8594Sunset("2027-06-02T05:00:00Z"));
        assertEquals("Wed, 09 Jun 2027 08:15:30 GMT", advice.formatRfc8594Sunset("2027-06-09T08:15:30Z"));

        // Days 10-31
        assertEquals("Thu, 10 Jun 2027 10:00:00 GMT", advice.formatRfc8594Sunset("2027-06-10T10:00:00Z"));
        assertEquals("Wed, 15 Dec 2027 12:30:00 GMT", advice.formatRfc8594Sunset("2027-12-15T12:30:00Z"));
        assertEquals("Sat, 31 Jul 2027 23:59:59 GMT", advice.formatRfc8594Sunset("2027-07-31T23:59:59Z"));

        // Unix timestamp format
        assertEquals("Sun, 22 Nov 2026 00:00:00 GMT", advice.formatRfc8594Sunset("@1795305600"));
        assertEquals("Sun, 22 Nov 2026 00:00:00 GMT", advice.formatRfc8594Sunset("1795305600"));

        // Null / blank / invalid inputs
        assertNull(advice.formatRfc8594Sunset(null));
        assertNull(advice.formatRfc8594Sunset(""));
        assertNull(advice.formatRfc8594Sunset("   "));
        assertNull(advice.formatRfc8594Sunset("not-a-date"));
    }

    @Test
    @DisplayName("Issue 2: Missing or invalid deprecation date omits Deprecation and Sunset headers")
    void testMissingOrInvalidDeprecationDateOmitsHeaders() throws NoSuchMethodException {
        // Missing date (empty string)
        MethodParameter missingParam = getMethodParameter("limit50NoPagedRouteMethod");
        HttpHeaders headersMissing = new HttpHeaders();
        ServerHttpResponse responseMissing = mock(ServerHttpResponse.class);
        when(responseMissing.getHeaders()).thenReturn(headersMissing);
        advice.beforeBodyWrite(createItems(10), missingParam, MediaType.APPLICATION_JSON, null, null, responseMissing);
        assertNull(headersMissing.getFirst("Deprecation"), "Must omit Deprecation header if date is not configured");
        assertNull(headersMissing.getFirst("Sunset"), "Must omit Sunset header if date is not configured");

        // Invalid format
        MethodParameter invalidParam = getMethodParameter("invalidDateFormatMethod");
        HttpHeaders headersInvalid = new HttpHeaders();
        ServerHttpResponse responseInvalid = mock(ServerHttpResponse.class);
        when(responseInvalid.getHeaders()).thenReturn(headersInvalid);
        advice.beforeBodyWrite(createItems(10), invalidParam, MediaType.APPLICATION_JSON, null, null, responseInvalid);
        assertNull(headersInvalid.getFirst("Deprecation"), "Must omit Deprecation header if date format is invalid");

        // Pure Java @Deprecated without @DeprecatedUnpagedEndpoint metadata
        MethodParameter fallbackParam = getMethodParameter("fallbackDeprecatedMethod");
        HttpHeaders headersFallback = new HttpHeaders();
        ServerHttpResponse responseFallback = mock(ServerHttpResponse.class);
        when(responseFallback.getHeaders()).thenReturn(headersFallback);
        advice.beforeBodyWrite(createItems(10), fallbackParam, MediaType.APPLICATION_JSON, null, null, responseFallback);
        assertNull(headersFallback.getFirst("Deprecation"), "Must not invent deprecation date from Java @Deprecated");
    }

    // =========================================================================
    // LIMIT 50 BOUNDARY TESTS (49, 50, 51)
    // =========================================================================

    @Test
    @DisplayName("Limit 50: 49 rows available -> complete, truncated=false, no warning")
    void testLimit50With49Rows() throws NoSuchMethodException {
        MethodParameter param = getMethodParameter("limit50Method");
        List<String> items = createItems(49);

        HttpHeaders headers = new HttpHeaders();
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        when(response.getHeaders()).thenReturn(headers);

        Object result = advice.beforeBodyWrite(items, param, MediaType.APPLICATION_JSON, null, null, response);

        assertEquals("49", headers.getFirst("X-Result-Count"));
        assertEquals("50", headers.getFirst("X-Result-Limit"));
        assertEquals("false", headers.getFirst("X-Result-Truncated"));
        assertNull(headers.getFirst("Warning"), "No Warning header should be emitted for complete results");
        assertEquals("</api/v1/sample/limit50/paged>; rel=\"alternate\"", headers.getFirst("Link"));
        assertInstanceOf(List.class, result);
        assertEquals(49, ((List<?>) result).size());
    }

    @Test
    @DisplayName("Limit 50: Exactly 50 rows available -> complete, truncated=false, no warning (no false truncation)")
    void testLimit50With50Rows() throws NoSuchMethodException {
        MethodParameter param = getMethodParameter("limit50Method");
        List<String> items = createItems(50);

        HttpHeaders headers = new HttpHeaders();
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        when(response.getHeaders()).thenReturn(headers);

        Object result = advice.beforeBodyWrite(items, param, MediaType.APPLICATION_JSON, null, null, response);

        assertEquals("50", headers.getFirst("X-Result-Count"));
        assertEquals("50", headers.getFirst("X-Result-Limit"));
        assertEquals("false", headers.getFirst("X-Result-Truncated"), "Exactly 50 rows must not be reported as truncated");
        assertNull(headers.getFirst("Warning"), "No Warning header should be emitted when not truncated");
        assertEquals("</api/v1/sample/limit50/paged>; rel=\"alternate\"", headers.getFirst("Link"));
        assertInstanceOf(List.class, result);
        assertEquals(50, ((List<?>) result).size());
    }

    @Test
    @DisplayName("Limit 50: 51 rows fetched (limit+1) -> truncated=true, trimmed to 50, no obsolete Warning header")
    void testLimit50With51Rows() throws NoSuchMethodException {
        MethodParameter param = getMethodParameter("limit50Method");
        List<String> items = createItems(51);

        HttpHeaders headers = new HttpHeaders();
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        when(response.getHeaders()).thenReturn(headers);

        Object result = advice.beforeBodyWrite(items, param, MediaType.APPLICATION_JSON, null, null, response);

        assertEquals("50", headers.getFirst("X-Result-Count"), "Client must receive exactly limit (50) items");
        assertEquals("50", headers.getFirst("X-Result-Limit"));
        assertEquals("true", headers.getFirst("X-Result-Truncated"), "Must report truncated when extra row exists");
        assertNull(headers.getFirst("Warning"), "Warning header was obsoleted by RFC 9111 and must not be emitted");
        assertEquals("</api/v1/sample/limit50/paged>; rel=\"alternate\"", headers.getFirst("Link"));
        assertInstanceOf(List.class, result);
        assertEquals(50, ((List<?>) result).size(), "Returned body must be trimmed to 50");
    }

    // =========================================================================
    // LIMIT 100 BOUNDARY TESTS (99, 100, 101)
    // =========================================================================

    @Test
    @DisplayName("Limit 100: 99 rows available -> complete, truncated=false, no warning")
    void testLimit100With99Rows() throws NoSuchMethodException {
        MethodParameter param = getMethodParameter("limit100Method");
        List<String> items = createItems(99);

        HttpHeaders headers = new HttpHeaders();
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        when(response.getHeaders()).thenReturn(headers);

        Object result = advice.beforeBodyWrite(items, param, MediaType.APPLICATION_JSON, null, null, response);

        assertEquals("99", headers.getFirst("X-Result-Count"));
        assertEquals("100", headers.getFirst("X-Result-Limit"));
        assertEquals("false", headers.getFirst("X-Result-Truncated"));
        assertNull(headers.getFirst("Warning"));
        assertEquals("</api/v1/sample/limit100/paged>; rel=\"alternate\"", headers.getFirst("Link"));
        assertEquals(99, ((List<?>) result).size());
    }

    @Test
    @DisplayName("Limit 100: Exactly 100 rows available -> complete, truncated=false, no warning")
    void testLimit100With100Rows() throws NoSuchMethodException {
        MethodParameter param = getMethodParameter("limit100Method");
        List<String> items = createItems(100);

        HttpHeaders headers = new HttpHeaders();
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        when(response.getHeaders()).thenReturn(headers);

        Object result = advice.beforeBodyWrite(items, param, MediaType.APPLICATION_JSON, null, null, response);

        assertEquals("100", headers.getFirst("X-Result-Count"));
        assertEquals("100", headers.getFirst("X-Result-Limit"));
        assertEquals("false", headers.getFirst("X-Result-Truncated"));
        assertNull(headers.getFirst("Warning"));
        assertEquals(100, ((List<?>) result).size());
    }

    @Test
    @DisplayName("Limit 100: 101 rows fetched (limit+1) -> truncated=true, trimmed to 100, no obsolete Warning header")
    void testLimit100With101Rows() throws NoSuchMethodException {
        MethodParameter param = getMethodParameter("limit100Method");
        List<String> items = createItems(101);

        HttpHeaders headers = new HttpHeaders();
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        when(response.getHeaders()).thenReturn(headers);

        Object result = advice.beforeBodyWrite(items, param, MediaType.APPLICATION_JSON, null, null, response);

        assertEquals("100", headers.getFirst("X-Result-Count"));
        assertEquals("100", headers.getFirst("X-Result-Limit"));
        assertEquals("true", headers.getFirst("X-Result-Truncated"));
        assertNull(headers.getFirst("Warning"), "Warning header was obsoleted by RFC 9111 and must not be emitted");
        assertEquals(100, ((List<?>) result).size());
    }

    // =========================================================================
    // LIMIT 500 BOUNDARY TESTS (499, 500, 501)
    // =========================================================================

    @Test
    @DisplayName("Limit 500: 499 rows available -> complete, truncated=false, no warning")
    void testLimit500With499Rows() throws NoSuchMethodException {
        MethodParameter param = getMethodParameter("limit500Method");
        List<String> items = createItems(499);

        HttpHeaders headers = new HttpHeaders();
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        when(response.getHeaders()).thenReturn(headers);

        Object result = advice.beforeBodyWrite(items, param, MediaType.APPLICATION_JSON, null, null, response);

        assertEquals("499", headers.getFirst("X-Result-Count"));
        assertEquals("500", headers.getFirst("X-Result-Limit"));
        assertEquals("false", headers.getFirst("X-Result-Truncated"));
        assertNull(headers.getFirst("Warning"));
        assertEquals(499, ((List<?>) result).size());
    }

    @Test
    @DisplayName("Limit 500: Exactly 500 rows available -> complete, truncated=false, no warning")
    void testLimit500With500Rows() throws NoSuchMethodException {
        MethodParameter param = getMethodParameter("limit500Method");
        List<String> items = createItems(500);

        HttpHeaders headers = new HttpHeaders();
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        when(response.getHeaders()).thenReturn(headers);

        Object result = advice.beforeBodyWrite(items, param, MediaType.APPLICATION_JSON, null, null, response);

        assertEquals("500", headers.getFirst("X-Result-Count"));
        assertEquals("500", headers.getFirst("X-Result-Limit"));
        assertEquals("false", headers.getFirst("X-Result-Truncated"));
        assertNull(headers.getFirst("Warning"));
        assertEquals(500, ((List<?>) result).size());
    }

    @Test
    @DisplayName("Limit 500: 501 rows fetched (limit+1) -> truncated=true, trimmed to 500, no obsolete Warning header")
    void testLimit500With501Rows() throws NoSuchMethodException {
        MethodParameter param = getMethodParameter("limit500Method");
        List<String> items = createItems(501);

        HttpHeaders headers = new HttpHeaders();
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        when(response.getHeaders()).thenReturn(headers);

        Object result = advice.beforeBodyWrite(items, param, MediaType.APPLICATION_JSON, null, null, response);

        assertEquals("500", headers.getFirst("X-Result-Count"));
        assertEquals("500", headers.getFirst("X-Result-Limit"));
        assertEquals("true", headers.getFirst("X-Result-Truncated"));
        assertNull(headers.getFirst("Warning"), "Warning header was obsoleted by RFC 9111 and must not be emitted");
        assertEquals(500, ((List<?>) result).size());
    }

    // =========================================================================
    // LIMIT INDEPENDENCE: LIMIT NOT INFERRED FROM RESULT SIZE
    // =========================================================================

    @Test
    @DisplayName("Limit 500 endpoint returning 2 rows reports limit 500, NOT 50")
    void testLimit500NotDowngradedOnSmallResult() throws NoSuchMethodException {
        MethodParameter param = getMethodParameter("limit500Method");
        List<String> items = createItems(2);

        HttpHeaders headers = new HttpHeaders();
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        when(response.getHeaders()).thenReturn(headers);

        advice.beforeBodyWrite(items, param, MediaType.APPLICATION_JSON, null, null, response);

        assertEquals("2", headers.getFirst("X-Result-Count"));
        assertEquals("500", headers.getFirst("X-Result-Limit"), "Safety limit must remain 500, not inferred as 50");
        assertEquals("false", headers.getFirst("X-Result-Truncated"));
        assertNull(headers.getFirst("Warning"));
    }

    @Test
    @DisplayName("Limit 500 endpoint returning 51 rows reports limit 500, NOT 50, and truncated=false")
    void testLimit500With51RowsNotFalseTruncated() throws NoSuchMethodException {
        MethodParameter param = getMethodParameter("limit500Method");
        List<String> items = createItems(51);

        HttpHeaders headers = new HttpHeaders();
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        when(response.getHeaders()).thenReturn(headers);

        advice.beforeBodyWrite(items, param, MediaType.APPLICATION_JSON, null, null, response);

        assertEquals("51", headers.getFirst("X-Result-Count"));
        assertEquals("500", headers.getFirst("X-Result-Limit"));
        assertEquals("false", headers.getFirst("X-Result-Truncated"), "51 rows on limit 500 is NOT truncated");
        assertNull(headers.getFirst("Warning"));
    }

    @Test
    @DisplayName("Limit 500 endpoint returning 100 rows reports limit 500, NOT 100, and truncated=false")
    void testLimit500With100RowsNotFalseTruncated() throws NoSuchMethodException {
        MethodParameter param = getMethodParameter("limit500Method");
        List<String> items = createItems(100);

        HttpHeaders headers = new HttpHeaders();
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        when(response.getHeaders()).thenReturn(headers);

        advice.beforeBodyWrite(items, param, MediaType.APPLICATION_JSON, null, null, response);

        assertEquals("100", headers.getFirst("X-Result-Count"));
        assertEquals("500", headers.getFirst("X-Result-Limit"));
        assertEquals("false", headers.getFirst("X-Result-Truncated"), "100 rows on limit 500 is NOT truncated");
        assertNull(headers.getFirst("Warning"));
    }

    @Test
    @DisplayName("Limit 100 endpoint returning 75 rows reports limit 100, NOT 50, and truncated=false")
    void testLimit100With75RowsReportsLimit100() throws NoSuchMethodException {
        MethodParameter param = getMethodParameter("limit100Method");
        List<String> items = createItems(75);

        HttpHeaders headers = new HttpHeaders();
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        when(response.getHeaders()).thenReturn(headers);

        advice.beforeBodyWrite(items, param, MediaType.APPLICATION_JSON, null, null, response);

        assertEquals("75", headers.getFirst("X-Result-Count"));
        assertEquals("100", headers.getFirst("X-Result-Limit"), "Safety limit must remain 100, not inferred as 50");
        assertEquals("false", headers.getFirst("X-Result-Truncated"));
        assertNull(headers.getFirst("Warning"));
    }

    // =========================================================================
    // LINK HEADER & NON-PAGED ENDPOINT BEHAVIOR
    // =========================================================================

    @Test
    @DisplayName("Endpoint without pagedUrl emits NO Link header")
    void testEndpointWithoutPagedRouteEmitsNoLink() throws NoSuchMethodException {
        MethodParameter param = getMethodParameter("limit50NoPagedRouteMethod");
        List<String> items = createItems(10);

        HttpHeaders headers = new HttpHeaders();
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        when(response.getHeaders()).thenReturn(headers);

        advice.beforeBodyWrite(items, param, MediaType.APPLICATION_JSON, null, null, response);

        assertNull(headers.getFirst("Link"), "Endpoints without a replacement paged route must not emit a Link header");
    }

    @Test
    @DisplayName("Parameterized pagedUrl resolves against incoming request path")
    void testParameterizedPagedRouteResolvesFromRequest() throws NoSuchMethodException {
        MethodParameter param = getMethodParameter("parameterizedPagedRouteMethod");
        List<String> items = createItems(10);

        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getURI()).thenReturn(URI.create("http://localhost:8080/api/v1/cash-registers/sessions/sess-123/movements"));

        HttpHeaders headers = new HttpHeaders();
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        when(response.getHeaders()).thenReturn(headers);

        advice.beforeBodyWrite(items, param, MediaType.APPLICATION_JSON, null, request, response);

        assertEquals(
                "</api/v1/cash-registers/sessions/sess-123/movements/paged>; rel=\"alternate\"",
                headers.getFirst("Link")
        );
    }
}
