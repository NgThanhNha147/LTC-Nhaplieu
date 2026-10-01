-- Business data must be changed through assigned work items. The legacy
-- template record endpoints remain read-only for administrators and exports.
UPDATE app_meta.sec_api_resource
SET function_code = 'RECORD_EXPORT'
WHERE (http_method = 'GET' AND path_pattern = '/api/v1/templates/*/records/*')
   OR (http_method = 'POST' AND path_pattern = '/api/v1/templates/*/records/search');

UPDATE app_meta.sec_api_resource
SET active = FALSE
WHERE (http_method = 'POST' AND path_pattern = '/api/v1/templates/*/records')
   OR (http_method = 'PUT' AND path_pattern = '/api/v1/templates/*/records/*')
   OR (http_method = 'DELETE' AND path_pattern = '/api/v1/templates/*/records/*');

-- The assigned data-entry user may move an incorrect, editable work item to
-- the recycle bin. Ownership and workflow state are enforced in the service.
INSERT INTO app_meta.sec_role_function (role_id, function_id, assigned_by)
SELECT r.id, f.id, 'flyway'
FROM app_meta.sec_role r
JOIN app_meta.sec_function f ON f.code = 'RECORD_DELETE'
WHERE r.code = 'DATA_ENTRY'
ON CONFLICT DO NOTHING;

-- The POC is intentionally operated with two roles. Keep the definitions for
-- future expansion, but hide and disable them until the business needs them.
UPDATE app_meta.sec_role
SET active = FALSE,
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'flyway',
    row_version = row_version + 1
WHERE code IN ('FORM_ADMIN', 'DATA_MANAGER', 'REVIEWER', 'AUDITOR', 'BACKUP_OPERATOR');

