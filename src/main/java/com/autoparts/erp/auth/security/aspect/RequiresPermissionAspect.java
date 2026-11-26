package com.autoparts.erp.auth.security.aspect;

import java.lang.reflect.Method;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.BridgeMethodResolver;
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
        if (!(joinPoint.getSignature() instanceof MethodSignature signature)) {
            return;
        }

        Class<?> targetClass = joinPoint.getTarget() != null ? joinPoint.getTarget().getClass() : signature.getDeclaringType();
        Method method = signature.getMethod();
        Method specificMethod = AopUtils.getMostSpecificMethod(method, targetClass);
        Method targetMethod = BridgeMethodResolver.findBridgedMethod(specificMethod);

        // 1. Resolve method-level annotation on target implementation method (highest precedence)
        RequiresPermission annotation = AnnotationUtils.findAnnotation(targetMethod, RequiresPermission.class);

        // 2. Fallback to method-level annotation on interface/declaring method
        if (annotation == null && !targetMethod.equals(method)) {
            annotation = AnnotationUtils.findAnnotation(method, RequiresPermission.class);
        }

        // 3. Fallback to class-level annotation on target class
        if (annotation == null) {
            annotation = AnnotationUtils.findAnnotation(targetClass, RequiresPermission.class);
        }

        // 4. Fallback to class-level annotation on declaring interface
        if (annotation == null && signature.getDeclaringType() != null) {
            annotation = AnnotationUtils.findAnnotation(signature.getDeclaringType(), RequiresPermission.class);
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
