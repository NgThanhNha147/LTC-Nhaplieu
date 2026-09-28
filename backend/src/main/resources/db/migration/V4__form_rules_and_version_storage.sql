ALTER TABLE app_meta.template_version
    ALTER COLUMN config_hash TYPE VARCHAR(80),
    ADD COLUMN storage_status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN purged_at TIMESTAMPTZ,
    ADD COLUMN purged_by VARCHAR(100),
    ADD COLUMN purge_reason VARCHAR(500);

CREATE TABLE app_meta.template_rule (
    id UUID PRIMARY KEY,
    template_version_id UUID NOT NULL REFERENCES app_meta.template_version(id) ON DELETE CASCADE,
    rule_type VARCHAR(50) NOT NULL,
    rule_config JSONB NOT NULL DEFAULT '{}'::jsonb,
    error_message VARCHAR(500),
    display_order INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_template_rule_version ON app_meta.template_rule(template_version_id);

CREATE TABLE app_meta.version_migration_log (
    id UUID PRIMARY KEY,
    source_version_id UUID REFERENCES app_meta.template_version(id),
    target_version_id UUID NOT NULL REFERENCES app_meta.template_version(id),
    source_row_count BIGINT NOT NULL,
    copied_row_count BIGINT NOT NULL,
    missing_record_count BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL,
    verified_at TIMESTAMPTZ NOT NULL,
    verified_by VARCHAR(100) NOT NULL
);

CREATE INDEX idx_version_migration_target ON app_meta.version_migration_log(target_version_id, verified_at DESC);
