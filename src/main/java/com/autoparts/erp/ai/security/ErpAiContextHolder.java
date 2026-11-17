package com.autoparts.erp.ai.security;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Thread-local holder for {@link ErpAiExecutionContext}.
 *
 * <p>Primarily used in unit tests and request thread contexts where execution context
 * needs to be made available to tools when not explicitly passed as an argument.
 *
 * Always clear the context after execution using {@link #clear()}.</p>
 */
public final class ErpAiContextHolder {

    private static final ThreadLocal<ErpAiExecutionContext> CONTEXT = new ThreadLocal<>();

    private ErpAiContextHolder() {
        // Utility class
    }

    public static void setContext(ErpAiExecutionContext context) {
        if (context == null) {
            clear();
        } else {
            CONTEXT.set(context);
        }
    }

    public static Optional<ErpAiExecutionContext> getContext() {
        return Optional.ofNullable(CONTEXT.get());
    }

    public static ErpAiExecutionContext requireContext() {
        return getContext().orElseThrow(() ->
                new ToolAuthorizationException("No ErpAiExecutionContext bound to current execution thread."));
    }

    public static void clear() {
        CONTEXT.remove();
    }

    /**
     * Executes the given supplier within the scope of the provided execution context,
     * ensuring proper cleanup via try-finally.
     */
    public static <T> T runWithContext(ErpAiExecutionContext context, Supplier<T> supplier) {
        ErpAiExecutionContext previous = CONTEXT.get();
        setContext(context);
        try {
            return supplier.get();
        } finally {
            if (previous != null) {
                setContext(previous);
            } else {
                clear();
            }
        }
    }
}
