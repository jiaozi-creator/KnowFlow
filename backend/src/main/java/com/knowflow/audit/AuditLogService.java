package com.knowflow.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.security.SecurityUtils;
import com.knowflow.security.UserPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

@Service
public class AuditLogService {

    private final AuditLogMapper mapper;
    private final ObjectMapper objectMapper;

    public AuditLogService(
            AuditLogMapper mapper,
            ObjectMapper objectMapper
    ) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public void record(
            Long tenantId,
            Long userId,
            String action,
            String resourceType,
            String resourceId,
            Map<String, Object> metadata
    ) throws Exception {
        if (tenantId == null || action == null || action.isBlank()) {
            return;
        }

        mapper.insertLog(
                tenantId,
                userId,
                trim(action, 80),
                trim(resourceType, 80),
                trim(resourceId, 80),
                objectMapper.writeValueAsString(metadata)
        );
    }

    public List<AuditLogView> list(Integer requestedLimit) {
        UserPrincipal user = SecurityUtils.current();

        if (!isAdmin(user.role())) {
            throw new AccessDeniedException("仅 OWNER / ADMIN 可查看审计日志");
        }

        int limit = requestedLimit == null
                ? 100
                : Math.max(1, Math.min(requestedLimit, 200));

        return mapper.listRecent(user.tenantId(), limit);
    }

    private boolean isAdmin(String role) {
        return "OWNER".equals(role) || "ADMIN".equals(role);
    }

    private String trim(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
