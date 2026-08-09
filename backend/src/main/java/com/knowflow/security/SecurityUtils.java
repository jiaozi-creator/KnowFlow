package com.knowflow.security;

import com.knowflow.common.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {
    private SecurityUtils() {}

    public static UserPrincipal current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw BusinessException.unauthorized("登录状态已失效");
        }
        return principal;
    }

    public static boolean isTenantAdmin() {
        String role = current().role();
        return "OWNER".equals(role) || "ADMIN".equals(role);
    }
}
