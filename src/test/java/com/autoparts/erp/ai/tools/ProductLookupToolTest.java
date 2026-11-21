
package com.autoparts.erp.ai.tools;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.autoparts.erp.ai.security.ErpAiContextHolder;
import com.autoparts.erp.ai.security.ErpAiExecutionContext;

@ExtendWith(MockitoExtension.class)
class ProductLookupToolTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private ProductLookupTool productLookupTool;

    @BeforeEach
    void setUp() {
        productLookupTool = new ProductLookupTool(jdbcTemplate);
        ErpAiContextHolder.setContext(ErpAiExecutionContext.create(
                UUID.randomUUID(), "test-user", List.of("ROLE_ADMIN")));
    }

    @AfterEach
    void tearDown() {
        ErpAiContextHolder.clear();
    }

    @Test
    void shouldStopExecutionWhenThreadIsInterrupted() {
        Thread.currentThread().interrupt();

        try {
            assertThrows(
                    CancellationException.class,
                    () -> productLookupTool.countProducts()
            );

            verifyNoInteractions(jdbcTemplate);

        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void shouldSearchProductsUsingProductCodePartNumberAndName() {
        Map<String, Object> product = Map.of(
                "uuid", UUID.randomUUID(),
                "product_code", "PRD001",
                "part_no", "FILTER-001",
                "name", "Oil Filter",
                "qty", 12,
                "unit", "PCS"
        );

        when(jdbcTemplate.queryForList(
                anyString(),
                any(Object[].class)
        )).thenReturn(List.of(product));

        List<Map<String, Object>> result =
                productLookupTool.searchProducts("PRD001");

        assertEquals(1, result.size());
        assertEquals("PRD001", result.get(0).get("product_code"));

        ArgumentCaptor<String> sqlCaptor =
                forClass(String.class);

        ArgumentCaptor<Object[]> argumentsCaptor =
                forClass(Object[].class);

        verify(jdbcTemplate).queryForList(
                sqlCaptor.capture(),
                argumentsCaptor.capture()
        );

        String sql = normalizeSql(sqlCaptor.getValue());

        assertTrue(sql.contains("where active = true"));
        assertTrue(sql.contains("lower(product_code) like lower(?)"));
        assertTrue(sql.contains("lower(part_no) like lower(?)"));
        assertTrue(sql.contains("lower(name) like lower(?)"));
        assertTrue(sql.contains("lower(description) like lower(?)"));

        assertArrayEquals(
                new Object[] {
                        "%PRD001%",
                        "%PRD001%",
                        "%PRD001%",
                        "%PRD001%"
                },
                argumentsCaptor.getValue()
        );
    }

    @Test
    void shouldReturnEmptyListForBlankSearchText() {
        List<Map<String, Object>> result =
                productLookupTool.searchProducts(" ");

        assertTrue(result.isEmpty());
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void shouldCountActiveProducts() {
        when(jdbcTemplate.queryForObject(
                anyString(),
                eq(Long.class)
        )).thenReturn(25L);

        Long result = productLookupTool.countProducts();

        assertEquals(25L, result);

        ArgumentCaptor<String> sqlCaptor =
                forClass(String.class);

        verify(jdbcTemplate).queryForObject(
                sqlCaptor.capture(),
                eq(Long.class)
        );

        String sql = normalizeSql(sqlCaptor.getValue());

        assertTrue(sql.contains("select count(*)"));
        assertTrue(sql.contains("from products"));
        assertTrue(sql.contains("where active = true"));
    }

    @Test
    void shouldReturnZeroWhenNoActiveProductsExist() {
        when(jdbcTemplate.queryForObject(
                anyString(),
                eq(Long.class)
        )).thenReturn(0L);

        assertEquals(0L, productLookupTool.countProducts());
    }

    @Test
    void shouldRetrieveProductDetailsUsingExactProductCodeOrPartNumber() {
        Map<String, Object> product = Map.of(
                "product_code", "PRD001",
                "part_no", "FILTER-001",
                "name", "Oil Filter",
                "qty", 12
        );

        when(jdbcTemplate.queryForList(
                anyString(),
                any(Object[].class)
        )).thenReturn(List.of(product));

        List<Map<String, Object>> result =
                productLookupTool.getProductDetails("PRD001");

        assertEquals(1, result.size());
        assertEquals("Oil Filter", result.get(0).get("name"));

        ArgumentCaptor<String> sqlCaptor =
                forClass(String.class);

        ArgumentCaptor<Object[]> argumentsCaptor =
                forClass(Object[].class);

        verify(jdbcTemplate).queryForList(
                sqlCaptor.capture(),
                argumentsCaptor.capture()
        );

        String sql = normalizeSql(sqlCaptor.getValue());

        assertTrue(sql.contains("where lower(product_code) = lower(?)"));
        assertTrue(sql.contains("or lower(part_no) = lower(?)"));

        assertArrayEquals(
                new Object[]{"PRD001", "PRD001"},
                argumentsCaptor.getValue()
        );
    }

    @Test
    void shouldNotQueryDatabaseForBlankProductDetailsSearch() {
        List<Map<String, Object>> result =
                productLookupTool.getProductDetails(" ");

        assertTrue(result.isEmpty());
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void shouldRetrieveLowStockProducts() {
        Map<String, Object> product = Map.of(
                "product_code", "PRD001",
                "part_no", "FILTER-001",
                "name", "Oil Filter",
                "qty", 2,
                "minimum_stock", 5,
                "unit", "PCS"
        );

        when(jdbcTemplate.queryForList(anyString()))
                .thenReturn(List.of(product));

        List<Map<String, Object>> result =
                productLookupTool.getLowStockProducts();

        assertEquals(1, result.size());
        assertEquals("PRD001", result.get(0).get("product_code"));

        ArgumentCaptor<String> sqlCaptor =
                forClass(String.class);

        verify(jdbcTemplate).queryForList(sqlCaptor.capture());

        String sql = normalizeSql(sqlCaptor.getValue());

        assertTrue(sql.contains("where active = true"));
        assertTrue(sql.contains("qty <= minimum_stock"));
        assertTrue(sql.contains("order by qty asc"));
    }

    @Test
    void shouldRetrieveProductStockUsingExactProductCodeOrPartNumber() {
        Map<String, Object> product = Map.of(
                "product_code", "PRD001",
                "part_no", "FILTER-001",
                "name", "Oil Filter",
                "qty", 12,
                "minimum_stock", 5,
                "unit", "PCS"
        );

        when(jdbcTemplate.queryForList(
                anyString(),
                any(Object[].class)
        )).thenReturn(List.of(product));

        List<Map<String, Object>> result =
                productLookupTool.getProductStock("PRD001");

        assertEquals(1, result.size());
        assertEquals(12, result.get(0).get("qty"));

        ArgumentCaptor<String> sqlCaptor =
                forClass(String.class);

        ArgumentCaptor<Object[]> argumentsCaptor =
                forClass(Object[].class);

        verify(jdbcTemplate).queryForList(
                sqlCaptor.capture(),
                argumentsCaptor.capture()
        );

        String sql = normalizeSql(sqlCaptor.getValue());

        assertTrue(sql.contains("where lower(product_code) = lower(?)"));
        assertTrue(sql.contains("or lower(part_no) = lower(?)"));

        assertArrayEquals(
                new Object[]{"PRD001", "PRD001"},
                argumentsCaptor.getValue()
        );
    }

    @Test
    void shouldNotQueryDatabaseForBlankStockSearch() {
        List<Map<String, Object>> result =
                productLookupTool.getProductStock(" ");

        assertTrue(result.isEmpty());
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void shouldExposeSearchDatabaseFailureAsIllegalStateException() {
        when(jdbcTemplate.queryForList(
                anyString(),
                any(Object[].class)
        )).thenThrow(
                new DataAccessResourceFailureException(
                        "Database unavailable"
                )
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> productLookupTool.searchProducts("PRD001")
        );

        assertEquals(
                "Unable to search products at this time.",
                exception.getMessage()
        );

        assertInstanceOf(
                DataAccessResourceFailureException.class,
                exception.getCause()
        );
    }

    @Test
    void shouldExposeCountDatabaseFailureAsIllegalStateException() {
        when(jdbcTemplate.queryForObject(
                anyString(),
                eq(Long.class)
        )).thenThrow(
                new DataAccessResourceFailureException(
                        "Database unavailable"
                )
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> productLookupTool.countProducts()
        );

        assertEquals(
                "Unable to retrieve the product count at this time.",
                exception.getMessage()
        );
    }

    @Test
    void shouldExposeProductDetailsDatabaseFailure() {
        when(jdbcTemplate.queryForList(
                anyString(),
                any(Object[].class)
        )).thenThrow(
                new DataAccessResourceFailureException(
                        "Database unavailable"
                )
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> productLookupTool.getProductDetails("PRD001")
        );

        assertEquals(
                "Unable to retrieve product details at this time.",
                exception.getMessage()
        );
    }

    @Test
    void shouldExposeLowStockDatabaseFailure() {
        when(jdbcTemplate.queryForList(anyString()))
                .thenThrow(
                        new DataAccessResourceFailureException(
                                "Database unavailable"
                        )
                );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> productLookupTool.getLowStockProducts()
        );

        assertEquals(
                "Unable to retrieve low-stock products at this time.",
                exception.getMessage()
        );
    }

    @Test
    void shouldExposeProductStockDatabaseFailure() {
        when(jdbcTemplate.queryForList(
                anyString(),
                any(Object[].class)
        )).thenThrow(
                new DataAccessResourceFailureException(
                        "Database unavailable"
                )
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> productLookupTool.getProductStock("PRD001")
        );

        assertEquals(
                "Unable to retrieve product stock at this time.",
                exception.getMessage()
        );
    }

    @Test
    void shouldExposeSearchProductsAsSpringAiTool() {
        Map<String, Object> product = Map.of(
                "product_code", "PRD001",
                "part_no", "FILTER-001",
                "name", "Oil Filter",
                "qty", 12
        );

        when(jdbcTemplate.queryForList(
                anyString(),
                any(Object[].class)
        )).thenReturn(List.of(product));

        ToolCallback callback = java.util.Arrays.stream(
                        ToolCallbacks.from(productLookupTool)
                )
                .filter(tool ->
                        tool.getToolDefinition()
                                .name()
                                .equals("searchProducts")
                )
                .findFirst()
                .orElseThrow();

        String schema = callback.getToolDefinition().inputSchema();

        assertTrue(schema.contains("searchText"));

        String result = callback.call(
                "{\"searchText\":\"PRD001\"}"
        );

        assertTrue(result.contains("\"product_code\":\"PRD001\""));
        assertTrue(result.contains("\"name\":\"Oil Filter\""));
    }

    private String normalizeSql(String sql) {
        return sql.replaceAll("\\s+", " ")
                .trim()
                .toLowerCase();
    }
}
