CREATE TABLE app_meta.document_analysis (
    id UUID PRIMARY KEY,
    document_file_id UUID NOT NULL REFERENCES app_meta.document_file(id) ON DELETE CASCADE,
    status VARCHAR(30) NOT NULL,
    source_kind VARCHAR(30),
    document_type VARCHAR(255),
    confidence NUMERIC(5,4),
    extracted_text TEXT,
    suggested_schema JSONB NOT NULL DEFAULT '{"groups":[]}'::jsonb,
    reviewed_schema JSONB,
    matched_template_version_id UUID REFERENCES app_meta.template_version(id),
    match_confidence NUMERIC(5,4),
    error_message VARCHAR(2000),
    created_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(100) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by VARCHAR(100) NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_document_analysis_status CHECK (
        status IN ('PROCESSING', 'COMPLETED', 'REVIEWED', 'PROMOTED', 'FAILED')
    ),
    CONSTRAINT ck_document_analysis_source CHECK (
        source_kind IS NULL OR source_kind IN ('ACROFORM', 'DIGITAL_TEXT', 'SCAN_TEXT', 'IMAGE', 'UNKNOWN')
    )
);

CREATE TABLE app_meta.document_draft_record (
    id UUID PRIMARY KEY,
    analysis_id UUID NOT NULL UNIQUE REFERENCES app_meta.document_analysis(id) ON DELETE CASCADE,
    schema_snapshot JSONB NOT NULL,
    data JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(100) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by VARCHAR(100) NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_document_draft_record_status CHECK (status IN ('DRAFT', 'CONFIRMED', 'PROMOTED'))
);

CREATE INDEX idx_document_analysis_document ON app_meta.document_analysis(document_file_id, created_at DESC);
CREATE INDEX idx_document_analysis_status ON app_meta.document_analysis(status, updated_at DESC);
CREATE INDEX idx_document_analysis_template ON app_meta.document_analysis(matched_template_version_id);
CREATE INDEX idx_document_analysis_schema_gin ON app_meta.document_analysis USING GIN(suggested_schema);
CREATE INDEX idx_document_draft_data_gin ON app_meta.document_draft_record USING GIN(data);

INSERT INTO app_meta.sec_function (id, code, name, function_group)
VALUES ('10000000-0000-0000-0000-000000000041', 'DOCUMENT_ANALYZE', 'Phân tích và đề xuất biểu mẫu từ tài liệu', 'Tài liệu')
ON CONFLICT (code) DO NOTHING;

INSERT INTO app_meta.sec_role_function (role_id, function_id, assigned_by)
SELECT r.id, f.id, 'flyway'
FROM app_meta.sec_role r
JOIN app_meta.sec_function f ON f.code = 'DOCUMENT_ANALYZE'
WHERE r.code IN ('SYSTEM_ADMIN', 'FORM_ADMIN', 'DATA_ENTRY', 'DATA_MANAGER')
ON CONFLICT DO NOTHING;

INSERT INTO app_meta.sec_role_function (role_id, function_id, assigned_by)
SELECT r.id, f.id, 'flyway'
FROM app_meta.sec_role r
JOIN app_meta.sec_function f ON f.code IN ('DOCUMENT_VIEW', 'DOCUMENT_UPLOAD')
WHERE r.code = 'FORM_ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO app_meta.sec_menu (id, code, label, path, icon, display_order)
VALUES ('30000000-0000-0000-0000-000000000013', 'DOCUMENT_INTAKE', 'Phân tích tài liệu', '/document-intake', 'FileSearchOutlined', 15)
ON CONFLICT (code) DO UPDATE SET label = EXCLUDED.label, path = EXCLUDED.path, icon = EXCLUDED.icon, display_order = EXCLUDED.display_order;

INSERT INTO app_meta.sec_menu_function (menu_id, function_id)
SELECT '30000000-0000-0000-0000-000000000013', id
FROM app_meta.sec_function
WHERE code = 'DOCUMENT_ANALYZE'
ON CONFLICT DO NOTHING;

INSERT INTO app_meta.sec_api_resource (id, http_method, path_pattern, function_code, priority) VALUES
    ('40000000-0000-0000-0000-000000000091', 'POST', '/api/v1/documents/*/analyses', 'DOCUMENT_ANALYZE', 3),
    ('40000000-0000-0000-0000-000000000092', 'GET', '/api/v1/document-analyses/**', 'DOCUMENT_ANALYZE', 3),
    ('40000000-0000-0000-0000-000000000093', 'PUT', '/api/v1/document-analyses/*/review', 'DOCUMENT_ANALYZE', 3),
    ('40000000-0000-0000-0000-000000000094', 'POST', '/api/v1/document-analyses/*/promote', 'TEMPLATE_CREATE', 3)
ON CONFLICT (http_method, path_pattern) DO UPDATE
SET function_code = EXCLUDED.function_code, priority = EXCLUDED.priority, active = TRUE;
