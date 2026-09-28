CREATE SCHEMA IF NOT EXISTS app_meta;
CREATE SCHEMA IF NOT EXISTS app_data;
CREATE SCHEMA IF NOT EXISTS ref_data;
CREATE SCHEMA IF NOT EXISTS app_audit;

CREATE TABLE app_meta.form_template (
    id UUID PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(30) NOT NULL,
    current_version INTEGER,
    created_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(100) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by VARCHAR(100) NOT NULL
);

CREATE TABLE app_meta.template_version (
    id UUID PRIMARY KEY,
    template_id UUID NOT NULL REFERENCES app_meta.form_template(id),
    version_no INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL,
    physical_schema VARCHAR(63),
    physical_table VARCHAR(63),
    config_hash VARCHAR(64),
    generated_at TIMESTAMPTZ,
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(100) NOT NULL,
    CONSTRAINT uq_template_version UNIQUE (template_id, version_no)
);

CREATE TABLE app_meta.field_group (
    id UUID PRIMARY KEY,
    template_version_id UUID NOT NULL REFERENCES app_meta.template_version(id) ON DELETE CASCADE,
    code VARCHAR(100) NOT NULL,
    label VARCHAR(255) NOT NULL,
    description TEXT,
    display_order INTEGER NOT NULL,
    column_count INTEGER NOT NULL DEFAULT 2,
    collapsible BOOLEAN NOT NULL DEFAULT FALSE,
    default_collapsed BOOLEAN NOT NULL DEFAULT FALSE,
    repeatable BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_group_code UNIQUE (template_version_id, code)
);

CREATE TABLE app_meta.lookup_source (
    id UUID PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    source_type VARCHAR(30) NOT NULL,
    source_schema VARCHAR(63) NOT NULL,
    source_table VARCHAR(63) NOT NULL,
    value_column VARCHAR(63) NOT NULL,
    label_column VARCHAR(63) NOT NULL,
    active_column VARCHAR(63),
    parent_column VARCHAR(63),
    sort_column VARCHAR(63),
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE app_meta.field_definition (
    id UUID PRIMARY KEY,
    template_version_id UUID NOT NULL REFERENCES app_meta.template_version(id) ON DELETE CASCADE,
    group_id UUID REFERENCES app_meta.field_group(id) ON DELETE SET NULL,
    field_code VARCHAR(100) NOT NULL,
    column_name VARCHAR(63) NOT NULL,
    label VARCHAR(255) NOT NULL,
    data_type VARCHAR(30) NOT NULL,
    component_type VARCHAR(30) NOT NULL,
    description TEXT,
    placeholder VARCHAR(255),
    required BOOLEAN NOT NULL DEFAULT FALSE,
    unique_value BOOLEAN NOT NULL DEFAULT FALSE,
    indexed BOOLEAN NOT NULL DEFAULT FALSE,
    searchable BOOLEAN NOT NULL DEFAULT FALSE,
    sortable BOOLEAN NOT NULL DEFAULT FALSE,
    exportable BOOLEAN NOT NULL DEFAULT TRUE,
    max_length INTEGER,
    precision_value INTEGER,
    scale_value INTEGER,
    default_value TEXT,
    display_order INTEGER NOT NULL,
    grid_span INTEGER NOT NULL DEFAULT 6,
    read_only BOOLEAN NOT NULL DEFAULT FALSE,
    hidden BOOLEAN NOT NULL DEFAULT FALSE,
    help_text TEXT,
    lookup_source_id UUID REFERENCES app_meta.lookup_source(id),
    CONSTRAINT uq_field_code UNIQUE (template_version_id, field_code),
    CONSTRAINT uq_column_name UNIQUE (template_version_id, column_name)
);

CREATE TABLE app_meta.validation_rule (
    id UUID PRIMARY KEY,
    field_id UUID NOT NULL REFERENCES app_meta.field_definition(id) ON DELETE CASCADE,
    rule_type VARCHAR(30) NOT NULL,
    rule_config JSONB NOT NULL DEFAULT '{}'::jsonb,
    error_message VARCHAR(500),
    display_order INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE app_meta.document_file (
    id UUID PRIMARY KEY,
    original_name VARCHAR(500) NOT NULL,
    stored_name VARCHAR(500) NOT NULL,
    content_type VARCHAR(150),
    size_bytes BIGINT NOT NULL,
    checksum_sha256 VARCHAR(64) NOT NULL,
    storage_path VARCHAR(1000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(100) NOT NULL
);

CREATE TABLE app_audit.data_change_log (
    id UUID PRIMARY KEY,
    template_version_id UUID NOT NULL,
    record_id UUID NOT NULL,
    action VARCHAR(20) NOT NULL,
    old_data JSONB,
    new_data JSONB,
    changed_by VARCHAR(100) NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL,
    request_id VARCHAR(100)
);

CREATE INDEX idx_template_version_template ON app_meta.template_version(template_id);
CREATE INDEX idx_group_version ON app_meta.field_group(template_version_id);
CREATE INDEX idx_field_version ON app_meta.field_definition(template_version_id);
CREATE INDEX idx_rule_field ON app_meta.validation_rule(field_id);
CREATE INDEX idx_audit_record ON app_audit.data_change_log(template_version_id, record_id);
