package com.autoparts.erp.supplier.serviceimpl;

import com.autoparts.erp.auth.security.annotation.AdminOrManager;
import com.autoparts.erp.auth.security.annotation.RequiresPermission;
import com.autoparts.erp.auth.security.annotation.SalesAccess;
import com.autoparts.erp.audit.service.AuditLogService;
import com.autoparts.erp.common.dto.PageResponse;
import com.autoparts.erp.common.exception.ApiException;
import com.autoparts.erp.common.util.PaginationUtils;
import com.autoparts.erp.supplier.dto.SupplierCreateRequest;
import com.autoparts.erp.supplier.dto.SupplierResponse;
import com.autoparts.erp.supplier.dto.SupplierSummaryResponse;
import com.autoparts.erp.supplier.dto.SupplierUpdateRequest;
import com.autoparts.erp.supplier.ledger.dto.SupplierLedgerSummary;
import com.autoparts.erp.supplier.ledger.service.SupplierLedgerService;
import com.autoparts.erp.supplier.model.Supplier;
import com.autoparts.erp.supplier.model.SupplierErrorCode;
import com.autoparts.erp.supplier.repository.SupplierRepository;
import com.autoparts.erp.supplier.service.SupplierService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class SupplierServiceImpl implements SupplierService {

    private static final Set<String> ALLOWED_SUPPLIER_TYPES =
            Set.of("MANUFACTURER", "DISTRIBUTOR", "WHOLESALER", "LOCAL_DEALER", "OTHER");

    private final SupplierRepository repository;
    private final SupplierLedgerService supplierLedgerService;
    private final AuditLogService auditLogService;

    @org.springframework.beans.factory.annotation.Autowired
    public SupplierServiceImpl(
            SupplierRepository repository,
            SupplierLedgerService supplierLedgerService,
            AuditLogService auditLogService
    ) {
        this.repository = repository;
        this.supplierLedgerService = supplierLedgerService;
        this.auditLogService = auditLogService;
    }

    public SupplierServiceImpl(
            SupplierRepository repository,
            AuditLogService auditLogService
    ) {
        this(repository, null, auditLogService);
    }

    @Override
    @Transactional
    @AdminOrManager
    @RequiresPermission("SUPPLIERS.CREATE")
    public SupplierResponse create(SupplierCreateRequest request) {
        String gstin = normalizeGstin(request.gstin());
        Supplier supplier = new Supplier(
                null,
                UUID.randomUUID(),
                null,
                cleanName(request.supplierName()),
                normalizeSupplierType(request.supplierType()),
                clean(request.contactPerson()),
                clean(request.phone()),
                clean(request.alternatePhone()),
                cleanEmail(request.email()),
                clean(request.address()),
                clean(request.city()),
                clean(request.state()),
                clean(request.pincode()),
                gstin,
                normalizeGstRegType(request.gstRegistrationType(), gstin),
                request.paymentTermsDays() == null ? 0 : request.paymentTermsDays(),
                request.creditLimit(),
                BigDecimal.ZERO,
                true,
                null,
                null
        );

        validateDuplicates(supplier.supplierName(), supplier.phone(), supplier.email(), supplier.gstin(), null);

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
                throw new ApiException(SupplierErrorCode.SUPPLIER_GSTIN_ALREADY_EXISTS);
            }
            throw new ApiException(SupplierErrorCode.INVALID_SUPPLIER_DATA);
        }
    }

    @Override
    @SalesAccess
    @RequiresPermission("SUPPLIERS.VIEW")
    public List<SupplierResponse> findAll() {
        return repository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @SalesAccess
    @RequiresPermission("SUPPLIERS.VIEW")
    public List<SupplierResponse> findAllActive() {
        return repository.findAllActive()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @SalesAccess
    @RequiresPermission("SUPPLIERS.VIEW")
    public PageResponse<SupplierResponse> findPaged(
            int page,
            int size,
            String search,
            String status,
            String city,
            String state,
            String supplierType,
            String sortBy,
            String sortDir
    ) {
        String normalizedStatus = normalizeStatusFilter(status);
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        int offset = PaginationUtils.calculateOffsetForNormalized(safePage, safeSize);

        boolean defaultActiveQuery = "ACTIVE".equals(normalizedStatus) && (supplierType == null || supplierType.isBlank());
        List<SupplierResponse> content = (defaultActiveQuery
                ? repository.findPaged(offset, safeSize, search, city, state, sortBy, sortDir)
                : repository.findPaged(offset, safeSize, search, normalizedStatus, city, state, supplierType, sortBy, sortDir)
        ).stream().map(this::toResponse).toList();

        long total = defaultActiveQuery
                ? repository.countPaged(search, city, state)
                : repository.countPaged(search, normalizedStatus, city, state, supplierType);
        return PageResponse.of(content, safePage, safeSize, total);
    }

    @Override
    @SalesAccess
    @RequiresPermission("SUPPLIERS.VIEW")
    public SupplierResponse findByUuid(UUID uuid) {
        Supplier supplier = repository.findByUuid(uuid)
                .orElseThrow(() -> new ApiException(SupplierErrorCode.SUPPLIER_NOT_FOUND));
        return toResponse(supplier);
    }

    @Override
    @SalesAccess
    @RequiresPermission("SUPPLIERS.VIEW")
    public SupplierSummaryResponse getSummary(UUID uuid) {
        Supplier supplier = repository.findByUuid(uuid)
                .orElseThrow(() -> new ApiException(SupplierErrorCode.SUPPLIER_NOT_FOUND));

        SupplierLedgerSummary ledgerSummary = supplierLedgerService
                .getSupplierLedger(uuid, null, null, 0, 1)
                .summary();
        SupplierRepository.SupplierActivityStats stats = repository.getActivityStats(supplier.id());

        return new SupplierSummaryResponse(
                supplier.uuid(),
                supplier.supplierCode(),
                supplier.supplierName(),
                supplier.active(),
                stats.totalPurchasesCount(),
                ledgerSummary.totalPurchases(),
                ledgerSummary.totalPayments(),
                ledgerSummary.totalPurchaseReturns(),
                ledgerSummary.closingBalance(),
                stats.lastPurchaseDate(),
                stats.lastPaymentDate(),
                supplier.creditLimit(),
                supplier.paymentTermsDays()
        );
    }

    @Override
    @Transactional
    @AdminOrManager
    @RequiresPermission("SUPPLIERS.UPDATE")
    public SupplierResponse update(UUID uuid, SupplierUpdateRequest request) {
        Supplier existing = repository.findByUuid(uuid)
                .orElseThrow(() -> new ApiException(SupplierErrorCode.SUPPLIER_NOT_FOUND));

        if (!existing.active()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Inactive supplier must be reactivated before editing"
            );
        }

        String gstin = normalizeGstin(request.gstin());
        Supplier updated = new Supplier(
                existing.id(),
                existing.uuid(),
                existing.supplierCode(),
                cleanName(request.supplierName()),
                request.supplierType() != null
                        ? normalizeSupplierType(request.supplierType())
                        : existing.supplierType(),
                clean(request.contactPerson()),
                clean(request.phone()),
                clean(request.alternatePhone()),
                cleanEmail(request.email()),
                clean(request.address()),
                clean(request.city()),
                clean(request.state()),
                clean(request.pincode()),
                gstin,
                normalizeGstRegType(request.gstRegistrationType(), gstin),
                request.paymentTermsDays() == null
                        ? existing.paymentTermsDays()
                        : request.paymentTermsDays(),
                request.creditLimit(),
                existing.outstandingBalance(),
                existing.active(),
                existing.createdAt(),
                existing.updatedAt()
        );

        validateDuplicates(updated.supplierName(), updated.phone(), updated.email(), updated.gstin(), uuid);

        try {
            if (!repository.update(uuid, updated)) {
                throw new ApiException(SupplierErrorCode.SUPPLIER_NOT_FOUND);
            }
        } catch (DuplicateKeyException exception) {
            if (updated.gstin() != null) {
                throw new ApiException(SupplierErrorCode.SUPPLIER_GSTIN_ALREADY_EXISTS);
            }
            throw new ApiException(SupplierErrorCode.INVALID_SUPPLIER_DATA);
        }

        Supplier saved = repository.findByUuid(uuid)
                .orElseThrow(() -> new ApiException(SupplierErrorCode.SUPPLIER_NOT_FOUND));

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
    @AdminOrManager
    @RequiresPermission("SUPPLIERS.DEACTIVATE")
    public void deactivate(UUID uuid) {
        Supplier supplier = repository.findByUuid(uuid)
                .orElseThrow(() -> new ApiException(SupplierErrorCode.SUPPLIER_NOT_FOUND));

        if (!supplier.active()) {
            throw new ApiException(SupplierErrorCode.SUPPLIER_ALREADY_INACTIVE);
        }

        if (!repository.deactivate(uuid)) {
            throw new ApiException(SupplierErrorCode.SUPPLIER_ALREADY_INACTIVE);
        }

        Map<String, Object> newValues = new LinkedHashMap<>(toAuditValues(supplier));
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

    @Override
    @Transactional
    @AdminOrManager
    @RequiresPermission("SUPPLIERS.DEACTIVATE")
    public SupplierResponse reactivate(UUID uuid) {
        Supplier supplier = repository.findByUuid(uuid)
                .orElseThrow(() -> new ApiException(SupplierErrorCode.SUPPLIER_NOT_FOUND));

        if (supplier.active()) {
            throw new ApiException(SupplierErrorCode.SUPPLIER_ALREADY_ACTIVE);
        }

        if (!repository.reactivate(uuid)) {
            throw new ApiException(SupplierErrorCode.SUPPLIER_ALREADY_ACTIVE);
        }

        Supplier saved = repository.findByUuid(uuid)
                .orElseThrow(() -> new ApiException(SupplierErrorCode.SUPPLIER_NOT_FOUND));

        auditLogService.log(
                "UPDATE",
                "SUPPLIER",
                saved.uuid().toString(),
                "Supplier reactivated: " + saved.supplierName(),
                toAuditValues(supplier),
                toAuditValues(saved)
        );

        return toResponse(saved);
    }

    private SupplierResponse toResponse(Supplier supplier) {
        return new SupplierResponse(
                supplier.id(),
                supplier.uuid(),
                supplier.supplierCode(),
                supplier.supplierName(),
                supplier.supplierType(),
                supplier.contactPerson(),
                supplier.phone(),
                supplier.alternatePhone(),
                supplier.email(),
                supplier.address(),
                supplier.city(),
                supplier.state(),
                supplier.pincode(),
                supplier.gstin(),
                supplier.gstRegistrationType(),
                supplier.paymentTermsDays(),
                supplier.creditLimit(),
                supplier.outstandingBalance(),
                supplier.active(),
                supplier.createdAt(),
                supplier.updatedAt()
        );
    }

    /**
     * Keeps sensitive supplier contact and GSTIN details out of audit snapshots.
     */
    private Map<String, Object> toAuditValues(Supplier supplier) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("supplierCode", supplier.supplierCode());
        values.put("supplierName", supplier.supplierName());
        values.put("supplierType", supplier.supplierType());
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

    private String cleanName(String value) {
        String cleaned = clean(value);
        return cleaned == null ? null : cleaned.replaceAll("\\s+", " ");
    }

    private String cleanEmail(String value) {
        String cleaned = clean(value);
        return cleaned == null ? null : cleaned.toLowerCase(Locale.ROOT);
    }

    private String normalizeGstin(String gstin) {
        String value = clean(gstin);
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }

    private String normalizeSupplierType(String type) {
        if (type == null || type.isBlank()) {
            return "DISTRIBUTOR";
        }
        String upper = type.trim().toUpperCase(Locale.ROOT);
        return ALLOWED_SUPPLIER_TYPES.contains(upper) ? upper : "DISTRIBUTOR";
    }

    private String normalizeGstRegType(String regType, String gstin) {
        String cleaned = clean(regType);
        if (cleaned != null) {
            return cleaned.toUpperCase(Locale.ROOT);
        }
        return gstin != null ? "REGULAR" : "UNREGISTERED";
    }

    private String normalizeStatusFilter(String status) {
        if (status == null || status.isBlank()) {
            return "ACTIVE";
        }
        String upper = status.trim().toUpperCase(Locale.ROOT);
        if (!upper.equals("ALL") && !upper.equals("ACTIVE") && !upper.equals("INACTIVE")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid status filter. Allowed values: ALL, ACTIVE, INACTIVE"
            );
        }
        return upper;
    }

    private void validateDuplicates(
            String supplierName,
            String phone,
            String email,
            String gstin,
            UUID excludeUuid
    ) {
        if (repository.existsBySupplierNameExcludingUuid(supplierName, excludeUuid)) {
            throw new ApiException(SupplierErrorCode.SUPPLIER_NAME_ALREADY_EXISTS);
        }
        if (repository.existsByPhoneExcludingUuid(phone, excludeUuid)) {
            throw new ApiException(SupplierErrorCode.SUPPLIER_PHONE_ALREADY_EXISTS);
        }
        if (repository.existsByEmailExcludingUuid(email, excludeUuid)) {
            throw new ApiException(SupplierErrorCode.SUPPLIER_EMAIL_ALREADY_EXISTS);
        }
        if (repository.existsByGstinExcludingUuid(gstin, excludeUuid)) {
            throw new ApiException(SupplierErrorCode.SUPPLIER_GSTIN_ALREADY_EXISTS);
        }
    }
}
