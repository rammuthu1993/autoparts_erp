package com.autoparts.erp.audit.controller;

import com.autoparts.erp.audit.model.AuditLog;
import com.autoparts.erp.audit.service.AuditLogService;
import com.autoparts.erp.common.advice.DeprecatedUnpagedApiAdvice;
import com.autoparts.erp.common.dto.PageResponse;
import com.autoparts.erp.common.exception.ClientValidationException;
import com.autoparts.erp.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditLogController: Real Spring MVC Binding, Limit Fallback, and Response Header Integration Tests")
class AuditLogControllerTest {

    @Mock
    private AuditLogService auditLogService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AuditLogController controller = new AuditLogController(auditLogService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new DeprecatedUnpagedApiAdvice(), new GlobalExceptionHandler())
                .build();
    }

    private List<AuditLog> createLogs(int count) {
        List<AuditLog> logs = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            logs.add(new AuditLog(
                    (long) i,
                    "uuid-" + i,
                    1L,
                    "admin",
                    "UPDATE",
                    "PRODUCT",
                    "PRD-" + i,
                    "Updated description " + i,
                    "{\"price\":100}",
                    "{\"price\":120}",
                    Instant.parse("2026-11-22T10:00:00Z")
            ));
        }
        return logs;
    }

    // =========================================================================
    // ISSUE 1: PARAMETER BINDING AND GRACEFUL FALLBACK (Spring MVC MockMvc)
    // =========================================================================

    @Test
    @DisplayName("Issue 1: Missing limit parameter defaults to 50, calls service with 50, returns 200 OK")
    void testGetRecentLogsWithMissingLimit() throws Exception {
        when(auditLogService.getRecentLogs(eq(50), eq(0))).thenReturn(createLogs(20));

        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Result-Count", "20"))
                .andExpect(header().string("X-Result-Limit", "50"))
                .andExpect(header().string("X-Result-Truncated", "false"))
                .andExpect(header().string("Deprecation", "@1795305600"))
                .andExpect(header().string("Link", "</api/v1/admin/audit-logs/paged>; rel=\"alternate\""))
                .andExpect(header().doesNotExist("Warning"))
                .andExpect(jsonPath("$", hasSize(20)));

        verify(auditLogService).getRecentLogs(50, 0);
    }

    @Test
    @DisplayName("Issue 1: Non-numeric limit (limit=abc) gracefully falls back to 50 instead of 400 Bad Request")
    void testGetRecentLogsWithNonNumericLimitGracefulFallback() throws Exception {
        when(auditLogService.getRecentLogs(eq(50), eq(0))).thenReturn(createLogs(25));

        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .param("limit", "abc")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Result-Count", "25"))
                .andExpect(header().string("X-Result-Limit", "50"))
                .andExpect(header().string("X-Result-Truncated", "false"))
                .andExpect(header().string("Deprecation", "@1795305600"))
                .andExpect(header().string("Link", "</api/v1/admin/audit-logs/paged>; rel=\"alternate\""))
                .andExpect(header().doesNotExist("Warning"))
                .andExpect(jsonPath("$", hasSize(25)));

        verify(auditLogService).getRecentLogs(50, 0);
    }

    @Test
    @DisplayName("Issue 1: Zero limit (limit=0) falls back to default 50")
    void testGetRecentLogsWithZeroLimit() throws Exception {
        when(auditLogService.getRecentLogs(eq(50), eq(0))).thenReturn(createLogs(10));

        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .param("limit", "0")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Result-Count", "10"))
                .andExpect(header().string("X-Result-Limit", "50"))
                .andExpect(header().string("X-Result-Truncated", "false"))
                .andExpect(header().doesNotExist("Warning"))
                .andExpect(jsonPath("$", hasSize(10)));

        verify(auditLogService).getRecentLogs(50, 0);
    }

    @Test
    @DisplayName("Issue 1: Negative limit (limit=-5) falls back to default 50")
    void testGetRecentLogsWithNegativeLimit() throws Exception {
        when(auditLogService.getRecentLogs(eq(50), eq(0))).thenReturn(createLogs(15));

        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .param("limit", "-5")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Result-Count", "15"))
                .andExpect(header().string("X-Result-Limit", "50"))
                .andExpect(header().string("X-Result-Truncated", "false"))
                .andExpect(header().doesNotExist("Warning"))
                .andExpect(jsonPath("$", hasSize(15)));

        verify(auditLogService).getRecentLogs(50, 0);
    }

    @Test
    @DisplayName("Issue 1: Configurable valid limit (limit=20) calls service with 20 and reports limit 20")
    void testGetRecentLogsWithValidLimit20() throws Exception {
        when(auditLogService.getRecentLogs(eq(20), eq(0))).thenReturn(createLogs(20));

        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .param("limit", "20")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Result-Count", "20"))
                .andExpect(header().string("X-Result-Limit", "20"))
                .andExpect(header().string("X-Result-Truncated", "false"))
                .andExpect(header().doesNotExist("Warning"))
                .andExpect(jsonPath("$", hasSize(20)));

        verify(auditLogService).getRecentLogs(20, 0);
    }

    @Test
    @DisplayName("Issue 1: Valid limit 50 calls service with 50 and reports limit 50")
    void testGetRecentLogsWithValidLimit50() throws Exception {
        when(auditLogService.getRecentLogs(eq(50), eq(0))).thenReturn(createLogs(50));

        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .param("limit", "50")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Result-Count", "50"))
                .andExpect(header().string("X-Result-Limit", "50"))
                .andExpect(header().string("X-Result-Truncated", "false"))
                .andExpect(header().doesNotExist("Warning"))
                .andExpect(jsonPath("$", hasSize(50)));

        verify(auditLogService).getRecentLogs(50, 0);
    }

    @Test
    @DisplayName("Issue 1: Exceeding ceiling (limit=75) is clamped to fixed ceiling 50")
    void testGetRecentLogsWithLimit75ClampedTo50() throws Exception {
        when(auditLogService.getRecentLogs(eq(50), eq(0))).thenReturn(createLogs(50));

        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .param("limit", "75")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Result-Count", "50"))
                .andExpect(header().string("X-Result-Limit", "50"))
                .andExpect(header().string("X-Result-Truncated", "false"))
                .andExpect(header().doesNotExist("Warning"))
                .andExpect(jsonPath("$", hasSize(50)));

        verify(auditLogService).getRecentLogs(50, 0);
    }

    @Test
    @DisplayName("Issue 1: Exceeding ceiling (limit=100) is clamped to fixed ceiling 50")
    void testGetRecentLogsWithLimit100ClampedTo50() throws Exception {
        when(auditLogService.getRecentLogs(eq(50), eq(0))).thenReturn(createLogs(50));

        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .param("limit", "100")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Result-Count", "50"))
                .andExpect(header().string("X-Result-Limit", "50"))
                .andExpect(header().string("X-Result-Truncated", "false"))
                .andExpect(header().doesNotExist("Warning"))
                .andExpect(jsonPath("$", hasSize(50)));

        verify(auditLogService).getRecentLogs(50, 0);
    }

    @Test
    @DisplayName("Issue 1 & 3: Limit 50 with 51 rows returned from repository -> trims to 50, truncated=true, NO Warning header")
    void testGetRecentLogsTruncatedTrimsTo50AndEmitsTruncationHeaders() throws Exception {
        when(auditLogService.getRecentLogs(eq(50), eq(0))).thenReturn(createLogs(51));

        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Result-Count", "50"))
                .andExpect(header().string("X-Result-Limit", "50"))
                .andExpect(header().string("X-Result-Truncated", "true"))
                .andExpect(header().string("Deprecation", "@1795305600"))
                .andExpect(header().string("Link", "</api/v1/admin/audit-logs/paged>; rel=\"alternate\""))
                .andExpect(header().doesNotExist("Warning"))
                .andExpect(jsonPath("$", hasSize(50)));

        verify(auditLogService).getRecentLogs(50, 0);
    }

    @Test
    @DisplayName("Dedicated /paged route returns PageResponse and does NOT emit legacy headers")
    void testFindPagedReturnsPageResponseWithoutLegacyHeaders() throws Exception {
        PageResponse<AuditLog> pageResponse = PageResponse.of(createLogs(20), 0, 20, 100);
        when(auditLogService.findPaged(eq(0), eq(20), isNull(), isNull(), isNull(), eq("createdAt"), eq("desc")))
                .thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/admin/audit-logs/paged")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("X-Result-Count"))
                .andExpect(header().doesNotExist("X-Result-Limit"))
                .andExpect(header().doesNotExist("X-Result-Truncated"))
                .andExpect(header().doesNotExist("Warning"))
                .andExpect(jsonPath("$.content", hasSize(20)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(100))
                .andExpect(jsonPath("$.totalPages").value(5));

        verify(auditLogService).findPaged(0, 20, null, null, null, "createdAt", "desc");
    }

    @Test
    @DisplayName("Overflowing page parameter results in HTTP 400 Bad Request with client validation message")
    void testFindPagedWithIntegerOverflowThrows400BadRequest() throws Exception {
        when(auditLogService.findPaged(eq(Integer.MAX_VALUE), eq(20), any(), any(), any(), any(), any()))
                .thenThrow(new ClientValidationException("Pagination offset exceeds maximum supported limit (42949672940 > 2147483647)"));

        mockMvc.perform(get("/api/v1/admin/audit-logs/paged")
                        .param("page", String.valueOf(Integer.MAX_VALUE))
                        .param("size", "20")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Pagination offset exceeds maximum supported limit (42949672940 > 2147483647)"));
    }
}
