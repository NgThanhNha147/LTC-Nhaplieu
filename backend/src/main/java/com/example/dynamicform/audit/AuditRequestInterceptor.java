package com.example.dynamicform.audit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class AuditRequestInterceptor implements HandlerInterceptor {
    private static final Logger log = LoggerFactory.getLogger(AuditRequestInterceptor.class);
    private static final String STARTED_AT = AuditRequestInterceptor.class.getName() + ".startedAt";
    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private final AuditEventService auditEvents;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(STARTED_AT, System.nanoTime());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (!shouldAudit(request)) return;
        long durationMs = durationMs(request);
        int status = response.getStatus();
        String result = status < 400 && ex == null ? "SUCCESS" : "FAILURE";
        String action = request.getMethod() + " " + normalizedPath(request.getRequestURI());
        try {
            auditEvents.log(eventType(request), action, "API", request.getRequestURI(), result,
                    ex == null ? null : ex.getClass().getSimpleName(),
                    Map.of("status", status, "durationMs", durationMs));
        } catch (RuntimeException auditError) {
            log.error("Unable to write audit event for {} {}", request.getMethod(), request.getRequestURI(), auditError);
        }
    }

    private boolean shouldAudit(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/")) return false;
        if (path.endsWith("/records/search")) return false;
        return MUTATING_METHODS.contains(request.getMethod())
                || path.contains("/export")
                || path.matches(".*/documents/[^/]+/(content|ocr/content)$")
                || path.startsWith("/api/v1/audit");
    }

    private String eventType(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.contains("/auth/")) return "AUTHENTICATION";
        if (path.contains("/export")) return "DATA_EXPORT";
        if (path.contains("/documents")) return "DOCUMENT";
        if (path.contains("/admin/")) return "ADMINISTRATION";
        return "BUSINESS_OPERATION";
    }

    private long durationMs(HttpServletRequest request) {
        Object started = request.getAttribute(STARTED_AT);
        if (!(started instanceof Long value)) return 0;
        return Math.max(0, (System.nanoTime() - value) / 1_000_000);
    }

    private String normalizedPath(String path) {
        return path.replaceAll("/[0-9a-fA-F]{8}-[0-9a-fA-F-]{27,}", "/{id}");
    }
}

