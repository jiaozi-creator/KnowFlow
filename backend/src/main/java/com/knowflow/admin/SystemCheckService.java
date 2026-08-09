package com.knowflow.admin;

import com.knowflow.security.SecurityUtils;
import com.knowflow.security.UserPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class SystemCheckService {

    private final SystemCheckMapper mapper;

    public SystemCheckService(SystemCheckMapper mapper) {
        this.mapper = mapper;
    }

    public SystemCheckView check() {
        UserPrincipal user = SecurityUtils.current();

        if (!"OWNER".equals(user.role()) && !"ADMIN".equals(user.role())) {
            throw new AccessDeniedException(
                    "仅 OWNER / ADMIN 可执行系统完整性检查"
            );
        }

        Long tenantId = user.tenantId();

        return new SystemCheckView(
                mapper.orphanChunks(tenantId),
                mapper.nonCurrentChunks(tenantId),
                mapper.readyDocumentsWithoutChunks(tenantId),
                mapper.activeIngestionTasks(tenantId),
                mapper.failedDocuments(tenantId),
                mapper.needsReindexDocuments(tenantId)
        );
    }
}
