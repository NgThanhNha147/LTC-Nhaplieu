CREATE TABLE app_meta.sec_user (
    id UUID PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ,
    password_changed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    must_change_password BOOLEAN NOT NULL DEFAULT TRUE,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL,
    CONSTRAINT ck_sec_user_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'LOCKED'))
);

CREATE TABLE app_meta.sec_role (
    id UUID PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    system_role BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL
);

CREATE TABLE app_meta.sec_function (
    id UUID PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    function_group VARCHAR(100) NOT NULL,
    description TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE app_meta.sec_user_role (
    user_id UUID NOT NULL REFERENCES app_meta.sec_user(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES app_meta.sec_role(id) ON DELETE CASCADE,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assigned_by VARCHAR(100) NOT NULL,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE app_meta.sec_role_function (
    role_id UUID NOT NULL REFERENCES app_meta.sec_role(id) ON DELETE CASCADE,
    function_id UUID NOT NULL REFERENCES app_meta.sec_function(id) ON DELETE CASCADE,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assigned_by VARCHAR(100) NOT NULL,
    PRIMARY KEY (role_id, function_id)
);

CREATE TABLE app_meta.sec_menu (
    id UUID PRIMARY KEY,
    parent_id UUID REFERENCES app_meta.sec_menu(id) ON DELETE CASCADE,
    code VARCHAR(100) NOT NULL UNIQUE,
    label VARCHAR(255) NOT NULL,
    path VARCHAR(500),
    icon VARCHAR(100),
    display_order INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE app_meta.sec_menu_function (
    menu_id UUID NOT NULL REFERENCES app_meta.sec_menu(id) ON DELETE CASCADE,
    function_id UUID NOT NULL REFERENCES app_meta.sec_function(id) ON DELETE CASCADE,
    PRIMARY KEY (menu_id, function_id)
);

CREATE TABLE app_meta.sec_api_resource (
    id UUID PRIMARY KEY,
    http_method VARCHAR(10) NOT NULL,
    path_pattern VARCHAR(500) NOT NULL,
    function_code VARCHAR(100) NOT NULL REFERENCES app_meta.sec_function(code),
    priority INTEGER NOT NULL DEFAULT 100,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_sec_api_resource UNIQUE (http_method, path_pattern)
);

CREATE TABLE app_meta.sec_refresh_token (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_meta.sec_user(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    replaced_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_ip VARCHAR(100),
    user_agent VARCHAR(500)
);

CREATE TABLE app_meta.sec_login_history (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES app_meta.sec_user(id) ON DELETE SET NULL,
    username VARCHAR(100) NOT NULL,
    successful BOOLEAN NOT NULL,
    failure_reason VARCHAR(100),
    ip_address VARCHAR(100),
    user_agent VARCHAR(500),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_sec_user_role_role ON app_meta.sec_user_role(role_id);
CREATE INDEX idx_sec_role_function_function ON app_meta.sec_role_function(function_id);
CREATE INDEX idx_sec_refresh_user ON app_meta.sec_refresh_token(user_id);
CREATE INDEX idx_sec_refresh_expiry ON app_meta.sec_refresh_token(expires_at);
CREATE INDEX idx_sec_login_username_time ON app_meta.sec_login_history(username, occurred_at DESC);
CREATE INDEX idx_sec_api_active_priority ON app_meta.sec_api_resource(active, priority);

INSERT INTO app_meta.sec_function (id, code, name, function_group) VALUES
    ('10000000-0000-0000-0000-000000000001', 'RECORD_VIEW', 'Xem hồ sơ', 'Hồ sơ'),
    ('10000000-0000-0000-0000-000000000002', 'RECORD_CREATE', 'Tạo hồ sơ', 'Hồ sơ'),
    ('10000000-0000-0000-0000-000000000003', 'RECORD_UPDATE', 'Cập nhật hồ sơ', 'Hồ sơ'),
    ('10000000-0000-0000-0000-000000000004', 'RECORD_DELETE', 'Xóa hồ sơ', 'Hồ sơ'),
    ('10000000-0000-0000-0000-000000000005', 'RECORD_COMPLETE', 'Hoàn tất hồ sơ', 'Hồ sơ'),
    ('10000000-0000-0000-0000-000000000006', 'RECORD_EXPORT', 'Xuất dữ liệu', 'Hồ sơ'),
    ('10000000-0000-0000-0000-000000000007', 'DOCUMENT_VIEW', 'Xem tài liệu', 'Tài liệu'),
    ('10000000-0000-0000-0000-000000000008', 'DOCUMENT_UPLOAD', 'Tải tài liệu', 'Tài liệu'),
    ('10000000-0000-0000-0000-000000000009', 'TEMPLATE_VIEW', 'Xem biểu mẫu', 'Biểu mẫu'),
    ('10000000-0000-0000-0000-000000000010', 'TEMPLATE_CREATE', 'Tạo biểu mẫu', 'Biểu mẫu'),
    ('10000000-0000-0000-0000-000000000011', 'TEMPLATE_CONFIGURE', 'Cấu hình biểu mẫu', 'Biểu mẫu'),
    ('10000000-0000-0000-0000-000000000012', 'SCHEMA_VALIDATE', 'Kiểm tra cấu trúc bảng', 'Cấu trúc bảng'),
    ('10000000-0000-0000-0000-000000000013', 'SCHEMA_GENERATE', 'Sinh cấu trúc bảng', 'Cấu trúc bảng'),
    ('10000000-0000-0000-0000-000000000014', 'SCHEMA_PURGE', 'Xóa lưu trữ phiên bản', 'Cấu trúc bảng'),
    ('10000000-0000-0000-0000-000000000015', 'TEMPLATE_PUBLISH', 'Đưa biểu mẫu vào sử dụng', 'Biểu mẫu'),
    ('10000000-0000-0000-0000-000000000016', 'LOOKUP_VIEW', 'Xem danh mục', 'Danh mục'),
    ('10000000-0000-0000-0000-000000000017', 'LOOKUP_MANAGE', 'Quản lý danh mục', 'Danh mục'),
    ('10000000-0000-0000-0000-000000000018', 'USER_VIEW', 'Xem người dùng', 'Bảo mật'),
    ('10000000-0000-0000-0000-000000000019', 'USER_MANAGE', 'Quản lý người dùng', 'Bảo mật'),
    ('10000000-0000-0000-0000-000000000020', 'ROLE_MANAGE', 'Quản lý vai trò và quyền', 'Bảo mật'),
    ('10000000-0000-0000-0000-000000000021', 'AUDIT_VIEW', 'Xem nhật ký', 'Kiểm toán'),
    ('10000000-0000-0000-0000-000000000022', 'AUDIT_EXPORT', 'Xuất nhật ký', 'Kiểm toán'),
    ('10000000-0000-0000-0000-000000000023', 'BACKUP_VIEW', 'Xem bản sao lưu', 'Sao lưu'),
    ('10000000-0000-0000-0000-000000000024', 'BACKUP_RUN', 'Thực hiện sao lưu', 'Sao lưu'),
    ('10000000-0000-0000-0000-000000000025', 'RESTORE_RUN', 'Phục hồi dữ liệu', 'Sao lưu');

INSERT INTO app_meta.sec_role (id, code, name, description, system_role, created_by, updated_by) VALUES
    ('20000000-0000-0000-0000-000000000001', 'SYSTEM_ADMIN', 'Quản trị hệ thống', 'Toàn quyền cấu hình hệ thống', TRUE, 'flyway', 'flyway'),
    ('20000000-0000-0000-0000-000000000002', 'FORM_ADMIN', 'Quản trị biểu mẫu', 'Cấu hình và phát hành biểu mẫu', TRUE, 'flyway', 'flyway'),
    ('20000000-0000-0000-0000-000000000003', 'DATA_ENTRY', 'Nhân viên nhập liệu', 'Nhập và cập nhật hồ sơ', TRUE, 'flyway', 'flyway'),
    ('20000000-0000-0000-0000-000000000004', 'DATA_MANAGER', 'Quản lý dữ liệu', 'Quản lý và xuất dữ liệu hồ sơ', TRUE, 'flyway', 'flyway'),
    ('20000000-0000-0000-0000-000000000005', 'REVIEWER', 'Người kiểm tra', 'Tra cứu và đối chiếu hồ sơ', TRUE, 'flyway', 'flyway'),
    ('20000000-0000-0000-0000-000000000006', 'AUDITOR', 'Kiểm toán viên', 'Tra cứu nhật ký kiểm toán', TRUE, 'flyway', 'flyway'),
    ('20000000-0000-0000-0000-000000000007', 'BACKUP_OPERATOR', 'Nhân viên sao lưu', 'Theo dõi và thực hiện sao lưu', TRUE, 'flyway', 'flyway');

INSERT INTO app_meta.sec_role_function (role_id, function_id, assigned_by)
SELECT '20000000-0000-0000-0000-000000000001', id, 'flyway' FROM app_meta.sec_function;

INSERT INTO app_meta.sec_role_function (role_id, function_id, assigned_by)
SELECT '20000000-0000-0000-0000-000000000002', id, 'flyway' FROM app_meta.sec_function
WHERE code IN ('TEMPLATE_VIEW','TEMPLATE_CREATE','TEMPLATE_CONFIGURE','SCHEMA_VALIDATE','SCHEMA_GENERATE','SCHEMA_PURGE','TEMPLATE_PUBLISH','LOOKUP_VIEW','LOOKUP_MANAGE');

INSERT INTO app_meta.sec_role_function (role_id, function_id, assigned_by)
SELECT '20000000-0000-0000-0000-000000000003', id, 'flyway' FROM app_meta.sec_function
WHERE code IN ('RECORD_VIEW','RECORD_CREATE','RECORD_UPDATE','RECORD_COMPLETE','DOCUMENT_VIEW','DOCUMENT_UPLOAD','TEMPLATE_VIEW','LOOKUP_VIEW');

INSERT INTO app_meta.sec_role_function (role_id, function_id, assigned_by)
SELECT '20000000-0000-0000-0000-000000000004', id, 'flyway' FROM app_meta.sec_function
WHERE code IN ('RECORD_VIEW','RECORD_CREATE','RECORD_UPDATE','RECORD_DELETE','RECORD_COMPLETE','RECORD_EXPORT','DOCUMENT_VIEW','DOCUMENT_UPLOAD','TEMPLATE_VIEW','LOOKUP_VIEW');

INSERT INTO app_meta.sec_role_function (role_id, function_id, assigned_by)
SELECT '20000000-0000-0000-0000-000000000005', id, 'flyway' FROM app_meta.sec_function
WHERE code IN ('RECORD_VIEW','DOCUMENT_VIEW','TEMPLATE_VIEW','LOOKUP_VIEW');

INSERT INTO app_meta.sec_role_function (role_id, function_id, assigned_by)
SELECT '20000000-0000-0000-0000-000000000006', id, 'flyway' FROM app_meta.sec_function
WHERE code IN ('AUDIT_VIEW','AUDIT_EXPORT');

INSERT INTO app_meta.sec_role_function (role_id, function_id, assigned_by)
SELECT '20000000-0000-0000-0000-000000000007', id, 'flyway' FROM app_meta.sec_function
WHERE code IN ('BACKUP_VIEW','BACKUP_RUN');

INSERT INTO app_meta.sec_menu (id, code, label, path, icon, display_order) VALUES
    ('30000000-0000-0000-0000-000000000001', 'RECORDS', 'Quản lý hồ sơ', '/workspaces', 'DatabaseOutlined', 10),
    ('30000000-0000-0000-0000-000000000002', 'TEMPLATES', 'Quản trị biểu mẫu', '/templates', 'AppstoreOutlined', 20),
    ('30000000-0000-0000-0000-000000000003', 'LOOKUPS', 'Danh mục dùng chung', '/lookups', 'TagsOutlined', 30),
    ('30000000-0000-0000-0000-000000000004', 'USERS', 'Người dùng', '/admin/users', 'TeamOutlined', 40),
    ('30000000-0000-0000-0000-000000000005', 'ROLES', 'Vai trò và quyền', '/admin/roles', 'SafetyOutlined', 50),
    ('30000000-0000-0000-0000-000000000006', 'AUDIT', 'Nhật ký hệ thống', '/admin/audit', 'AuditOutlined', 60),
    ('30000000-0000-0000-0000-000000000007', 'BACKUP', 'Sao lưu dữ liệu', '/admin/backups', 'CloudServerOutlined', 70);

INSERT INTO app_meta.sec_menu_function (menu_id, function_id) VALUES
    ('30000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001'),
    ('30000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000009'),
    ('30000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000016'),
    ('30000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000018'),
    ('30000000-0000-0000-0000-000000000005', '10000000-0000-0000-0000-000000000020'),
    ('30000000-0000-0000-0000-000000000006', '10000000-0000-0000-0000-000000000021'),
    ('30000000-0000-0000-0000-000000000007', '10000000-0000-0000-0000-000000000023');

INSERT INTO app_meta.sec_api_resource (id, http_method, path_pattern, function_code, priority) VALUES
    ('40000000-0000-0000-0000-000000000001', 'GET', '/api/v1/forms/**', 'RECORD_VIEW', 10),
    ('40000000-0000-0000-0000-000000000002', 'GET', '/api/v1/templates/*/records/*', 'RECORD_VIEW', 10),
    ('40000000-0000-0000-0000-000000000003', 'POST', '/api/v1/templates/*/records/search', 'RECORD_VIEW', 5),
    ('40000000-0000-0000-0000-000000000004', 'POST', '/api/v1/templates/*/records/export', 'RECORD_EXPORT', 5),
    ('40000000-0000-0000-0000-000000000005', 'POST', '/api/v1/templates/*/records', 'RECORD_CREATE', 10),
    ('40000000-0000-0000-0000-000000000006', 'PUT', '/api/v1/templates/*/records/*', 'RECORD_UPDATE', 10),
    ('40000000-0000-0000-0000-000000000007', 'DELETE', '/api/v1/templates/*/records/*', 'RECORD_DELETE', 10),
    ('40000000-0000-0000-0000-000000000008', 'GET', '/api/v1/documents/**', 'DOCUMENT_VIEW', 10),
    ('40000000-0000-0000-0000-000000000009', 'POST', '/api/v1/documents', 'DOCUMENT_UPLOAD', 10),
    ('40000000-0000-0000-0000-000000000010', 'POST', '/api/v1/documents/*/ocr', 'DOCUMENT_UPLOAD', 5),
    ('40000000-0000-0000-0000-000000000011', 'GET', '/api/v1/templates', 'TEMPLATE_VIEW', 10),
    ('40000000-0000-0000-0000-000000000012', 'GET', '/api/v1/templates/**', 'TEMPLATE_VIEW', 20),
    ('40000000-0000-0000-0000-000000000013', 'POST', '/api/v1/templates', 'TEMPLATE_CREATE', 10),
    ('40000000-0000-0000-0000-000000000014', 'PUT', '/api/v1/templates/**', 'TEMPLATE_CONFIGURE', 10),
    ('40000000-0000-0000-0000-000000000015', 'DELETE', '/api/v1/templates/**', 'TEMPLATE_CONFIGURE', 10),
    ('40000000-0000-0000-0000-000000000016', 'POST', '/api/v1/templates/*/versions', 'TEMPLATE_CONFIGURE', 5),
    ('40000000-0000-0000-0000-000000000017', 'GET', '/api/v1/template-versions/**', 'TEMPLATE_VIEW', 20),
    ('40000000-0000-0000-0000-000000000018', 'POST', '/api/v1/template-versions/*/validate', 'SCHEMA_VALIDATE', 5),
    ('40000000-0000-0000-0000-000000000019', 'POST', '/api/v1/template-versions/*/generate', 'SCHEMA_GENERATE', 5),
    ('40000000-0000-0000-0000-000000000020', 'POST', '/api/v1/template-versions/*/publish', 'TEMPLATE_PUBLISH', 5),
    ('40000000-0000-0000-0000-000000000021', 'DELETE', '/api/v1/template-versions/*/storage', 'SCHEMA_PURGE', 5),
    ('40000000-0000-0000-0000-000000000022', 'POST', '/api/v1/template-versions/**', 'TEMPLATE_CONFIGURE', 30),
    ('40000000-0000-0000-0000-000000000023', 'POST', '/api/v1/groups/**', 'TEMPLATE_CONFIGURE', 10),
    ('40000000-0000-0000-0000-000000000024', 'PUT', '/api/v1/groups/**', 'TEMPLATE_CONFIGURE', 10),
    ('40000000-0000-0000-0000-000000000025', 'DELETE', '/api/v1/groups/**', 'TEMPLATE_CONFIGURE', 10),
    ('40000000-0000-0000-0000-000000000026', 'PUT', '/api/v1/fields/**', 'TEMPLATE_CONFIGURE', 10),
    ('40000000-0000-0000-0000-000000000027', 'DELETE', '/api/v1/fields/**', 'TEMPLATE_CONFIGURE', 10),
    ('40000000-0000-0000-0000-000000000028', 'PUT', '/api/v1/form-rules/**', 'TEMPLATE_CONFIGURE', 10),
    ('40000000-0000-0000-0000-000000000029', 'DELETE', '/api/v1/form-rules/**', 'TEMPLATE_CONFIGURE', 10),
    ('40000000-0000-0000-0000-000000000030', 'GET', '/api/v1/lookups/**', 'LOOKUP_VIEW', 10),
    ('40000000-0000-0000-0000-000000000031', 'GET', '/api/v1/lookup-sources/**', 'LOOKUP_VIEW', 10),
    ('40000000-0000-0000-0000-000000000032', 'POST', '/api/v1/lookups/**', 'LOOKUP_MANAGE', 10),
    ('40000000-0000-0000-0000-000000000033', 'PUT', '/api/v1/lookups/**', 'LOOKUP_MANAGE', 10),
    ('40000000-0000-0000-0000-000000000034', 'DELETE', '/api/v1/lookups/**', 'LOOKUP_MANAGE', 10),
    ('40000000-0000-0000-0000-000000000035', 'GET', '/api/v1/admin/users/**', 'USER_VIEW', 10),
    ('40000000-0000-0000-0000-000000000036', 'POST', '/api/v1/admin/users/**', 'USER_MANAGE', 10),
    ('40000000-0000-0000-0000-000000000037', 'PUT', '/api/v1/admin/users/**', 'USER_MANAGE', 10),
    ('40000000-0000-0000-0000-000000000038', 'GET', '/api/v1/admin/roles/**', 'ROLE_MANAGE', 10),
    ('40000000-0000-0000-0000-000000000039', 'POST', '/api/v1/admin/roles/**', 'ROLE_MANAGE', 10),
    ('40000000-0000-0000-0000-000000000040', 'PUT', '/api/v1/admin/roles/**', 'ROLE_MANAGE', 10),
    ('40000000-0000-0000-0000-000000000041', 'GET', '/api/v1/admin/functions', 'ROLE_MANAGE', 10),
    ('40000000-0000-0000-0000-000000000042', 'GET', '/api/v1/audit/**', 'AUDIT_VIEW', 10),
    ('40000000-0000-0000-0000-000000000043', 'POST', '/api/v1/audit/export', 'AUDIT_EXPORT', 5),
    ('40000000-0000-0000-0000-000000000044', 'GET', '/api/v1/admin/backups/**', 'BACKUP_VIEW', 10),
    ('40000000-0000-0000-0000-000000000045', 'POST', '/api/v1/admin/backups', 'BACKUP_RUN', 10),
    ('40000000-0000-0000-0000-000000000046', 'POST', '/api/v1/admin/backups/*/verify', 'BACKUP_RUN', 5),
    ('40000000-0000-0000-0000-000000000047', 'POST', '/api/v1/admin/backups/*/restore', 'RESTORE_RUN', 5);
