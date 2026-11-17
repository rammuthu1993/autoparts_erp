package com.autoparts.erp.ai.security;

import org.springframework.security.access.AccessDeniedException;

/**
 * Typed authorization exception thrown when an AI tool execution is attempted
 * by a subject lacking the required roles/authorities.
 *
 * <p>Thrown before any database or JDBC call is made.</p>
 */
public class ToolAuthorizationException extends AccessDeniedException {

    public ToolAuthorizationException(String msg) {
        super(msg);
    }

    public ToolAuthorizationException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
