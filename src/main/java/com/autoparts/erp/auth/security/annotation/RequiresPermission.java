package com.autoparts.erp.auth.security.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enforces permission-based access control (PBAC) at method or class level.
 * Can be applied to Spring MVC controllers or service layer methods.
 * Evaluated by {@link com.autoparts.erp.auth.security.aspect.RequiresPermissionAspect}.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface RequiresPermission {

    /**
     * The permission code required to execute this operation (e.g. "PRODUCTS.CREATE", "SALES.CANCEL").
     */
    String value();
}
