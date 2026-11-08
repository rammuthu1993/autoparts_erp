
package com.autoparts.erp.auth.dto;

import jakarta.validation.constraints.NotNull;

public record UserStatusRequest(

        @NotNull(message = "Enabled status is required")
        Boolean enabled
) {
}
