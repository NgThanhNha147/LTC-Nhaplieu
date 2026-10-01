package com.example.dynamicform.security;

import com.example.dynamicform.audit.AuditEventService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class SecurityAuditHandlers {
    private final AuditEventService auditEvents;
    private final ObjectMapper objectMapper;

    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, exception) -> {
            auditDenied(request, "AUTHENTICATION_REQUIRED", "UNAUTHORIZED");
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED",
                    "Vui long dang nhap de tiep tuc");
        };
    }

    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, exception) -> {
            auditDenied(request, "ACCESS_DENIED", "FORBIDDEN");
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "ACCESS_DENIED",
                    "Ban khong co quyen thuc hien thao tac nay");
        };
    }

    private void auditDenied(HttpServletRequest request, String action, String errorCode) {
        try {
            auditEvents.log("AUTHORIZATION", action, "API", request.getRequestURI(), "DENIED", errorCode,
                    Map.of("httpMethod", request.getMethod()));
        } catch (RuntimeException error) {
            log.error("Unable to audit denied request {} {}", request.getMethod(), request.getRequestURI(), error);
        }
    }

    private void writeError(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), Map.of("code", code, "message", message));
    }
}
