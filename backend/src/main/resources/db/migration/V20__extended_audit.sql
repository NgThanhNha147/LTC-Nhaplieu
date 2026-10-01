CREATE TABLE app_audit.audit_event (
    id UUID PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    action VARCHAR(120) NOT NULL,
    actor_username VARCHAR(100) NOT NULL,
    object_type VARCHAR(100),
    object_id VARCHAR(250),
    template_code VARCHAR(100),
    record_id UUID,
    old_data JSONB,
    new_data JSONB,
    metadata JSONB,
    request_id VARCHAR(100),
    ip_address VARCHAR(64),
    user_agent VARCHAR(1000),
    http_method VARCHAR(10),
    request_path VARCHAR(1000),
    result VARCHAR(20) NOT NULL,
    error_code VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_audit_event_created ON app_audit.audit_event(created_at DESC);
CREATE INDEX idx_audit_event_actor ON app_audit.audit_event(actor_username, created_at DESC);
CREATE INDEX idx_audit_event_action ON app_audit.audit_event(action, created_at DESC);
CREATE INDEX idx_audit_event_object ON app_audit.audit_event(object_type, object_id);
CREATE INDEX idx_audit_event_request ON app_audit.audit_event(request_id);

