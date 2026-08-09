package com.knowflow.audit;

import lombok.Data;
import java.time.OffsetDateTime;

@Data
public class AuditLogView {
    private Long id;
    private Long tenantId;
    private Long userId;
    private String action;
    private String resourceType;
    private String resourceId;
    private String metadata;
    private OffsetDateTime createdAt;
}
