INSERT INTO app_meta.sec_menu (id, code, label, path, icon, display_order, active)
VALUES ('30000000-0000-0000-0000-000000000012', 'OVERVIEW', 'Tổng quan', '/overview', 'HomeOutlined', 1, TRUE)
ON CONFLICT (code) DO UPDATE
SET label = EXCLUDED.label,
    path = EXCLUDED.path,
    icon = EXCLUDED.icon,
    display_order = EXCLUDED.display_order,
    active = TRUE;

INSERT INTO app_meta.sec_menu_function (menu_id, function_id)
SELECT m.id, f.id
FROM app_meta.sec_menu m
JOIN app_meta.sec_function f ON f.code = 'BATCH_VIEW'
WHERE m.code = 'OVERVIEW'
ON CONFLICT DO NOTHING;
