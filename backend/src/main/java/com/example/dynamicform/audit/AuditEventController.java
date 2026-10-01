package com.example.dynamicform.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit/events")
@RequiredArgsConstructor
public class AuditEventController {
    private final NamedParameterJdbcTemplate jdbc;

    @GetMapping
    public AuditPage list(@RequestParam(required = false) String actor,
                          @RequestParam(required = false) String action,
                          @RequestParam(required = false) String eventType,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                          @RequestParam(defaultValue = "0") int page,
                          @RequestParam(defaultValue = "50") int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 200);
        List<String> conditions = new ArrayList<>();
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (actor != null && !actor.isBlank()) {
            conditions.add("LOWER(actor_username) LIKE LOWER(:actor)");
            params.addValue("actor", "%" + actor.trim() + "%");
        }
        if (action != null && !action.isBlank()) {
            conditions.add("LOWER(action) LIKE LOWER(:action)");
            params.addValue("action", "%" + action.trim() + "%");
        }
        if (eventType != null && !eventType.isBlank()) {
            conditions.add("event_type=:eventType");
            params.addValue("eventType", eventType.trim());
        }
        if (from != null) {
            conditions.add("created_at>=:from");
            params.addValue("from", java.sql.Timestamp.from(from));
        }
        if (to != null) {
            conditions.add("created_at<=:to");
            params.addValue("to", java.sql.Timestamp.from(to));
        }
        String where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
        Long total = jdbc.queryForObject("SELECT count(*) FROM app_audit.audit_event" + where, params, Long.class);
        params.addValue("limit", safeSize).addValue("offset", safePage * safeSize);
        List<AuditEvent> items = jdbc.query("SELECT * FROM app_audit.audit_event" + where
                + " ORDER BY created_at DESC LIMIT :limit OFFSET :offset", params, this::map);
        long count = total == null ? 0 : total;
        return new AuditPage(items, safePage, safeSize, count, (int) Math.ceil((double) count / safeSize));
    }

    private AuditEvent map(ResultSet rs, int rowNumber) throws SQLException {
        return new AuditEvent(rs.getObject("id", UUID.class), rs.getString("event_type"), rs.getString("action"),
                rs.getString("actor_username"), rs.getString("object_type"), rs.getString("object_id"),
                rs.getString("request_id"), rs.getString("ip_address"), rs.getString("http_method"),
                rs.getString("request_path"), rs.getString("result"), rs.getString("error_code"),
                rs.getTimestamp("created_at").toInstant(), rs.getString("metadata"));
    }

    public record AuditEvent(UUID id, String eventType, String action, String actorUsername,
                             String objectType, String objectId, String requestId, String ipAddress,
                             String httpMethod, String requestPath, String result, String errorCode,
                             Instant createdAt, String metadata) {}

    public record AuditPage(List<AuditEvent> items, int page, int size, long totalElements, int totalPages) {}
}
