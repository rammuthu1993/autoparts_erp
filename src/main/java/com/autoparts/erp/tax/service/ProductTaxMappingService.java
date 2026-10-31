
package com.autoparts.erp.tax.service;

import com.autoparts.erp.tax.dto.ProductTaxMappingCreateRequest;
import com.autoparts.erp.tax.dto.ProductTaxMappingResponse;

import java.util.List;
import java.util.UUID;

public interface ProductTaxMappingService {

    ProductTaxMappingResponse create(
        UUID productUuid,
        ProductTaxMappingCreateRequest request
    );

    ProductTaxMappingResponse findByUuid(UUID uuid);

    List<ProductTaxMappingResponse> findByProductUuid(
        UUID productUuid
    );

    ProductTaxMappingResponse findCurrentByProductUuid(
        UUID productUuid
    );
}
