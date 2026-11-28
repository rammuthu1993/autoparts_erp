package com.autoparts.erp.tax.service;

import com.autoparts.erp.common.dto.PageResponse;
import com.autoparts.erp.tax.dto.BulkProductTaxAssignRequest;
import com.autoparts.erp.tax.dto.BulkProductTaxAssignResponse;
import com.autoparts.erp.tax.dto.ProductTaxMappingCloseRequest;
import com.autoparts.erp.tax.dto.ProductTaxMappingCreateRequest;
import com.autoparts.erp.tax.dto.ProductTaxMappingResponse;
import com.autoparts.erp.tax.dto.ProductTaxMappingSupersedeRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ProductTaxMappingService {

    ProductTaxMappingResponse create(
        UUID productUuid,
        ProductTaxMappingCreateRequest request
    );

    ProductTaxMappingResponse findByUuid(UUID uuid);

    List<ProductTaxMappingResponse> findByProductUuid(UUID productUuid);

    PageResponse<ProductTaxMappingResponse> findPagedByProductUuid(
        UUID productUuid,
        int page,
        int size,
        LocalDate fromDate,
        LocalDate toDate
    );

    ProductTaxMappingResponse findCurrentByProductUuid(UUID productUuid);

    ProductTaxMappingResponse closePeriod(
        UUID productUuid,
        UUID mappingUuid,
        ProductTaxMappingCloseRequest request
    );

    ProductTaxMappingResponse supersede(
        UUID productUuid,
        UUID mappingUuid,
        ProductTaxMappingSupersedeRequest request
    );

    BulkProductTaxAssignResponse bulkAssign(BulkProductTaxAssignRequest request);
}
