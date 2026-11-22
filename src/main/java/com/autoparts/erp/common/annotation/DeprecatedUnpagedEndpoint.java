package com.autoparts.erp.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Metadata annotation for deprecated unpaged collection endpoints subject to safety limit capping.
 *
 * <p>Explicitly defines the configured SQL safety limit, alternate paginated endpoint URL,
 * RFC 9745 deprecation timestamp/date, and optional RFC 8594 sunset removal date.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DeprecatedUnpagedEndpoint {

    /**
     * The configured SQL safety limit for this legacy endpoint (e.g. 50, 100, 500).
     *
     * @return safety limit ceiling
     */
    int limit() default 500;

    /**
     * Replacement paginated endpoint URL path (e.g. "/api/v1/customers/paged").
     * If empty, no alternate Link header is emitted.
     *
     * @return replacement endpoint path, or empty string if none exists
     */
    String pagedUrl() default "";

    /**
     * Real configured deprecation timestamp or date for RFC 9745.
     * Accepted formats:
     * <ul>
     *   <li>Unix epoch seconds prefixed with '@' (e.g. "@1732233600")</li>
     *   <li>Numeric epoch seconds (e.g. "1732233600")</li>
     *   <li>ISO-8601 UTC date/time (e.g. "2026-11-22T00:00:00Z" or "2026-11-22")</li>
     * </ul>
     * If empty or unparseable, the standard Deprecation header is omitted.
     *
     * @return deprecation date or epoch representation
     */
    String deprecationDate() default "";

    /**
     * Future sunset / removal date for RFC 8594.
     * Emitted as an HTTP-date (IMF-fixdate) per RFC 8594 / RFC 7231.
     * If empty or unparseable, the Sunset header is omitted.
     *
     * @return sunset removal date representation
     */
    String sunsetDate() default "";
}
