package com.autoparts.erp.repository;

import com.autoparts.erp.ai.config.AiToolJdbcConfiguration;
import com.autoparts.erp.ai.tools.*;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies datasource isolation between the primary ERP database connection
 * and the read-only AI lookup datasource.
 *
 * Guarantees that:
 * 1. The primary DataSource is writable and marked @Primary.
 * 2. The AI tool DataSource is read-only and never marked @Primary.
 * 3. AI connections fail-closed against write operations (INSERT/UPDATE/DELETE).
 * 4. All 25 ERP repositories inject the primary NamedParameterJdbcTemplate.
 * 5. All AI lookup tools inject the read-only AI JdbcTemplate.
 */
class DatasourceIsolationIntegrationTest extends BaseRepositoryIntegrationTest {

    @Test
    @DisplayName("Configuration: Primary and AI tool Bean definitions are isolated and correctly qualified")
    void testDatasourceBeanDefinitionsAndIsolation() throws NoSuchMethodException {
        Class<AiToolJdbcConfiguration> configClass = AiToolJdbcConfiguration.class;

        // 1. Primary DataSource
        Method appDsMethod = configClass.getMethod("applicationDataSource",
                org.springframework.boot.jdbc.autoconfigure.DataSourceProperties.class);
        assertTrue(AnnotatedElementUtils.hasAnnotation(appDsMethod, Primary.class),
                "applicationDataSource must be marked @Primary");
        Bean appDsBean = AnnotatedElementUtils.findMergedAnnotation(appDsMethod, Bean.class);
        assertNotNull(appDsBean);
        assertEquals("dataSource", appDsBean.name()[0]);

        // 2. AI Tool DataSources (prod and dev)
        Method prodAiDsMethod = configClass.getMethod("productionAiToolDataSource",
                String.class, String.class, String.class);
        assertFalse(AnnotatedElementUtils.hasAnnotation(prodAiDsMethod, Primary.class),
                "productionAiToolDataSource must NOT be marked @Primary");
        Bean prodAiDsBean = AnnotatedElementUtils.findMergedAnnotation(prodAiDsMethod, Bean.class);
        assertNotNull(prodAiDsBean);
        assertEquals("aiToolDataSource", prodAiDsBean.name()[0]);

        Method devAiDsMethod = configClass.getMethod("developmentAiToolDataSource",
                String.class, String.class, String.class);
        assertFalse(AnnotatedElementUtils.hasAnnotation(devAiDsMethod, Primary.class),
                "developmentAiToolDataSource must NOT be marked @Primary");

        // 3. Primary NamedParameterJdbcTemplate
        Method primaryTemplateMethod = configClass.getMethod("applicationNamedParameterJdbcTemplate",
                javax.sql.DataSource.class);
        assertTrue(AnnotatedElementUtils.hasAnnotation(primaryTemplateMethod, Primary.class),
                "applicationNamedParameterJdbcTemplate must be marked @Primary");
        Bean primaryTemplateBean = AnnotatedElementUtils.findMergedAnnotation(primaryTemplateMethod, Bean.class);
        assertNotNull(primaryTemplateBean);
        assertEquals("namedParameterJdbcTemplate", primaryTemplateBean.name()[0]);

        // 4. AI Tool NamedParameterJdbcTemplate
        Method aiNamedTemplateMethod = configClass.getMethod("aiToolNamedParameterJdbcTemplate",
                javax.sql.DataSource.class);
        assertFalse(AnnotatedElementUtils.hasAnnotation(aiNamedTemplateMethod, Primary.class),
                "aiToolNamedParameterJdbcTemplate must NOT be marked @Primary");
        Bean aiNamedTemplateBean = AnnotatedElementUtils.findMergedAnnotation(aiNamedTemplateMethod, Bean.class);
        assertNotNull(aiNamedTemplateBean);
        assertEquals("aiToolNamedParameterJdbcTemplate", aiNamedTemplateBean.name()[0]);
    }

    @Test
    @DisplayName("Security: AI tool DataSource is read-only and blocks write operations")
    void testReadOnlyDatasourceEnforcesReadOnlyBehavior() throws SQLException {
        AiToolJdbcConfiguration config = new AiToolJdbcConfiguration();
        try (HikariDataSource aiDs = config.developmentAiToolDataSource(dbUrl, dbUser, dbPass)) {
            assertTrue(aiDs.isReadOnly(), "AI tool DataSource must be configured as read-only");

            // Connection-level read-only verification
            try (Connection conn = aiDs.getConnection()) {
                assertTrue(conn.isReadOnly(), "Connection from AI DataSource must be in read-only mode");

                conn.setAutoCommit(false);
                try (Statement stmt = conn.createStatement()) {
                    // Attempting a write in a read-only transaction must fail
                    assertThrows(SQLException.class, () -> {
                        stmt.executeUpdate("UPDATE users SET updated_at = CURRENT_TIMESTAMP WHERE id = -999");
                    }, "PostgreSQL must reject write statements on read-only connections");
                } finally {
                    conn.rollback();
                }
            }
        }
    }

