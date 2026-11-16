package com.autoparts.erp.ai.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class ProductLookupToolComparisonTest {

    @Mock JdbcTemplate jdbcTemplate;
    @InjectMocks ProductLookupTool tool;

    @Test
    void performsExactParameterizedLookupForAllRequestedProducts() {
        List<String> identifiers = List.of("GAP-000001", "GAP-000002");
        List<Map<String, Object>> expected = List.of(
                Map.of("product_code", "GAP-000001"),
                Map.of("product_code", "GAP-000002"));
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(expected);

        assertEquals(expected, tool.getProductsByIdentifiers(identifiers));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForList(sql.capture(), args.capture());
        assertEquals(4, sql.getValue().split("\\?").length - 1);
        assertEquals(List.of("gap-000001", "gap-000002", "gap-000001", "gap-000002"),
                java.util.Arrays.asList(args.getValue()));
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
