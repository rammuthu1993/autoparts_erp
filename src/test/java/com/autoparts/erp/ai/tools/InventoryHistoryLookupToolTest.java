package com.autoparts.erp.ai.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

@ExtendWith(MockitoExtension.class)
class InventoryHistoryLookupToolTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Test
    void bindsProductAndDateRangeUsingNamedParameters() {
        InventoryHistoryLookupTool tool =
                new InventoryHistoryLookupTool(jdbcTemplate);
        when(jdbcTemplate.queryForList(
                anyString(),
                any(SqlParameterSource.class)
        )).thenReturn(List.of());

        tool.getProductMovementsByDateRange(
                " GAP-000001 ",
                "2025-03-01",
                "2025-03-02"
        );

        ArgumentCaptor<String> sql =
                ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> parameters =
                ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbcTemplate).queryForList(sql.capture(), parameters.capture());

        assertFalse(sql.getValue().contains("?"));
        MapSqlParameterSource values =
                (MapSqlParameterSource) parameters.getValue();
        assertEquals("GAP-000001", values.getValue("productCode"));
        assertEquals("GAP-000001", values.getValue("partNumber"));
        assertEquals(
                OffsetDateTime.parse("2025-03-01T00:00+05:30"),
                values.getValue("startDate")
        );
        assertEquals(
                OffsetDateTime.parse("2025-03-03T00:00+05:30"),
                values.getValue("endDate")
        );
    }

    @Test
    void bindsProductAndLimitForFilteredAdjustmentHistory() {
        InventoryHistoryLookupTool tool =
                new InventoryHistoryLookupTool(jdbcTemplate);
        when(jdbcTemplate.queryForList(
                anyString(),
                any(SqlParameterSource.class)
        )).thenReturn(List.of());

        tool.getInventoryAdjustments("GAP-000001", 100);

        ArgumentCaptor<String> sql =
                ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> parameters =
                ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbcTemplate).queryForList(sql.capture(), parameters.capture());

        assertFalse(sql.getValue().contains("?"));
        MapSqlParameterSource values =
                (MapSqlParameterSource) parameters.getValue();
        assertEquals("GAP-000001", values.getValue("productCode"));
        assertEquals("GAP-000001", values.getValue("partNumber"));
        assertEquals(50, values.getValue("limit"));
    }
}
