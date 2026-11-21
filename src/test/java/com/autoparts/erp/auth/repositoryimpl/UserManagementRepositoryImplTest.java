package com.autoparts.erp.auth.repositoryimpl;

import com.autoparts.erp.auth.model.ManagedUser;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserManagementRepositoryImplTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @InjectMocks
    private UserManagementRepositoryImpl repository;

    @Test
    @DisplayName("findByUuid queries with named parameter :uuid and aggregates roles")
    void testFindByUuid() {
        UUID uuid = UUID.randomUUID();
        Instant now = Instant.now();

        when(jdbcTemplate.query(any(String.class), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(
                        new Object[]{1L, uuid, "admin", true, now, now, "ROLE_ADMIN"},
                        new Object[]{1L, uuid, "admin", true, now, now, "ROLE_USER"}
                ));

        Optional<ManagedUser> result = repository.findByUuid(uuid);

        assertTrue(result.isPresent());
        ManagedUser user = result.get();
        assertEquals("admin", user.username());
        assertEquals(Set.of("ROLE_ADMIN", "ROLE_USER"), user.roles());

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> paramCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), paramCaptor.capture(), any(RowMapper.class));

        assertTrue(sqlCaptor.getValue().contains(":uuid"));
        assertEquals(uuid, paramCaptor.getValue().getValue("uuid"));
    }

    @Test
    @DisplayName("create user binds named parameters and returns generated id")
    void testCreate() {
        UUID uuid = UUID.randomUUID();

        when(jdbcTemplate.queryForObject(any(String.class), any(SqlParameterSource.class), eq(Long.class)))
                .thenReturn(10L);

        Long userId = repository.create("johndoe", "hash123", uuid);

        assertNotNull(userId);
        assertEquals(10L, userId);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> paramCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);

        verify(jdbcTemplate).queryForObject(
                sqlCaptor.capture(),
                paramCaptor.capture(),
                eq(Long.class)
        );

        SqlParameterSource params = paramCaptor.getValue();
        assertEquals("johndoe", params.getValue("username"));
        assertEquals("hash123", params.getValue("passwordHash"));
        assertEquals(uuid, params.getValue("uuid"));
        assertTrue(sqlCaptor.getValue().contains(":username"));
        assertTrue(sqlCaptor.getValue().contains(":passwordHash"));
        assertTrue(sqlCaptor.getValue().contains(":uuid"));
    }
}
