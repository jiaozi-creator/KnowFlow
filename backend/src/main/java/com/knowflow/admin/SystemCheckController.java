package com.knowflow.admin;

import com.knowflow.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/system-check")
public class SystemCheckController {

    private final SystemCheckService service;

    public SystemCheckController(SystemCheckService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<SystemCheckView> check() {
        return ApiResponse.ok(service.check());
    }
}
