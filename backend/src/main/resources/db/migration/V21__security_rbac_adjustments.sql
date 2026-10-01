UPDATE app_meta.sec_api_resource
SET function_code = 'TEMPLATE_VIEW'
WHERE http_method = 'GET' AND path_pattern = '/api/v1/forms/**';
