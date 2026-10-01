-- System administrators supervise configuration and workflow but do not enter
-- or delete business data. Data changes belong to the assigned data-entry user.
DELETE FROM app_meta.sec_role_function rf
USING app_meta.sec_role r, app_meta.sec_function f
WHERE rf.role_id = r.id
  AND rf.function_id = f.id
  AND r.code = 'SYSTEM_ADMIN'
  AND f.code IN ('RECORD_CREATE', 'RECORD_UPDATE', 'RECORD_DELETE', 'RECORD_COMPLETE', 'RECORD_SUBMIT');
