package com.example.dynamicform.schema;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public class TemplateOperationLock {
    private static final int LOCK_NAMESPACE = 764231;
    private final JdbcTemplate jdbc;

    public void lockForDataChange(String templateCode) {
        jdbc.queryForObject("SELECT pg_advisory_xact_lock_shared(?, hashtext(?))", Object.class,
                LOCK_NAMESPACE, templateCode.toUpperCase(Locale.ROOT));
    }

    public void lockForPublish(String templateCode) {
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(?, hashtext(?))", Object.class,
                LOCK_NAMESPACE, templateCode.toUpperCase(Locale.ROOT));
    }
}
