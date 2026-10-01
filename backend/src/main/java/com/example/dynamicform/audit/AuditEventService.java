package com.example.dynamicform.audit;

import com.example.dynamicform.common.CurrentUser;
import com.example.dynamicform.common.RequestCorrelationFilter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditEventService {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final CurrentUser currentUser;

    public void log(String eventType, String action, String objectType, String objectId,
                    String result, String errorCode, Map<String, Object> metadata) {
        HttpServletRequest request = currentRequest();
        String requestId = request == null ? null
                : String.valueOf(request.getAttribute(RequestCorrelationFilter.REQUEST_ID_ATTRIBUTE));
        String sql = "INSERT INTO app_audit.audit_event(" +
                "id,event_type,action,actor_username,object_type,object_id,metadata,request_id," +
                "ip_address,user_agent,http_method,request_path,result,error_code,created_at) " +
                "VALUES(:id,:eventType,:action,:actor,:objectType,:objectId,CAST(:metadata AS jsonb),:requestId," +
                ":ip,:userAgent,:method,:path,:result,:errorCode,:createdAt)";
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("eventType", limit(eventType, 50))
                .addValue("action", limit(action, 120))
                .addValue("actor", limit(currentUser.username(), 100))
                .addValue("objectType", limit(objectType, 100))
                .addValue("objectId", limit(objectId, 250))
                .addValue("metadata", json(metadata))
                .addValue("requestId", normalizeNullable(requestId, 100))
                .addValue("ip", request == null ? null : limit(clientIp(request), 64))
                .addValue("userAgent", request == null ? null : limit(request.getHeader("User-Agent"), 1000))
                .addValue("method", request == null ? null : limit(request.getMethod(), 10))
                .addValue("path", request == null ? null : limit(request.getRequestURI(), 1000))
                .addValue("result", limit(result, 20))
                .addValue("errorCode", limit(errorCode, 100))
                .addValue("createdAt", Timestamp.from(Instant.now())));
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        return request.getRemoteAddr();
    }

    private String json(Map<String, Object> value) {
        if (value == null || value.isEmpty()) return "{}";
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private String normalizeNullable(String value, int max) {
        if (value == null || value.isBlank() || "null".equals(value)) return null;
        return limit(value, max);
    }

    private String limit(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}

