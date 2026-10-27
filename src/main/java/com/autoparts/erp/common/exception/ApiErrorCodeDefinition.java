package com.autoparts.erp.common.exception;

import org.springframework.http.HttpStatus;

public interface ApiErrorCodeDefinition {

    HttpStatus getStatus();

    String getMessage();

    String name();
}