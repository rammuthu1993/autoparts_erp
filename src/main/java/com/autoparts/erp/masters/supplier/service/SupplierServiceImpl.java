
package com.autoparts.erp.masters.supplier.service;

import com.autoparts.erp.common.exception.ApiException;
import com.autoparts.erp.masters.supplier.dto.SupplierCreateRequest;
import com.autoparts.erp.masters.supplier.dto.SupplierResponse;
import com.autoparts.erp.masters.supplier.dto.SupplierUpdateRequest;
import com.autoparts.erp.masters.supplier.model.Supplier;
import com.autoparts.erp.masters.supplier.model.SupplierErrorCode;
import com.autoparts.erp.masters.supplier.repository.SupplierRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class SupplierServiceImpl implements SupplierService {

    private final SupplierRepository repository;

    public SupplierServiceImpl(SupplierRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public SupplierResponse create(SupplierCreateRequest request) {
        Supplier supplier = new Supplier(
            null,
            UUID.randomUUID(),
            clean(request.supplierName()),
            clean(request.contactPerson()),
            clean(request.phone()),
            clean(request.email()),
            clean(request.address()),
            clean(request.city()),
            clean(request.state()),
            clean(request.pincode()),
            normalizeGstin(request.gstin()),
            request.paymentTermsDays() == null
                ? 0 : request.paymentTermsDays(),
            true,
            null,
            null
        );
        validateDuplicateOnCreate(request);
        try {
            return toResponse(repository.create(supplier));
        } catch (DuplicateKeyException exception) {
            if (supplier.gstin() != null) {
                throw new ApiException(
                    SupplierErrorCode.SUPPLIER_GSTIN_ALREADY_EXISTS
                );
            }

            throw new ApiException(
                SupplierErrorCode.INVALID_SUPPLIER_DATA
            );
        }
    }

    @Override
    public List<SupplierResponse> findAll() {
        return repository.findAll()
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Override
    public SupplierResponse findByUuid(UUID uuid) {
        Supplier supplier = repository.findByUuid(uuid)
            .filter(Supplier::active)
            .orElseThrow(() ->
                new ApiException(SupplierErrorCode.SUPPLIER_NOT_FOUND)
            );

        return toResponse(supplier);
    }

    @Override
    @Transactional
    public SupplierResponse update(
        UUID uuid,
        SupplierUpdateRequest request
    ) {
        Supplier existing = repository.findByUuid(uuid)
            .filter(Supplier::active)
            .orElseThrow(() ->
                new ApiException(SupplierErrorCode.SUPPLIER_NOT_FOUND)
            );

        Supplier updated = new Supplier(
            existing.id(),
            existing.uuid(),
            clean(request.supplierName()),
            clean(request.contactPerson()),
            clean(request.phone()),
            clean(request.email()),
            clean(request.address()),
            clean(request.city()),
            clean(request.state()),
            clean(request.pincode()),
            normalizeGstin(request.gstin()),
            request.paymentTermsDays() == null
                ? existing.paymentTermsDays()
                : request.paymentTermsDays(),
            existing.active(),
            existing.createdAt(),
            existing.updatedAt()
        );
//        validateDuplicateOnUpdate(uuid, request);
        try {
            if (!repository.update(uuid, updated)) {
                throw new ApiException(
                    SupplierErrorCode.SUPPLIER_NOT_FOUND
                );
            }
        } catch (DuplicateKeyException exception) {
            if (updated.gstin() != null) {
                throw new ApiException(
                    SupplierErrorCode.SUPPLIER_GSTIN_ALREADY_EXISTS
                );
            }

            throw new ApiException(
                SupplierErrorCode.INVALID_SUPPLIER_DATA
            );
        }

        return findByUuid(uuid);
    }

    @Override
    @Transactional
    public void deactivate(UUID uuid) {
        if (!repository.deactivate(uuid)) {
            throw new ApiException(
                SupplierErrorCode.SUPPLIER_NOT_FOUND
            );
        }
    }

    private SupplierResponse toResponse(Supplier supplier) {
        return new SupplierResponse(
            supplier.id(),
            supplier.uuid(),
            supplier.supplierName(),
            supplier.contactPerson(),
            supplier.phone(),
            supplier.email(),
            supplier.address(),
            supplier.city(),
            supplier.state(),
            supplier.pincode(),
            supplier.gstin(),
            supplier.paymentTermsDays(),
            supplier.active(),
            supplier.createdAt(),
            supplier.updatedAt()
        );
    }

    private String clean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normalizeGstin(String gstin) {
        String value = clean(gstin);
        return value == null ? null : value.toUpperCase();
    }
    

	private void validateDuplicateOnCreate(SupplierCreateRequest request) {
	
	    String supplierName = clean(request.supplierName());
	    String phone = clean(request.phone());
	    String email = clean(request.email());
	    String gstin = normalizeGstin(request.gstin());
	
	    if (repository.existsBySupplierName(supplierName)) {
	        throw new ApiException(
	            SupplierErrorCode.SUPPLIER_NAME_ALREADY_EXISTS
	        );
	    }
	
	    if (repository.existsByPhone(phone)) {
	        throw new ApiException(
	            SupplierErrorCode.SUPPLIER_PHONE_ALREADY_EXISTS
	        );
	    }
	
	    if (repository.existsByEmail(email)) {
	        throw new ApiException(
	            SupplierErrorCode.SUPPLIER_EMAIL_ALREADY_EXISTS
	        );
	    }
	
	    if (repository.existsByGstin(gstin)) {
	        throw new ApiException(
	            SupplierErrorCode.SUPPLIER_GSTIN_ALREADY_EXISTS
	        );
	    }
	}
	
//	private void validateDuplicateOnUpdate(
//	    UUID uuid,
//	    SupplierUpdateRequest request
//	) {
//	    String supplierName = clean(request.supplierName());
//	    String phone = clean(request.phone());
//	    String email = clean(request.email());
//	    String gstin = normalizeGstin(request.gstin());
//	
//	    if (repository.existsBySupplierNameExcludingUuid(
//	        supplierName, uuid
//	    )) {
//	        throw new ApiException(
//	            SupplierErrorCode.SUPPLIER_NAME_ALREADY_EXISTS
//	        );
//	    }
//	
//	    if (repository.existsByPhoneExcludingUuid(phone, uuid)) {
//	        throw new ApiException(
//	            SupplierErrorCode.SUPPLIER_PHONE_ALREADY_EXISTS
//	        );
//	    }
//	
//	    if (repository.existsByEmailExcludingUuid(email, uuid)) {
//	        throw new ApiException(
//	            SupplierErrorCode.SUPPLIER_EMAIL_ALREADY_EXISTS
//	        );
//	    }
//	
//	    if (repository.existsByGstinExcludingUuid(gstin, uuid)) {
//	        throw new ApiException(
//	            SupplierErrorCode.SUPPLIER_GSTIN_ALREADY_EXISTS
//	        );
//	    }
//	}

}
