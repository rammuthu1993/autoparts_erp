package com.autoparts.erp.common.pagination;

import com.autoparts.erp.common.exception.ApiException;
import com.autoparts.erp.common.exception.ClientValidationException;
import com.autoparts.erp.customer.exception.CustomerErrorCode;
import com.autoparts.erp.customer.ledger.repository.CustomerLedgerRepository;
import com.autoparts.erp.customer.ledger.serviceimpl.CustomerLedgerServiceImpl;
import com.autoparts.erp.customer.repository.CustomerRepository;
import com.autoparts.erp.expense.ledger.repository.ExpenseLedgerRepository;
import com.autoparts.erp.expense.ledger.serviceimpl.ExpenseLedgerServiceImpl;
import com.autoparts.erp.purchase.exception.PurchaseErrorCode;
import com.autoparts.erp.purchase.ledger.repository.PurchaseLedgerRepository;
import com.autoparts.erp.purchase.ledger.serviceimpl.PurchaseLedgerServiceImpl;
import com.autoparts.erp.sales.ledger.exception.SalesLedgerErrorCode;
import com.autoparts.erp.sales.ledger.repository.SalesLedgerRepository;
import com.autoparts.erp.sales.ledger.serviceimpl.SalesLedgerServiceImpl;
import com.autoparts.erp.supplier.model.SupplierErrorCode;
import com.autoparts.erp.supplier.ledger.repository.SupplierLedgerRepository;
import com.autoparts.erp.supplier.ledger.serviceimpl.SupplierLedgerServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("Ledger Pagination Offset Overflow Specifications")
class LedgerPaginationOverflowTest {

    @Mock
    private CustomerLedgerRepository customerLedgerRepository;

    @Mock
    private SupplierLedgerRepository supplierLedgerRepository;

    @Mock
    private PurchaseLedgerRepository purchaseLedgerRepository;

    @Mock
    private SalesLedgerRepository salesLedgerRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private ExpenseLedgerRepository expenseLedgerRepository;

    @Test
    @DisplayName("CustomerLedgerService rejects integer overflow with INVALID_CUSTOMER_LEDGER_FILTER")
    void customerLedgerRejectsOverflow() {
        var service = new CustomerLedgerServiceImpl(customerLedgerRepository);
        UUID customerUuid = UUID.randomUUID();

        assertThatThrownBy(() -> service.getCustomerLedger(
                customerUuid,
                LocalDate.now().minusDays(30),
                LocalDate.now(),
                Integer.MAX_VALUE,
                20
        )).isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getError())
                        .isEqualTo(CustomerErrorCode.INVALID_CUSTOMER_LEDGER_FILTER));

        verifyNoInteractions(customerLedgerRepository);
    }

    @Test
    @DisplayName("SupplierLedgerService rejects integer overflow with INVALID_SUPPLIER_LEDGER_FILTER")
    void supplierLedgerRejectsOverflow() {
        var service = new SupplierLedgerServiceImpl(supplierLedgerRepository);
        UUID supplierUuid = UUID.randomUUID();

        assertThatThrownBy(() -> service.getSupplierLedger(
                supplierUuid,
                LocalDate.now().minusDays(30),
                LocalDate.now(),
                Integer.MAX_VALUE,
                20
        )).isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getError())
                        .isEqualTo(SupplierErrorCode.INVALID_SUPPLIER_LEDGER_FILTER));

        verifyNoInteractions(supplierLedgerRepository);
    }

    @Test
    @DisplayName("PurchaseLedgerService rejects integer overflow with INVALID_PURCHASE_LEDGER_FILTER")
    void purchaseLedgerRejectsOverflow() {
        var service = new PurchaseLedgerServiceImpl(purchaseLedgerRepository);

        assertThatThrownBy(() -> service.getLedger(
                LocalDate.now().minusDays(30),
                LocalDate.now(),
                UUID.randomUUID(),
                null,
                Integer.MAX_VALUE,
                20
        )).isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getError())
                        .isEqualTo(PurchaseErrorCode.INVALID_PURCHASE_LEDGER_FILTER));

        verifyNoInteractions(purchaseLedgerRepository);
    }

    @Test
    @DisplayName("SalesLedgerService rejects integer overflow with INVALID_PAGINATION")
    void salesLedgerRejectsOverflow() {
        var service = new SalesLedgerServiceImpl(salesLedgerRepository, customerRepository);
        UUID customerUuid = UUID.randomUUID();

        assertThatThrownBy(() -> service.getSalesLedger(
                customerUuid,
                LocalDate.now().minusDays(30),
                LocalDate.now(),
                null,
                Integer.MAX_VALUE,
                20
        )).isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getError())
                        .isEqualTo(SalesLedgerErrorCode.INVALID_PAGINATION));

        verifyNoInteractions(salesLedgerRepository);
    }

    @Test
    @DisplayName("ExpenseLedgerService rejects integer overflow with ClientValidationException")
    void expenseLedgerRejectsOverflow() {
        var service = new ExpenseLedgerServiceImpl(expenseLedgerRepository);

        assertThatThrownBy(() -> service.getLedger(
                LocalDate.now().minusDays(30),
                LocalDate.now(),
                UUID.randomUUID(),
                null,
                null,
                Integer.MAX_VALUE,
                20
        )).isInstanceOf(ClientValidationException.class)
                .hasMessageContaining("Pagination offset exceeds maximum supported limit");

        verifyNoInteractions(expenseLedgerRepository);
    }
}
