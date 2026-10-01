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

import java.time.Instant;
import java.sql.Timestamp;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditService {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final CurrentUser currentUser;

    public void log(UUID versionId, UUID recordId, String action, Map<String, Object> oldData, Map<String, Object> newData) {
        String sql = "INSERT INTO app_audit.data_change_log(id,template_version_id,record_id,action,old_data,new_data,changed_by,changed_at,request_id) " +
                "VALUES(:id,:versionId,:recordId,:action,CAST(:oldData AS jsonb),CAST(:newData AS jsonb),:user,:now,:requestId)";
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID()).addValue("versionId", versionId).addValue("recordId", recordId)
                .addValue("action", action).addValue("oldData", json(oldData)).addValue("newData", json(newData))
                .addValue("user", currentUser.username()).addValue("now", Timestamp.from(Instant.now()))
                .addValue("requestId", requestId()));
    }

    private String json(Map<String, Object> value) {
        if (value == null) return null;
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Không thể ghi audit log", e); }
    }

    private String requestId() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            Object value = request.getAttribute(RequestCorrelationFilter.REQUEST_ID_ATTRIBUTE);
            return value == null ? null : value.toString();
        }
        return null;
    }
}
