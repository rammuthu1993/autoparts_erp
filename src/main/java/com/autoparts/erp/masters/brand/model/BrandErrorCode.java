
package com.autoparts.erp.masters.brand.model;

import com.autoparts.erp.common.exception.ApiErrorCodeDefinition;
import org.springframework.http.HttpStatus;

public enum BrandErrorCode implements ApiErrorCodeDefinition {

    BRAND_ALREADY_EXISTS(
            HttpStatus.CONFLICT,
            "Product brand already exists"
    ),

    BRAND_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "Product brand not found"
    ),

    INVALID_BRAND_DATA(
            HttpStatus.BAD_REQUEST,
            "Invalid product brand data"
    );

    private final HttpStatus status;
    private final String message;

    BrandErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    @Override
    public HttpStatus getStatus() {
        return status;
    }

    @Override
    public String getMessage() {
        return message;
    }
}







//package com.autoparts.erp.masters.product.model;
//
//import org.springframework.http.HttpStatus;
//
//public enum ProductErrorCode {
//
//    PRODUCT_ALREADY_EXISTS(
//            HttpStatus.CONFLICT,
//            "Product already exists"
//    ),
//
//    PRODUCT_NOT_FOUND(
//            HttpStatus.NOT_FOUND,
//            "Product not found"
//    ),
//
//    INVALID_PRODUCT_DATA(
//            HttpStatus.BAD_REQUEST,
//            "Invalid product data"
//    );
//
//    private final HttpStatus status;
//    private final String message;
//
//    ProductErrorCode(HttpStatus status, String message) {
//        this.status = status;
//        this.message = message;
//    }
//
//    public HttpStatus getStatus() {
//        return status;
//    }
//
//    public String getMessage() {
//        return message;
//    }
//}
//
