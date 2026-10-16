-- Document-first intake is a standalone upload flow, so data-entry users need
-- the generic document permissions in addition to DOCUMENT_ANALYZE.
INSERT INTO app_meta.sec_role_function (role_id, function_id, assigned_by)
SELECT r.id, f.id, 'flyway'
FROM app_meta.sec_role r
JOIN app_meta.sec_function f ON f.code IN ('DOCUMENT_VIEW', 'DOCUMENT_UPLOAD')
WHERE r.code = 'DATA_ENTRY'
ON CONFLICT DO NOTHING;
