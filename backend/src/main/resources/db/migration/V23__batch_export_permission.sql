INSERT INTO app_meta.sec_api_resource (id, http_method, path_pattern, function_code, priority) VALUES
    ('40000000-0000-0000-0000-000000000075', 'GET', '/api/v1/batches/*/export', 'RECORD_EXPORT', 3)
ON CONFLICT (http_method, path_pattern) DO UPDATE SET
    function_code = EXCLUDED.function_code,
    priority = EXCLUDED.priority,
    active = TRUE;
