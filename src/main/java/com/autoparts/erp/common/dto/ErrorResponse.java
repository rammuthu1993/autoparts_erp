
package com.autoparts.erp.common.dto;

import java.time.OffsetDateTime;

public record ErrorResponse(
        OffsetDateTime timestamp,
        int status,
        String errorCode,
        String message,
        String path
) {}
