package com.autoparts.erp.ai.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import com.autoparts.erp.ai.service.AiPerformanceMetrics;

@ExtendWith(MockitoExtension.class)
class ProductLookupToolComparisonTest {

    @Mock NamedParameterJdbcTemplate jdbcTemplate;
    ProductLookupTool tool;

    @BeforeEach
    void setUp() {
        tool = new ProductLookupTool(
                jdbcTemplate,
                new AiPerformanceMetrics());
    }

    @Test
    void performsExactParameterizedLookupForAllRequestedProducts() {
        List<String> identifiers = List.of("GAP-000001", "GAP-000002");
        List<Map<String, Object>> expected = List.of(
                Map.of("product_code", "GAP-000001"),
                Map.of("product_code", "GAP-000002"));
        when(jdbcTemplate.queryForList(anyString(), any(SqlParameterSource.class))).thenReturn(expected);

        assertEquals(expected, tool.getProductsByIdentifiers(identifiers));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> args = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbcTemplate).queryForList(sql.capture(), args.capture());
        assertEquals(4, sql.getValue().split(":").length - 1);
        MapSqlParameterSource parameters = (MapSqlParameterSource) args.getValue();
        assertEquals("gap-000001", parameters.getValue("product0"));
        assertEquals("gap-000002", parameters.getValue("product1"));
        assertEquals("gap-000001", parameters.getValue("partNumber0"));
        assertEquals("gap-000002", parameters.getValue("partNumber1"));
    }

    @Test
    void rejectsComparisonWithFewerThanTwoIdentifiers() {
        assertThrows(IllegalArgumentException.class,
                () -> tool.getProductsByIdentifiers(List.of("GAP-000001")));
    }

    @Test
    void rejectsMoreThanFiveIdentifiers() {
        assertThrows(IllegalArgumentException.class,
                () -> tool.getProductsByIdentifiers(
                        List.of("A-001", "A-002", "A-003", "A-004", "A-005", "A-006")));
    }
}
