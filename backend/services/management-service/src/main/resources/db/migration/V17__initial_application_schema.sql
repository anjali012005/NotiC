CREATE TABLE IF NOT EXISTS notification_templates (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants (id),
    template_key VARCHAR(100) NOT NULL,
    name VARCHAR(100) NOT NULL,
    channel VARCHAR(255) NOT NULL,
    subject VARCHAR(255),
    body TEXT NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE,
    active_version INTEGER,
    CONSTRAINT uk_template_tenant_key UNIQUE (tenant_id, template_key)
);

CREATE TABLE IF NOT EXISTS notification_template_versions (
    id UUID PRIMARY KEY,
    template_id UUID NOT NULL REFERENCES notification_templates (id),
    version INTEGER NOT NULL,
    version_number INTEGER NOT NULL,
    channel VARCHAR(255) NOT NULL,
    name VARCHAR(100) NOT NULL,
    subject TEXT,
    body TEXT NOT NULL,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT uk_template_version UNIQUE (template_id, version)
);

CREATE TABLE IF NOT EXISTS notification_template_tags (
    template_id UUID NOT NULL REFERENCES notification_templates (id),
    tag VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS notification_template_version_tags (
    version_id UUID NOT NULL REFERENCES notification_template_versions (id),
    tags VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS template_variables (
    id UUID PRIMARY KEY,
    version_id UUID NOT NULL REFERENCES notification_template_versions (id),
    variable_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS notification_providers (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants (id),
    name VARCHAR(100) NOT NULL,
    channel VARCHAR(255) NOT NULL,
    provider_type VARCHAR(255) NOT NULL,
    config TEXT NOT NULL,
    enabled BOOLEAN NOT NULL,
    is_default BOOLEAN NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT uk_provider_tenant_channel_name UNIQUE (tenant_id, channel, name)
);

CREATE TABLE IF NOT EXISTS notifications (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants (id),
    template_id UUID NOT NULL REFERENCES notification_templates (id),
    template_version_id UUID REFERENCES notification_template_versions (id),
    template_key VARCHAR(100) NOT NULL,
    recipient VARCHAR(255) NOT NULL,
    channel VARCHAR(255) NOT NULL,
    provider_id UUID,
    provider_name VARCHAR(100),
    subject VARCHAR(255),
    body TEXT,
    status VARCHAR(255) NOT NULL,
    retry_count INTEGER NOT NULL,
    failure_reason TEXT,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE,
    sent_at TIMESTAMP WITHOUT TIME ZONE
);

CREATE TABLE IF NOT EXISTS outbox_events (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants (id),
    event_type VARCHAR(100) NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempt_count INTEGER NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    published_at TIMESTAMP WITHOUT TIME ZONE,
    last_error TEXT
);

CREATE INDEX IF NOT EXISTS idx_notification_status ON notifications (status);
CREATE INDEX IF NOT EXISTS idx_notification_recipient ON notifications (recipient);
CREATE INDEX IF NOT EXISTS idx_notification_channel ON notifications (channel);
CREATE INDEX IF NOT EXISTS idx_notification_created_at ON notifications (created_at);
CREATE INDEX IF NOT EXISTS idx_notification_template_id ON notifications (template_id);
CREATE INDEX IF NOT EXISTS idx_notification_tenant_created_at ON notifications (tenant_id, created_at);
CREATE INDEX IF NOT EXISTS idx_outbox_status_created_at ON outbox_events (status, created_at);