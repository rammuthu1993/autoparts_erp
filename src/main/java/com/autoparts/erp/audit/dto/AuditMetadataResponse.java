package com.autoparts.erp.audit.dto;

import java.util.List;

public record AuditMetadataResponse(
        List<String> actions,
        List<String> entityTypes
) {}
