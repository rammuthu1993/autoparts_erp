package com.autoparts.erp.auth.security.aspect;

import java.lang.reflect.Method;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.autoparts.erp.auth.security.annotation.RequiresPermission;
import com.autoparts.erp.auth.service.PermissionService;

@Aspect
@Component
public class RequiresPermissionAspect {

    private final PermissionService permissionService;

    public RequiresPermissionAspect(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @Before("@annotation(com.autoparts.erp.auth.security.annotation.RequiresPermission) || @within(com.autoparts.erp.auth.security.annotation.RequiresPermission)")
    public void checkPermission(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // 1. Resolve method-level annotation first, then fallback to class-level
        RequiresPermission annotation = AnnotationUtils.findAnnotation(method, RequiresPermission.class);
        if (annotation == null) {
            annotation = AnnotationUtils.findAnnotation(joinPoint.getTarget().getClass(), RequiresPermission.class);
        }

        if (annotation == null) {
            return;
        }

        String requiredPermission = annotation.value();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getName())) {
            throw new AccessDeniedException("Authentication required to access this resource.");
        }

        boolean hasAccess = permissionService.hasPermission(authentication, requiredPermission);
        if (!hasAccess) {
            throw new AccessDeniedException("You do not have permission to perform this action.");
        }
    }
}
