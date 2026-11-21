package com.autoparts.erp.customer.ledger.repositoryimpl;

import com.autoparts.erp.customer.ledger.dto.CustomerLedgerEntry;
import com.autoparts.erp.customer.ledger.dto.CustomerLedgerSummary;
import com.autoparts.erp.customer.ledger.repository.CustomerLedgerRepository.CustomerInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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
class CustomerLedgerRepositoryImplTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @InjectMocks
    private CustomerLedgerRepositoryImpl repository;

    @Test
    @DisplayName("findCustomerByUuid binds customerUuid named parameter")
    void testFindCustomerByUuid() {
        UUID customerUuid = UUID.randomUUID();
        when(jdbcTemplate.query(any(String.class), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(new CustomerInfo(10L, customerUuid, "Acme Customer")));

        Optional<CustomerInfo> result = repository.findCustomerByUuid(customerUuid);

        assertTrue(result.isPresent());
        assertEquals("Acme Customer", result.get().customerName());

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> paramCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), paramCaptor.capture(), any(RowMapper.class));

        assertTrue(sqlCaptor.getValue().contains(":customerUuid"));
        assertEquals(customerUuid, paramCaptor.getValue().getValue("customerUuid"));
    }

    @Test
    @DisplayName("getOpeningBalance binds customerId and fromDate named parameters")
    void testGetOpeningBalance() {
        LocalDate fromDate = LocalDate.of(2026, 1, 1);
        when(jdbcTemplate.queryForObject(any(String.class), any(SqlParameterSource.class), eq(BigDecimal.class)))
                .thenReturn(BigDecimal.valueOf(500.00));

        BigDecimal balance = repository.getOpeningBalance(10L, fromDate);

        assertEquals(BigDecimal.valueOf(500.00), balance);

        ArgumentCaptor<SqlParameterSource> paramCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbcTemplate).queryForObject(any(String.class), paramCaptor.capture(), eq(BigDecimal.class));

        SqlParameterSource params = paramCaptor.getValue();
        assertEquals(10L, params.getValue("customerId"));
        assertEquals(fromDate, params.getValue("fromDate"));
    }

    @Test
    @DisplayName("countEntries binds customerId, fromDate, and toDate named parameters")
    void testCountEntries() {
        LocalDate fromDate = LocalDate.of(2026, 1, 1);
        LocalDate toDate = LocalDate.of(2026, 3, 31);

        when(jdbcTemplate.queryForObject(any(String.class), any(SqlParameterSource.class), eq(Long.class)))
                .thenReturn(15L);

        long count = repository.countEntries(10L, fromDate, toDate);

        assertEquals(15L, count);

        ArgumentCaptor<SqlParameterSource> paramCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbcTemplate).queryForObject(any(String.class), paramCaptor.capture(), eq(Long.class));

        SqlParameterSource params = paramCaptor.getValue();
        assertEquals(10L, params.getValue("customerId"));
        assertEquals(fromDate, params.getValue("fromDate"));
        assertEquals(toDate, params.getValue("toDate"));
    }
}
