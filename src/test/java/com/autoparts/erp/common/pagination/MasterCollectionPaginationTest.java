package com.autoparts.erp.common.pagination;

import com.autoparts.erp.audit.service.AuditLogService;
import com.autoparts.erp.common.dto.PageResponse;
import com.autoparts.erp.customer.dto.CustomerResponse;
import com.autoparts.erp.customer.model.Customer;
import com.autoparts.erp.customer.repository.CustomerRepository;
import com.autoparts.erp.customer.serviceimpl.CustomerServiceImpl;
import com.autoparts.erp.product.dto.ProductResponse;
import com.autoparts.erp.product.model.Product;
import com.autoparts.erp.product.repository.ProductRepository;
import com.autoparts.erp.product.serviceimpl.ProductServiceImpl;
import com.autoparts.erp.supplier.dto.SupplierResponse;
import com.autoparts.erp.supplier.model.Supplier;
import com.autoparts.erp.supplier.repository.SupplierRepository;
import com.autoparts.erp.supplier.serviceimpl.SupplierServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MasterCollectionPaginationTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private SupplierRepository supplierRepository;

    @Test
    void pageResponseComputesBoundsCorrectly() {
        var page = PageResponse.of(List.of("A", "B", "C"), 0, 10, 25);
        assertEquals(0, page.page());
        assertEquals(10, page.size());
        assertEquals(25, page.totalElements());
        assertEquals(3, page.totalPages());
        assertTrue(page.first());
        assertFalse(page.last());

        var lastPage = PageResponse.of(List.of("X"), 2, 10, 25);
        assertFalse(lastPage.first());
        assertTrue(lastPage.last());
    }

    @Test
    void productFindPagedCapsMaxPageSizeAt100() {
        var service = new ProductServiceImpl(
                productRepository,
                mock(com.autoparts.erp.audit.service.AuditLogService.class)
        );

        when(productRepository.findPaged(eq(0), eq(100), any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        when(productRepository.countPaged(any(), any(), any())).thenReturn(0L);

        // Requested size 500 should be capped at 100
        var result = service.findPaged(0, 500, "brake", 1L, 2L, "name", "asc");

        verify(productRepository).findPaged(0, 100, "brake", 1L, 2L, "name", "asc");
        assertEquals(100, result.size());
    }

    @Test
    void customerFindPagedComputesOffsetAndMapsResponse() {
        var service = new CustomerServiceImpl(
                customerRepository,
                mock(com.autoparts.erp.audit.service.AuditLogService.class)
        );

        var customer = new Customer(
                1L, UUID.randomUUID(), "CUS000001", "Acme Auto",
                "9876543210", "acme@example.com", "Main Road",
                "Chennai", "Tamil Nadu", "600001", "33AAAAA0000A1Z5",
                true, OffsetDateTime.now(), OffsetDateTime.now()
        );

        when(customerRepository.findPaged(eq(20), eq(20), eq("Acme"), eq("Chennai"), isNull(), eq("customerName"), eq("asc")))
                .thenReturn(List.of(customer));
        when(customerRepository.countPaged(eq("Acme"), eq("Chennai"), isNull())).thenReturn(45L);

        var result = service.findPaged(1, 20, "Acme", "Chennai", null, "customerName", "asc");

        assertEquals(1, result.page());
        assertEquals(20, result.size());
        assertEquals(45L, result.totalElements());
        assertEquals(3, result.totalPages());
        assertEquals(1, result.content().size());
        assertEquals("Acme Auto", result.content().get(0).customerName());
    }

    @Test
    void supplierFindPagedEnforcesSafeDefaults() {
        var service = new SupplierServiceImpl(
                supplierRepository,
                mock(AuditLogService.class)
        );

        when(supplierRepository.findPaged(eq(0), eq(1), isNull(), isNull(), isNull(), eq("supplierName"), eq("asc")))
                .thenReturn(List.of());
        when(supplierRepository.countPaged(isNull(), isNull(), isNull())).thenReturn(0L);

        // Negative page and size should be normalized to safe minimums
        var result = service.findPaged(-1, -5, null, null, null, "supplierName", "asc");

        verify(supplierRepository).findPaged(0, 1, null, null, null, "supplierName", "asc");
        assertEquals(0, result.page());
        assertEquals(1, result.size());
    }
}
