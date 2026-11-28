package com.autoparts.erp.customer.serviceimpl;

import com.autoparts.erp.auth.security.annotation.AdminOrManager;
import com.autoparts.erp.auth.security.annotation.RequiresPermission;
import com.autoparts.erp.auth.security.annotation.SalesAccess;
import com.autoparts.erp.audit.service.AuditLogService;
import com.autoparts.erp.common.dto.PageResponse;
import com.autoparts.erp.common.exception.ApiException;
import com.autoparts.erp.common.util.PaginationUtils;
import com.autoparts.erp.customer.dto.CustomerCreateRequest;
import com.autoparts.erp.customer.dto.CustomerDuplicateWarning;
import com.autoparts.erp.customer.dto.CustomerResponse;
import com.autoparts.erp.customer.dto.CustomerSummaryResponse;
import com.autoparts.erp.customer.dto.CustomerUpdateRequest;
import com.autoparts.erp.customer.exception.CustomerErrorCode;
import com.autoparts.erp.customer.ledger.dto.CustomerLedgerSummary;
import com.autoparts.erp.customer.ledger.service.CustomerLedgerService;
import com.autoparts.erp.customer.model.Customer;
import com.autoparts.erp.customer.repository.CustomerRepository;
import com.autoparts.erp.customer.service.CustomerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class CustomerServiceImpl implements CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerServiceImpl.class);
    private static final Set<String> ALLOWED_CUSTOMER_TYPES =
            Set.of("RETAIL", "WHOLESALE", "CORPORATE", "DEALER", "OTHER");

    private final CustomerRepository customerRepository;
    private final CustomerLedgerService customerLedgerService;
    private final AuditLogService auditLogService;

    @org.springframework.beans.factory.annotation.Autowired
    public CustomerServiceImpl(
            CustomerRepository customerRepository,
            CustomerLedgerService customerLedgerService,
            AuditLogService auditLogService
    ) {
        this.customerRepository = customerRepository;
        this.customerLedgerService = customerLedgerService;
        this.auditLogService = auditLogService;
    }

    public CustomerServiceImpl(
            CustomerRepository customerRepository,
            AuditLogService auditLogService
    ) {
        this(customerRepository, null, auditLogService);
    }

    @Override
    @Transactional
    @AdminOrManager
    @RequiresPermission("CUSTOMERS.CREATE")
    public CustomerResponse create(CustomerCreateRequest request) {
        String gstin = normalizeGstin(request.gstin());
        if (gstin != null && customerRepository.existsByGstinExcludingUuid(gstin, null)) {
            throw new ApiException(CustomerErrorCode.CUSTOMER_GSTIN_ALREADY_EXISTS);
        }

        Customer customer = new Customer(
                null,
                UUID.randomUUID(),
                null,
                request.customerName().trim().replaceAll("\\s+", " "),
                normalizeCustomerType(request.customerType()),
                clean(request.contactPerson()),
                clean(request.phone()),
                clean(request.alternatePhone()),
                cleanEmail(request.email()),
                clean(request.address()),
                clean(request.shippingAddress()),
                clean(request.city()),
                clean(request.state()),
                clean(request.pincode()),
                gstin,
                normalizeGstRegType(request.gstRegistrationType(), gstin),
                request.creditLimit(),
                request.creditPeriodDays(),
                BigDecimal.ZERO,
                true,
                null,
                null
        );

        Customer saved = customerRepository.create(customer);

        auditLogService.log(
                "CREATE",
                "CUSTOMER",
                saved.uuid().toString(),
                "Customer created: " + saved.customerCode(),
                null,
                toAuditValues(saved)
        );

        log.info("Customer created successfully. Customer code: {}", saved.customerCode());
        return toResponse(saved);
    }

    @Override
    @SalesAccess
    @RequiresPermission("CUSTOMERS.VIEW")
    public List<CustomerResponse> findAll() {
        return customerRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @SalesAccess
    @RequiresPermission("CUSTOMERS.VIEW")
    public List<CustomerResponse> findAllActive() {
        return customerRepository.findAllActive()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @SalesAccess
    @RequiresPermission("CUSTOMERS.VIEW")
    public PageResponse<CustomerResponse> findPaged(
            int page,
            int size,
            String search,
            String status,
            String city,
            String state,
            String customerType,
            String sortBy,
            String sortDir
    ) {
        String normalizedStatus = normalizeStatusFilter(status);
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        int offset = PaginationUtils.calculateOffsetForNormalized(safePage, safeSize);

        boolean defaultActiveQuery = "ACTIVE".equals(normalizedStatus) && (customerType == null || customerType.isBlank());
        List<CustomerResponse> content = (defaultActiveQuery
                ? customerRepository.findPaged(offset, safeSize, search, city, state, sortBy, sortDir)
                : customerRepository.findPaged(offset, safeSize, search, normalizedStatus, city, state, customerType, sortBy, sortDir)
        ).stream().map(this::toResponse).toList();

        long total = defaultActiveQuery
                ? customerRepository.countPaged(search, city, state)
                : customerRepository.countPaged(search, normalizedStatus, city, state, customerType);
        return PageResponse.of(content, safePage, safeSize, total);
    }

    @Override
    @SalesAccess
    @RequiresPermission("CUSTOMERS.VIEW")
    public CustomerResponse findByUuid(UUID uuid) {
        Customer customer = customerRepository.findByUuid(uuid)
                .orElseThrow(() -> new ApiException(CustomerErrorCode.CUSTOMER_NOT_FOUND));
        return toResponse(customer);
    }

    @Override
    @SalesAccess
    @RequiresPermission("CUSTOMERS.VIEW")
    public CustomerSummaryResponse getSummary(UUID uuid) {
        Customer customer = customerRepository.findByUuid(uuid)
                .orElseThrow(() -> new ApiException(CustomerErrorCode.CUSTOMER_NOT_FOUND));

        CustomerLedgerSummary ledgerSummary = customerLedgerService
                .getCustomerLedger(uuid, null, null, 0, 1)
                .summary();
        CustomerRepository.CustomerActivityStats stats = customerRepository.getActivityStats(customer.id());

        BigDecimal outstanding = ledgerSummary.closingBalance() != null
                ? ledgerSummary.closingBalance()
                : BigDecimal.ZERO;
        BigDecimal availableCredit = customer.creditLimit() != null
                ? customer.creditLimit().subtract(outstanding.max(BigDecimal.ZERO))
                : null;

        return new CustomerSummaryResponse(
                customer.uuid(),
                customer.customerCode(),
                customer.customerName(),
                Boolean.TRUE.equals(customer.active()),
                stats.totalSalesCount(),
                ledgerSummary.totalSales(),
                ledgerSummary.totalPayments(),
                ledgerSummary.totalSalesReturns(),
                outstanding,
                stats.lastSaleDate(),
                stats.lastPaymentDate(),
                customer.creditLimit(),
                availableCredit
        );
    }

    @Override
    @SalesAccess
    @RequiresPermission("CUSTOMERS.VIEW")
    public List<CustomerDuplicateWarning> checkDuplicates(
            String phone,
            String email,
            String gstin,
            UUID excludeUuid
    ) {
        return customerRepository.findPotentialDuplicates(
                clean(phone),
                cleanEmail(email),
                normalizeGstin(gstin),
                excludeUuid
        );
    }

    @Override
    @Transactional
    @AdminOrManager
    @RequiresPermission("CUSTOMERS.UPDATE")
    public CustomerResponse update(UUID uuid, CustomerUpdateRequest request) {
        Customer existing = customerRepository.findByUuid(uuid)
                .orElseThrow(() -> new ApiException(CustomerErrorCode.CUSTOMER_NOT_FOUND));

        if (!Boolean.TRUE.equals(existing.active())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Inactive customer must be reactivated before editing"
            );
        }

        String gstin = normalizeGstin(request.gstin());
        if (gstin != null && customerRepository.existsByGstinExcludingUuid(gstin, uuid)) {
            throw new ApiException(CustomerErrorCode.CUSTOMER_GSTIN_ALREADY_EXISTS);
        }

        Customer updated = new Customer(
                existing.id(),
                existing.uuid(),
                existing.customerCode(),
                request.customerName().trim().replaceAll("\\s+", " "),
                request.customerType() != null
                        ? normalizeCustomerType(request.customerType())
                        : existing.customerType(),
                clean(request.contactPerson()),
                clean(request.phone()),
                clean(request.alternatePhone()),
                cleanEmail(request.email()),
                clean(request.address()),
                clean(request.shippingAddress()),
                clean(request.city()),
                clean(request.state()),
                clean(request.pincode()),
                gstin,
                normalizeGstRegType(request.gstRegistrationType(), gstin),
                request.creditLimit(),
                request.creditPeriodDays(),
                existing.outstandingBalance(),
                existing.active(),
                existing.createdAt(),
                existing.updatedAt()
        );

        customerRepository.update(updated);

        Customer saved = customerRepository.findByUuid(uuid)
                .orElseThrow(() -> new ApiException(CustomerErrorCode.CUSTOMER_NOT_FOUND));

        auditLogService.log(
                "UPDATE",
                "CUSTOMER",
                saved.uuid().toString(),
                "Customer updated: " + saved.customerCode(),
                toAuditValues(existing),
                toAuditValues(saved)
        );

        log.info("Customer updated successfully. Customer code: {}", saved.customerCode());
        return toResponse(saved);
    }

    @Override
    @Transactional
    @AdminOrManager
    @RequiresPermission("CUSTOMERS.DEACTIVATE")
    public void deactivate(UUID uuid) {
        Customer customer = customerRepository.findByUuid(uuid)
                .orElseThrow(() -> new ApiException(CustomerErrorCode.CUSTOMER_NOT_FOUND));

        if (!Boolean.TRUE.equals(customer.active())) {
            throw new ApiException(CustomerErrorCode.CUSTOMER_ALREADY_INACTIVE);
        }

        boolean deactivated = customerRepository.deactivate(customer.id());
        if (!deactivated) {
            throw new ApiException(CustomerErrorCode.CUSTOMER_ALREADY_INACTIVE);
        }

        Map<String, Object> newValues = new LinkedHashMap<>(toAuditValues(customer));
        newValues.put("active", false);

        auditLogService.log(
                "DELETE",
                "CUSTOMER",
                customer.uuid().toString(),
                "Customer deactivated: " + customer.customerCode(),
                toAuditValues(customer),
                newValues
        );

        log.info("Customer deactivated. Customer code: {}", customer.customerCode());
    }

    @Override
    @Transactional
    @AdminOrManager
    @RequiresPermission("CUSTOMERS.DEACTIVATE")
    public CustomerResponse reactivate(UUID uuid) {
        Customer customer = customerRepository.findByUuid(uuid)
                .orElseThrow(() -> new ApiException(CustomerErrorCode.CUSTOMER_NOT_FOUND));

        if (Boolean.TRUE.equals(customer.active())) {
            throw new ApiException(CustomerErrorCode.CUSTOMER_ALREADY_ACTIVE);
        }

        boolean reactivated = customerRepository.reactivate(customer.id());
        if (!reactivated) {
            throw new ApiException(CustomerErrorCode.CUSTOMER_ALREADY_ACTIVE);
        }

        Customer saved = customerRepository.findByUuid(uuid)
                .orElseThrow(() -> new ApiException(CustomerErrorCode.CUSTOMER_NOT_FOUND));

        auditLogService.log(
                "UPDATE",
                "CUSTOMER",
                saved.uuid().toString(),
                "Customer reactivated: " + saved.customerCode(),
                toAuditValues(customer),
                toAuditValues(saved)
        );

        log.info("Customer reactivated. Customer code: {}", saved.customerCode());
        return toResponse(saved);
    }

    private CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(
                customer.uuid(),
                customer.customerCode(),
                customer.customerName(),
                customer.customerType(),
                customer.contactPerson(),
                customer.phone(),
                customer.alternatePhone(),
                customer.email(),
                customer.address(),
                customer.shippingAddress(),
                customer.city(),
                customer.state(),
                customer.pincode(),
                customer.gstin(),
                customer.gstRegistrationType(),
                customer.creditLimit(),
                customer.creditPeriodDays(),
                customer.outstandingBalance(),
                customer.active(),
                customer.createdAt(),
                customer.updatedAt()
        );
    }

    /**
     * Avoid storing customer contact details and GSTIN unnecessarily in audit logs.
     */
    private Map<String, Object> toAuditValues(Customer customer) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("customerCode", customer.customerCode());
        values.put("customerName", customer.customerName());
        values.put("customerType", customer.customerType());
        values.put("active", customer.active());
        return values;
    }

    private String clean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String cleanEmail(String value) {
        String cleaned = clean(value);
        return cleaned == null ? null : cleaned.toLowerCase(Locale.ROOT);
    }

    private String normalizeGstin(String gstin) {
        String value = clean(gstin);
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }

    private String normalizeCustomerType(String type) {
        if (type == null || type.isBlank()) {
            return "RETAIL";
        }
        String upper = type.trim().toUpperCase(Locale.ROOT);
        return ALLOWED_CUSTOMER_TYPES.contains(upper) ? upper : "RETAIL";
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
}
