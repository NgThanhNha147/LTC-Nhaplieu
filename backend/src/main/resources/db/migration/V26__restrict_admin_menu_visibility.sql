DELETE FROM app_meta.sec_menu_function mf
USING app_meta.sec_menu m, app_meta.sec_function f
WHERE mf.menu_id = m.id
  AND mf.function_id = f.id
  AND ((m.code = 'TEMPLATES' AND f.code = 'TEMPLATE_VIEW')
    OR (m.code = 'LOOKUPS' AND f.code = 'LOOKUP_VIEW'));

INSERT INTO app_meta.sec_menu_function (menu_id, function_id)
SELECT m.id, f.id
FROM app_meta.sec_menu m
JOIN app_meta.sec_function f ON
    (m.code = 'TEMPLATES' AND f.code = 'TEMPLATE_CONFIGURE')
    OR (m.code = 'LOOKUPS' AND f.code = 'LOOKUP_MANAGE')
ON CONFLICT DO NOTHING;
