package com.autoparts.erp.settings.dto;

import java.time.Instant;
import java.util.List;

public record RuntimeStatusResponse(
        String applicationName,
        List<String> activeProfiles,
        boolean databaseConnected,
        boolean mailEnabled,
        boolean mailHostConfigured,
        String businessTimeZone,
        Instant checkedAt
) {}
