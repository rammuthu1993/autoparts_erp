package com.autoparts.erp.ai.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

@ExtendWith(MockitoExtension.class)
class PurchaseLookupToolTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    private PurchaseLookupTool tool;

    @BeforeEach
    void setUp() {
        tool = new PurchaseLookupTool(jdbcTemplate);
    }

    @Test
    void searchesRecentPurchasesAndClampsLimit() {

        List<Map<String, Object>> expected = List.of(
                Map.of(
                        "purchase_number",
                        "PUR-00000001"
                )
        );

        when(jdbcTemplate.queryForList(
                anyString(),
                any(SqlParameterSource.class)
        )).thenReturn(expected);

        List<Map<String, Object>> actual =
                tool.searchPurchases("", 500);

        assertEquals(expected, actual);

        ArgumentCaptor<String> sqlCaptor =
                ArgumentCaptor.forClass(String.class);

        ArgumentCaptor<SqlParameterSource> parametersCaptor =
                ArgumentCaptor.forClass(SqlParameterSource.class);

        verify(jdbcTemplate).queryForList(
                sqlCaptor.capture(),
                parametersCaptor.capture()
        );

        assertTrue(
                sqlCaptor.getValue().contains("LIMIT :limit")
        );

        MapSqlParameterSource parameters =
                (MapSqlParameterSource) parametersCaptor.getValue();

        assertEquals(50, parameters.getValue("limit"));
    }


	@Test
	void searchesBySupplierAndPurchaseIdentifiers() {
	
	    when(jdbcTemplate.queryForList(
	            anyString(),
	            any(SqlParameterSource.class)
	    )).thenReturn(List.of());
	
	    tool.searchPurchases("ABC Auto Parts", 10);
	
	    ArgumentCaptor<String> sqlCaptor =
	            ArgumentCaptor.forClass(String.class);
	
	    ArgumentCaptor<SqlParameterSource> parametersCaptor =
	            ArgumentCaptor.forClass(SqlParameterSource.class);
	
	    verify(jdbcTemplate).queryForList(
	            sqlCaptor.capture(),
	            parametersCaptor.capture()
	    );
	
	    String sql = sqlCaptor.getValue();
	
	    assertTrue(
	            sql.contains("LOWER(p.purchase_number) = LOWER(:identifier)")
	    );
	
	    assertTrue(
	            sql.contains("LOWER(p.supplier_invoice_no) = LOWER(:identifier)")
	    );
	
	    assertTrue(
	            sql.contains("s.supplier_name ILIKE :identifierSupplier")
	    );
	
	    MapSqlParameterSource parameters =
	            (MapSqlParameterSource) parametersCaptor.getValue();
	
	    assertEquals(
	            "ABC Auto Parts",
	            parameters.getValue("identifier")
	    );
	
	    assertEquals(
	            "%ABC Auto Parts%",
	            parameters.getValue("identifierSupplier")
	    );
	
	    assertEquals(
	            10,
	            parameters.getValue("limit")
	    );
	}


    @Test
    void returnsEmptyForBlankPurchaseIdentifier() {

        assertEquals(
                List.of(),
                tool.getPurchaseDetails(" ")
        );
    }

    @Test
    void retrievesPurchaseDetailsByExactIdentifier() {

        List<Map<String, Object>> expected = List.of(
                Map.of(
                        "purchase_number", "PUR-00000001",
                        "product_code", "GAP-000001"
                )
        );

        when(jdbcTemplate.queryForList(
                anyString(),
                any(SqlParameterSource.class)
        )).thenReturn(expected);

        List<Map<String, Object>> actual =
                tool.getPurchaseDetails("PUR-00000001");

        assertEquals(expected, actual);

        ArgumentCaptor<String> sqlCaptor =
                ArgumentCaptor.forClass(String.class);

        ArgumentCaptor<SqlParameterSource> parametersCaptor =
                ArgumentCaptor.forClass(SqlParameterSource.class);

        verify(jdbcTemplate).queryForList(
                sqlCaptor.capture(),
                parametersCaptor.capture()
        );

        assertTrue(
                sqlCaptor.getValue().contains(
                        "purchase_products pp"
                )
        );

        MapSqlParameterSource parameters =
                (MapSqlParameterSource) parametersCaptor.getValue();

        assertEquals(
                "PUR-00000001",
                parameters.getValue("purchaseNumber")
        );

        assertEquals(
                "PUR-00000001",
                parameters.getValue("invoiceNumber")
        );

        assertEquals(
                100,
                parameters.getValue("limit")
        );
    }
}