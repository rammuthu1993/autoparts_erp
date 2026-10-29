
package com.autoparts.erp.masters.supplier.repository;

import com.autoparts.erp.masters.supplier.model.Supplier;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupplierRepository {

    Supplier create(Supplier supplier);

    List<Supplier> findAll();

    Optional<Supplier> findByUuid(UUID uuid);

    boolean update(UUID uuid, Supplier supplier);

    boolean deactivate(UUID uuid);
    

	boolean existsBySupplierName(String supplierName);
	
	boolean existsByPhone(String phone);
	
	boolean existsByEmail(String email);
	
	boolean existsByGstin(String gstin);
	
//	boolean existsBySupplierNameExcludingUuid(
//	    String supplierName, UUID uuid
//	);
//	
//	boolean existsByPhoneExcludingUuid(
//	    String phone, UUID uuid
//	);
//	
//	boolean existsByEmailExcludingUuid(
//	    String email, UUID uuid
//	);
//	
//	boolean existsByGstinExcludingUuid(
//	    String gstin, UUID uuid
//	);

}
