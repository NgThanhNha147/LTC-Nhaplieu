CREATE TABLE app_meta.ingestion_batch (
    id UUID PRIMARY KEY,
    batch_code VARCHAR(40) NOT NULL UNIQUE,
    batch_name VARCHAR(255) NOT NULL,
    template_version_id UUID NOT NULL REFERENCES app_meta.template_version(id),
    source_type VARCHAR(30),
    original_name VARCHAR(500),
    assigned_user_id UUID REFERENCES app_meta.sec_user(id),
    description TEXT,
    archived_at TIMESTAMPTZ,
    archived_by VARCHAR(100),
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(100) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by VARCHAR(100) NOT NULL,
    CONSTRAINT ck_ingestion_batch_source_type CHECK (
        source_type IS NULL OR source_type IN ('ZIP', 'FOLDER', 'MULTI_FILE', 'SINGLE_FILE')
    )
);

CREATE TABLE app_meta.batch_document (
    id UUID PRIMARY KEY,
    batch_id UUID NOT NULL REFERENCES app_meta.ingestion_batch(id),
    document_file_id UUID REFERENCES app_meta.document_file(id),
    sequence_no INTEGER NOT NULL,
    relative_path VARCHAR(1000),
    display_name VARCHAR(500) NOT NULL,
    workflow_status VARCHAR(30) NOT NULL DEFAULT 'UNPROCESSED',
    assigned_user_id UUID REFERENCES app_meta.sec_user(id),
    dynamic_record_id UUID,
    started_at TIMESTAMPTZ,
    submitted_at TIMESTAMPTZ,
    submitted_by VARCHAR(100),
    approved_at TIMESTAMPTZ,
    approved_by VARCHAR(100),
    rejected_at TIMESTAMPTZ,
    rejected_by VARCHAR(100),
    rejection_reason VARCHAR(2000),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by VARCHAR(100),
    previous_status VARCHAR(30),
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_batch_document_sequence UNIQUE (batch_id, sequence_no),
    CONSTRAINT ck_batch_document_status CHECK (
        workflow_status IN ('UNPROCESSED', 'DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED')
    ),
    CONSTRAINT ck_batch_document_previous_status CHECK (
        previous_status IS NULL OR previous_status IN ('UNPROCESSED', 'DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED')
    )
);

CREATE TABLE app_audit.record_workflow_history (
    id UUID PRIMARY KEY,
    batch_document_id UUID NOT NULL REFERENCES app_meta.batch_document(id),
    from_status VARCHAR(30),
    to_status VARCHAR(30) NOT NULL,
    action VARCHAR(40) NOT NULL,
    comment VARCHAR(2000),
    performed_by VARCHAR(100) NOT NULL,
    performed_at TIMESTAMPTZ NOT NULL,
    snapshot_json JSONB
);

CREATE INDEX idx_ingestion_batch_template ON app_meta.ingestion_batch(template_version_id);
CREATE INDEX idx_ingestion_batch_assignee ON app_meta.ingestion_batch(assigned_user_id, created_at DESC);
CREATE INDEX idx_batch_document_queue ON app_meta.batch_document(batch_id, workflow_status, sequence_no)
    WHERE deleted = FALSE;
CREATE INDEX idx_batch_document_assignee ON app_meta.batch_document(assigned_user_id, workflow_status, updated_at DESC)
    WHERE deleted = FALSE;
CREATE INDEX idx_batch_document_record ON app_meta.batch_document(dynamic_record_id)
    WHERE dynamic_record_id IS NOT NULL;
CREATE INDEX idx_batch_document_checksum ON app_meta.batch_document(batch_id, document_file_id);
CREATE INDEX idx_workflow_history_document ON app_audit.record_workflow_history(batch_document_id, performed_at DESC);

INSERT INTO app_meta.sec_function (id, code, name, function_group, description) VALUES
    ('10000000-0000-0000-0000-000000000026', 'BATCH_VIEW', 'Xem lô hồ sơ', 'Nhập liệu theo lô', 'Xem lô và danh sách công việc'),
    ('10000000-0000-0000-0000-000000000027', 'BATCH_CREATE', 'Tạo lô hồ sơ', 'Nhập liệu theo lô', 'Tạo lô và tải tài liệu'),
    ('10000000-0000-0000-0000-000000000028', 'BATCH_ASSIGN', 'Giao lô hồ sơ', 'Nhập liệu theo lô', 'Giao hoặc chuyển người nhập liệu'),
    ('10000000-0000-0000-0000-000000000029', 'RECORD_SUBMIT', 'Gửi duyệt hồ sơ', 'Phê duyệt hồ sơ', 'Gửi hồ sơ đã nhập để kiểm tra'),
    ('10000000-0000-0000-0000-000000000030', 'RECORD_APPROVE', 'Phê duyệt hồ sơ', 'Phê duyệt hồ sơ', 'Phê duyệt hồ sơ chờ duyệt'),
    ('10000000-0000-0000-0000-000000000031', 'RECORD_REJECT', 'Trả lại hồ sơ', 'Phê duyệt hồ sơ', 'Trả lại hồ sơ kèm lý do'),
    ('10000000-0000-0000-0000-000000000032', 'RECORD_REOPEN', 'Thu hồi phê duyệt', 'Phê duyệt hồ sơ', 'Mở lại hồ sơ đã duyệt'),
    ('10000000-0000-0000-0000-000000000033', 'RECORD_RESTORE', 'Khôi phục hồ sơ', 'Hồ sơ', 'Khôi phục hồ sơ từ thùng rác'),
    ('10000000-0000-0000-0000-000000000034', 'BATCH_UPLOAD', 'Tải tài liệu vào lô', 'Nhập liệu theo lô', 'Tải PDF, thư mục hoặc ZIP vào lô'),
    ('10000000-0000-0000-0000-000000000035', 'BATCH_ARCHIVE', 'Lưu trữ lô hồ sơ', 'Nhập liệu theo lô', 'Đóng lô đã hoàn tất')
