package com.autoparts.erp.masters.brand.service;

import com.autoparts.erp.common.exception.ApiException;
import com.autoparts.erp.masters.brand.dto.BrandCreateRequest;
import com.autoparts.erp.masters.brand.dto.BrandResponse;
import com.autoparts.erp.masters.brand.dto.BrandUpdateRequest;
import com.autoparts.erp.masters.brand.model.BrandErrorCode;
import com.autoparts.erp.masters.brand.model.ProductBrand;
import com.autoparts.erp.masters.brand.repository.ProductBrandRepository;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ProductBrandServiceImpl implements ProductBrandService {

    private final ProductBrandRepository repository;

    public ProductBrandServiceImpl(ProductBrandRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public BrandResponse create(BrandCreateRequest request) {
        String name = request.name().trim();
        ProductBrand brand = new ProductBrand(
                null,
                UUID.randomUUID(),
                name,
                request.description(),
                true,
                null,
                null
        );
        
        if (repository.findByNameIgnoreCase(name).isPresent()) {
            throw new ApiException(
                    BrandErrorCode.BRAND_ALREADY_EXISTS
            );
        }

        try {
            return toResponse(repository.create(brand));
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A brand with this name already exists"
            );
        }
    }

    @Override
    public List<BrandResponse> findAll() {

        return repository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public BrandResponse findByUuid(UUID uuid) {

        ProductBrand brand = repository.findByUuid(uuid)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Brand not found"
                ));

        return toResponse(brand);
    }

    @Override
    @Transactional
    public BrandResponse update(
            UUID uuid,
            BrandUpdateRequest request
    ) {

        try {
            boolean updated = repository.update(
                    uuid,
                    request.name().trim(),
                    request.description()
            );

            if (!updated) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Active brand not found"
                );
            }

            return findByUuid(uuid);

        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A brand with this name already exists"
            );
        }
    }

    @Override
    @Transactional
    public void deactivate(UUID uuid) {

        boolean deactivated = repository.deactivate(uuid);

        if (!deactivated) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Active brand not found"
            );
        }
    }

    private BrandResponse toResponse(ProductBrand brand) {

        return new BrandResponse(
                brand.id(),
                brand.name(),
                brand.description(),
                brand.active(),
                brand.createdAt(),
                brand.updatedAt()
        );
    }
}