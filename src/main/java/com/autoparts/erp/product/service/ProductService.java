
package com.autoparts.erp.product.service;

import com.autoparts.erp.product.dto.ProductCreateRequest;
import com.autoparts.erp.product.dto.ProductResponse;
import com.autoparts.erp.product.dto.ProductUpdateRequest;

import java.util.List;
import java.util.UUID;

public interface ProductService {

    ProductResponse create(ProductCreateRequest request);

    List<ProductResponse> findAll();

    ProductResponse findByUuid(UUID uuid);

    ProductResponse update(
            UUID uuid,
            ProductUpdateRequest request
    );

    void deactivate(UUID uuid);
}
