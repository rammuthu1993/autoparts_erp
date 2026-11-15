package com.autoparts.erp.ai.repositoryimpl;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import com.autoparts.erp.ai.enums.AiAgentRunStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiRepositoryImplStatusTest {

    @Mock
    private NamedParameterJdbcTemplate jdbc;

    @InjectMocks
    private AiRepositoryImpl aiRepository;

    @Test
    void repeatedTerminalStatusUpdatesOnlyTargetRunningRows() {
        UUID runId = UUID.randomUUID();
        when(jdbc.update(anyString(), any(SqlParameterSource.class)))
                .thenReturn(1, 0);

        assertEquals(
                1,
                aiRepository.updateAgentRunStatus(
                        runId,
                        AiAgentRunStatus.CANCELLED,
                        "Execution cancelled by user"
                )
        );
        assertEquals(
                0,
                aiRepository.updateAgentRunStatus(
                        runId,
                        AiAgentRunStatus.FAILED,
                        "Later failure"
                )
        );

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(2)).update(sql.capture(), any(SqlParameterSource.class));
        assertEquals(2, sql.getAllValues().size());
        for (String statement : sql.getAllValues()) {
            String normalized = statement.replaceAll("\\s+", " ");
            assertTrue(normalized.contains("WHERE id = :runId AND status = 'RUNNING'"));
        }
    }
}
