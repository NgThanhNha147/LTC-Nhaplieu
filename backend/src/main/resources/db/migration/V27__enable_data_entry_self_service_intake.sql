INSERT INTO app_meta.sec_role_function (role_id, function_id, assigned_by)
SELECT r.id, f.id, 'flyway'
FROM app_meta.sec_role r
JOIN app_meta.sec_function f ON f.code IN ('BATCH_CREATE', 'BATCH_UPLOAD')
WHERE r.code = 'DATA_ENTRY'
ON CONFLICT DO NOTHING;

-- Batch uploads use their own permission. The generic document upload permission
-- is not needed by the guided data-entry workflow.
DELETE FROM app_meta.sec_role_function rf
USING app_meta.sec_role r, app_meta.sec_function f
WHERE rf.role_id = r.id
  AND rf.function_id = f.id
  AND r.code = 'DATA_ENTRY'
  AND f.code = 'DOCUMENT_UPLOAD';
