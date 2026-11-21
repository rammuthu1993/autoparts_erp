package com.autoparts.erp.ai.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import com.autoparts.erp.ai.tools.ProductLookupTool;

class ErpAiSqlInjectionDefenseTest {

    @Test
    void productIdentifierSearchKeepsUntrustedTextInJdbcParameters() {
        JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
        ProductLookupTool tool = new ProductLookupTool(jdbcTemplate);
        ErpAiExecutionContext context = ErpAiExecutionContext.create(
                UUID.randomUUID(), "security-test-user", List.of("ROLE_ADMIN"));

        String attack = "gap-000001' or '1'='1' --";
        tool.getProductsByIdentifiers(List.of(attack, "GAP-000002"), context);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> parameters = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForList(sql.capture(), parameters.capture());

        assertFalse(sql.getValue().contains(attack), "Untrusted identifier must never be interpolated into SQL");
        assertTrue(sql.getValue().contains("IN (?,?)"), "Query must use parameter placeholders");
        assertTrue(List.of(parameters.getValue()).contains(attack),
                "The original attack string should remain data, not executable SQL");
    }
}
