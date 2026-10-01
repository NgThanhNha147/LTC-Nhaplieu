UPDATE app_meta.sec_menu
SET label = 'Mẫu hồ sơ', path = '/workspaces', icon = 'AppstoreOutlined', display_order = 5, active = TRUE
WHERE code = 'RECORDS';

UPDATE app_meta.sec_menu
SET label = 'Việc của tôi', display_order = 6, active = TRUE
WHERE code = 'MY_WORK';

UPDATE app_meta.sec_menu
SET label = 'Cấu hình biểu mẫu'
WHERE code = 'TEMPLATES';

UPDATE app_meta.sec_menu
SET active = FALSE
WHERE code IN ('BATCHES', 'MY_BATCHES');

DELETE FROM app_meta.sec_role_function rf
USING app_meta.sec_role r, app_meta.sec_function f
WHERE rf.role_id = r.id
  AND rf.function_id = f.id
  AND r.code = 'DATA_ENTRY'
  AND f.code = 'RECORD_CREATE';
