package com.knowflow.audit;

import com.knowflow.security.SecurityUtils;
import com.knowflow.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AuditRequestInterceptor implements HandlerInterceptor {

    private static final Pattern NUMERIC_SEGMENT =
            Pattern.compile("/(\\d+)(?:/|$)");

    private final AuditLogService auditLogService;

    public AuditRequestInterceptor(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex
    ) {
        String method = request.getMethod();

        if (!isMutating(method)) return;

        UserPrincipal user;
        try {
            user = SecurityUtils.current();
        } catch (Exception ignored) {
            return;
        }

        String uri = request.getRequestURI();

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("method", method);
        metadata.put("path", uri);
        metadata.put("httpStatus", response.getStatus());
        metadata.put("success", ex == null && response.getStatus() < 400);
        metadata.put("remoteAddress", request.getRemoteAddr());

        if (ex != null) {
            metadata.put("exception", ex.getClass().getSimpleName());
        }

        try {
            auditLogService.record(
                    user.tenantId(),
                    user.userId(),
                    classifyAction(method, uri),
                    classifyResource(uri),
                    extractResourceId(uri),
                    metadata
            );
        } catch (Exception ignored) {
            // Audit failures must not break the business request.
        }
    }

    private boolean isMutating(String method) {
        return "POST".equals(method)
                || "PUT".equals(method)
                || "PATCH".equals(method)
                || "DELETE".equals(method);
    }

    private String classifyAction(String method, String uri) {
        if (uri.contains("/repair-indexes")) return "REPAIR_INDEXES";
        if (uri.contains("/reindex")) return "REINDEX_DOCUMENT";
        if ("DELETE".equals(method)) return "DELETE";
        if ("POST".equals(method)) return "CREATE_OR_ACTION";
        return "UPDATE";
    }

    private String classifyResource(String uri) {
        if (uri.contains("/documents")) return "DOCUMENT";
        if (uri.contains("/knowledge-bases")) return "KNOWLEDGE_BASE";
        if (uri.contains("/members")) return "MEMBER";
        if (uri.contains("/departments")) return "DEPARTMENT";
        if (uri.contains("/conversations") || uri.contains("/chat")) return "CHAT";
        return "API";
    }

    private String extractResourceId(String uri) {
        Matcher matcher = NUMERIC_SEGMENT.matcher(uri);
        String last = null;
        while (matcher.find()) last = matcher.group(1);
        return last;
    }
}