ON CONFLICT (code) DO NOTHING;

INSERT INTO app_meta.sec_role_function (role_id, function_id, assigned_by)
SELECT r.id, f.id, 'flyway'
FROM app_meta.sec_role r
JOIN app_meta.sec_function f ON f.code IN (
    'BATCH_VIEW','BATCH_CREATE','BATCH_ASSIGN','RECORD_SUBMIT','RECORD_APPROVE',
    'RECORD_REJECT','RECORD_REOPEN','RECORD_RESTORE','BATCH_UPLOAD','BATCH_ARCHIVE'
)
WHERE r.code = 'SYSTEM_ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO app_meta.sec_role_function (role_id, function_id, assigned_by)
SELECT r.id, f.id, 'flyway'
FROM app_meta.sec_role r
JOIN app_meta.sec_function f ON f.code IN ('BATCH_VIEW','RECORD_SUBMIT')
WHERE r.code = 'DATA_ENTRY'
ON CONFLICT DO NOTHING;

DELETE FROM app_meta.sec_role_function rf
USING app_meta.sec_role r, app_meta.sec_function f
WHERE rf.role_id = r.id AND rf.function_id = f.id
  AND r.code = 'DATA_ENTRY' AND f.code = 'RECORD_COMPLETE';

INSERT INTO app_meta.sec_menu (id, code, label, path, icon, display_order) VALUES
    ('30000000-0000-0000-0000-000000000008', 'BATCHES', 'Quản lý lô', '/batches', 'InboxOutlined', 8),
    ('30000000-0000-0000-0000-000000000009', 'MY_BATCHES', 'Lô của tôi', '/my-batches', 'FolderOpenOutlined', 5),
    ('30000000-0000-0000-0000-000000000010', 'MY_WORK', 'Nhập liệu', '/my-work', 'FormOutlined', 6),
    ('30000000-0000-0000-0000-000000000011', 'APPROVALS', 'Chờ duyệt', '/approvals', 'AuditOutlined', 7)
ON CONFLICT (code) DO UPDATE SET label = EXCLUDED.label, path = EXCLUDED.path, active = TRUE;

INSERT INTO app_meta.sec_menu_function (menu_id, function_id)
SELECT mapping.menu_id, f.id
FROM (VALUES
    ('30000000-0000-0000-0000-000000000008'::uuid, 'BATCH_CREATE'),
    ('30000000-0000-0000-0000-000000000009'::uuid, 'BATCH_VIEW'),
    ('30000000-0000-0000-0000-000000000010'::uuid, 'BATCH_VIEW'),
    ('30000000-0000-0000-0000-000000000011'::uuid, 'RECORD_APPROVE')
) AS mapping(menu_id, function_code)
JOIN app_meta.sec_function f ON f.code = mapping.function_code
ON CONFLICT DO NOTHING;

INSERT INTO app_meta.sec_api_resource (id, http_method, path_pattern, function_code, priority) VALUES
    ('40000000-0000-0000-0000-000000000061', 'GET', '/api/v1/batches/**', 'BATCH_VIEW', 5),
    ('40000000-0000-0000-0000-000000000062', 'POST', '/api/v1/batches', 'BATCH_CREATE', 5),
    ('40000000-0000-0000-0000-000000000063', 'POST', '/api/v1/batches/*/upload/**', 'BATCH_UPLOAD', 3),
    ('40000000-0000-0000-0000-000000000064', 'POST', '/api/v1/batches/*/assign', 'BATCH_ASSIGN', 3),
    ('40000000-0000-0000-0000-000000000065', 'GET', '/api/v1/work-items/**', 'BATCH_VIEW', 5),
    ('40000000-0000-0000-0000-000000000066', 'PUT', '/api/v1/work-items/*/draft', 'RECORD_UPDATE', 3),
    ('40000000-0000-0000-0000-000000000067', 'POST', '/api/v1/work-items/*/submit', 'RECORD_SUBMIT', 3),
    ('40000000-0000-0000-0000-000000000068', 'POST', '/api/v1/work-items/*/approve', 'RECORD_APPROVE', 3),
    ('40000000-0000-0000-0000-000000000069', 'POST', '/api/v1/work-items/*/reject', 'RECORD_REJECT', 3),
    ('40000000-0000-0000-0000-000000000070', 'POST', '/api/v1/work-items/*/reopen', 'RECORD_REOPEN', 3),
    ('40000000-0000-0000-0000-000000000071', 'DELETE', '/api/v1/work-items/*', 'RECORD_DELETE', 5),
    ('40000000-0000-0000-0000-000000000072', 'POST', '/api/v1/work-items/*/restore', 'RECORD_RESTORE', 3),
    ('40000000-0000-0000-0000-000000000073', 'POST', '/api/v1/batches/*/documents', 'BATCH_UPLOAD', 3),
    ('40000000-0000-0000-0000-000000000074', 'POST', '/api/v1/batches/*/archive', 'BATCH_ARCHIVE', 3)
ON CONFLICT (http_method, path_pattern) DO UPDATE SET
    function_code = EXCLUDED.function_code,
    priority = EXCLUDED.priority,
    active = TRUE;
