package com.autoparts.erp.tax.repositoryimpl;

import com.autoparts.erp.tax.model.TaxRate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaxRateRepositoryImplTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @InjectMocks
    private TaxRateRepositoryImpl repository;

    @Test
    @DisplayName("existsOverlappingTaxRate binds taxName, excludeUuid, effectiveFrom, effectiveTo")
    void testExistsOverlappingTaxRate() {
        UUID excludeUuid = UUID.randomUUID();
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 12, 31);

        when(jdbcTemplate.queryForObject(any(String.class), any(SqlParameterSource.class), eq(Boolean.class)))
                .thenReturn(true);

        boolean exists = repository.existsOverlappingTaxRate("GST 18%", from, to, excludeUuid);

        assertTrue(exists);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> paramCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbcTemplate).queryForObject(sqlCaptor.capture(), paramCaptor.capture(), eq(Boolean.class));

        String sql = sqlCaptor.getValue();
        assertTrue(sql.contains(":taxName"));
        assertTrue(sql.contains(":excludeUuid"));
        assertTrue(sql.contains(":effectiveFrom"));
        assertTrue(sql.contains(":effectiveTo"));

        SqlParameterSource params = paramCaptor.getValue();
        assertEquals("GST 18%", params.getValue("taxName"));
        assertEquals(excludeUuid, params.getValue("excludeUuid"));
        assertEquals(from, params.getValue("effectiveFrom"));
        assertEquals(to, params.getValue("effectiveTo"));
    }

    @Test
    @DisplayName("lockTaxRateConfiguration executes advisory lock with lockKey parameter")
    void testLockTaxRateConfiguration() {
        repository.lockTaxRateConfiguration();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> paramCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbcTemplate).execute(sqlCaptor.capture(), paramCaptor.capture(), any(PreparedStatementCallback.class));

        assertTrue(sqlCaptor.getValue().contains(":lockKey"));
        assertEquals(824611L, paramCaptor.getValue().getValue("lockKey"));
    }

    @Test
    @DisplayName("create tax rate binds parameters and returns TaxRate")
    void testCreate() {
        UUID uuid = UUID.randomUUID();
        LocalDate from = LocalDate.of(2026, 1, 1);
        TaxRate dummy = new TaxRate(
                1L, uuid, "GST 18%",
                BigDecimal.valueOf(18.0),
                BigDecimal.valueOf(9.0),
                BigDecimal.valueOf(9.0),
                BigDecimal.valueOf(18.0),
                from, null, true, null, null
        );

        when(jdbcTemplate.queryForObject(any(String.class), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(dummy);

        TaxRate created = repository.create(
                uuid, "GST 18%",
                BigDecimal.valueOf(18.0),
                BigDecimal.valueOf(9.0),
                BigDecimal.valueOf(9.0),
                BigDecimal.valueOf(18.0),
                from, null
        );

        assertNotNull(created);
        assertEquals(uuid, created.uuid());
        assertEquals("GST 18%", created.taxName());

        ArgumentCaptor<SqlParameterSource> paramCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbcTemplate).queryForObject(any(String.class), paramCaptor.capture(), any(RowMapper.class));

        SqlParameterSource params = paramCaptor.getValue();
        assertEquals(uuid, params.getValue("uuid"));
        assertEquals("GST 18%", params.getValue("taxName"));
        assertEquals(BigDecimal.valueOf(18.0), params.getValue("gstRate"));
    }
}
