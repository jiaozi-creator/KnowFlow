package com.knowflow.audit;

import com.knowflow.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {

    private final AuditLogService service;

    public AuditLogController(AuditLogService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<AuditLogView>> list(
            @RequestParam(defaultValue = "100") Integer limit
    ) {
        return ApiResponse.ok(service.list(limit));
    }
}
