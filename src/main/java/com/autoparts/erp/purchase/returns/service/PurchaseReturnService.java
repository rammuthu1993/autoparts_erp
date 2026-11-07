package com.autoparts.erp.purchase.returns.service;

import com.autoparts.erp.purchase.returns.dto.PurchaseReturnRequest;
import com.autoparts.erp.purchase.returns.dto.PurchaseReturnResponse;
import com.autoparts.erp.purchase.returns.dto.SupplierCreditApplicationRequest;
import com.autoparts.erp.purchase.returns.dto.SupplierCreditResponse;
import com.autoparts.erp.purchase.returns.dto.SupplierRefundRequest;

import java.util.UUID;

public interface PurchaseReturnService {

    PurchaseReturnResponse createReturn(
        UUID purchaseUuid,
        PurchaseReturnRequest request
    );

    PurchaseReturnResponse findReturnByUuid(UUID returnUuid);

    SupplierCreditResponse recordRefund(
        UUID creditUuid,
        SupplierRefundRequest request
    );

    SupplierCreditResponse applyCredit(
        UUID creditUuid,
        SupplierCreditApplicationRequest request
    );
}