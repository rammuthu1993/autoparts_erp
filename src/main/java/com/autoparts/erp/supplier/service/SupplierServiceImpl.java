
package com.autoparts.erp.supplier.service;

import com.autoparts.erp.auth.security.annotation.AdminOnly;
import com.autoparts.erp.auth.security.annotation.AdminOrManager;
import com.autoparts.erp.auth.security.annotation.SalesAccess;
import com.autoparts.erp.audit.service.AuditLogService;
import com.autoparts.erp.common.exception.ApiException;
import com.autoparts.erp.supplier.dto.SupplierCreateRequest;
import com.autoparts.erp.supplier.dto.SupplierResponse;
import com.autoparts.erp.supplier.dto.SupplierUpdateRequest;
import com.autoparts.erp.supplier.model.Supplier;
import com.autoparts.erp.supplier.model.SupplierErrorCode;
import com.autoparts.erp.supplier.repository.SupplierRepository;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class SupplierServiceImpl implements SupplierService {

    private final SupplierRepository repository;
    private final AuditLogService auditLogService;

    public SupplierServiceImpl(
        SupplierRepository repository,
        AuditLogService auditLogService
    ) {
        this.repository = repository;
        this.auditLogService = auditLogService;
    }

    @Override
    @Transactional
    @AdminOrManager
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
            Supplier saved = repository.create(supplier);

            auditLogService.log(
                "CREATE",
                "SUPPLIER",
                saved.uuid().toString(),
                "Supplier created: " + saved.supplierName(),
                null,
                toAuditValues(saved)
            );

            return toResponse(saved);

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
    @SalesAccess
    public List<SupplierResponse> findAll() {

        return repository.findAll()
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Override
    @SalesAccess
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
    @AdminOrManager
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

        // Existing update duplicate-validation behavior is preserved.
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

        Supplier saved = repository.findByUuid(uuid)
            .filter(Supplier::active)
            .orElseThrow(() ->
                new ApiException(SupplierErrorCode.SUPPLIER_NOT_FOUND)
            );

        auditLogService.log(
            "UPDATE",
            "SUPPLIER",
            saved.uuid().toString(),
            "Supplier updated: " + saved.supplierName(),
            toAuditValues(existing),
            toAuditValues(saved)
        );

        return toResponse(saved);
    }

    @Override
    @Transactional
    @AdminOnly
    public void deactivate(UUID uuid) {

        Supplier supplier = repository.findByUuid(uuid)
            .filter(Supplier::active)
            .orElseThrow(() ->
                new ApiException(SupplierErrorCode.SUPPLIER_NOT_FOUND)
            );

        if (!repository.deactivate(uuid)) {
            throw new ApiException(
                SupplierErrorCode.SUPPLIER_NOT_FOUND
            );
        }

        Map<String, Object> newValues = new LinkedHashMap<>();
        newValues.put("supplierName", supplier.supplierName());
        newValues.put("active", false);

        auditLogService.log(
            "DELETE",
            "SUPPLIER",
            supplier.uuid().toString(),
            "Supplier deactivated: " + supplier.supplierName(),
            toAuditValues(supplier),
            newValues
        );
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

    /**
     * Keeps sensitive supplier contact and GSTIN details
     * out of audit snapshots.
     */
    private Map<String, Object> toAuditValues(Supplier supplier) {

        Map<String, Object> values = new LinkedHashMap<>();

        values.put("supplierName", supplier.supplierName());
        values.put("paymentTermsDays", supplier.paymentTermsDays());
        values.put("active", supplier.active());

        return values;
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
}