    @Test
    @DisplayName("Architecture: All 25 ERP repositories inject primary NamedParameterJdbcTemplate without AI qualifier")
    void testErpRepositoriesDoNotAcceptAiToolDatasource() {
        List<Class<?>> erpRepositories = List.of(
                com.autoparts.erp.product.repositoryimpl.ProductRepositoryImpl.class,
                com.autoparts.erp.masters.brand.repositoryimpl.ProductBrandRepositoryImpl.class,
                com.autoparts.erp.masters.category.repositoryimpl.ProductCategoryRepositoryImpl.class,
                com.autoparts.erp.inventory.repositoryimpl.InventoryRepositoryImpl.class,
                com.autoparts.erp.supplier.repositoryimpl.SupplierRepositoryImpl.class,
                com.autoparts.erp.supplier.ledger.repositoryimpl.SupplierLedgerRepositoryImpl.class,
                com.autoparts.erp.purchase.repositoryimpl.PurchaseRepositoryImpl.class,
                com.autoparts.erp.purchase.payment.repositoryimpl.PurchasePaymentRepositoryImpl.class,
                com.autoparts.erp.purchase.returns.repositoryimpl.PurchaseReturnRepositoryImpl.class,
                com.autoparts.erp.purchase.ledger.repositoryimpl.PurchaseLedgerRepositoryImpl.class,
                com.autoparts.erp.customer.repositoryimpl.CustomerRepositoryImpl.class,
                com.autoparts.erp.customer.ledger.repositoryimpl.CustomerLedgerRepositoryImpl.class,
                com.autoparts.erp.sales.repositoryimpl.SalesRepositoryImpl.class,
                com.autoparts.erp.sales.repositoryimpl.SalesReturnRepositoryImpl.class,
                com.autoparts.erp.sales.ledger.repositoryimpl.SalesLedgerRepositoryImpl.class,
                com.autoparts.erp.cashregister.repositoryimpl.CashRegisterRepositoryImpl.class,
                com.autoparts.erp.tax.repositoryimpl.TaxRateRepositoryImpl.class,
                com.autoparts.erp.tax.repositoryimpl.ProductTaxMappingRepositoryImpl.class,
                com.autoparts.erp.expense.management.repositoryimpl.ExpenseRepositoryImpl.class,
                com.autoparts.erp.expense.category.repositoryimpl.ExpenseCategoryRepositoryImpl.class,
                com.autoparts.erp.expense.ledger.repositoryimpl.ExpenseLedgerRepositoryImpl.class,
                com.autoparts.erp.dailyclosing.repositoryimpl.DailyClosingRepositoryImpl.class,
                com.autoparts.erp.audit.repositoryimpl.AuditLogRepositoryImpl.class,
                com.autoparts.erp.auth.repositoryimpl.UserRepositoryImpl.class,
                com.autoparts.erp.auth.repositoryimpl.UserManagementRepositoryImpl.class,
                com.autoparts.erp.auth.repositoryimpl.RefreshTokenRepositoryImpl.class
        );

        for (Class<?> repoClass : erpRepositories) {
            boolean hasNamedParamTemplateConstructor = false;
            for (Constructor<?> ctor : repoClass.getDeclaredConstructors()) {
                for (Parameter param : ctor.getParameters()) {
                    if (param.getType().equals(NamedParameterJdbcTemplate.class)) {
                        hasNamedParamTemplateConstructor = true;
                        Qualifier q = param.getAnnotation(Qualifier.class);
                        if (q != null) {
                            assertNotEquals("aiToolNamedParameterJdbcTemplate", q.value(),
                                    repoClass.getSimpleName() + " must never inject aiToolNamedParameterJdbcTemplate");
                            assertNotEquals("aiToolJdbcTemplate", q.value(),
                                    repoClass.getSimpleName() + " must never inject aiToolJdbcTemplate");
                        }
                    }
                }
            }
            assertTrue(hasNamedParamTemplateConstructor,
                    repoClass.getSimpleName() + " must have a constructor accepting NamedParameterJdbcTemplate");
        }
    }

    @Test
    @DisplayName("Architecture: All AI lookup tools inject the read-only AI template")
    void testAiLookupToolsInjectAiTemplate() {
        List<Class<?>> aiTools = List.of(
                AuditLogLookupTool.class,
                CustomerLookupTool.class,
                FinancialLookupTool.class,
                InventoryHistoryLookupTool.class,
                ProductLookupTool.class,
                PurchaseLookupTool.class,
                ReportLookupTool.class,
                ReturnLookupTool.class,
                SalesLookupTool.class,
                SupplierLookupTool.class
        );

        for (Class<?> toolClass : aiTools) {
            boolean hasQualifiedAiTemplate = false;
            for (Constructor<?> ctor : toolClass.getDeclaredConstructors()) {
                for (Parameter param : ctor.getParameters()) {
                    if (param.getType().equals(JdbcTemplate.class) || param.getType().equals(NamedParameterJdbcTemplate.class)) {
                        Qualifier q = param.getAnnotation(Qualifier.class);
                        if (q != null && "aiToolJdbcTemplate".equals(q.value())) {
                            hasQualifiedAiTemplate = true;
                        }
                    }
                }
            }
            assertTrue(hasQualifiedAiTemplate,
                    toolClass.getSimpleName() + " must inject @Qualifier(\"aiToolJdbcTemplate\")");
        }
    }
}
