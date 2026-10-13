CREATE TABLE IF NOT EXISTS tenants (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    slug VARCHAR(64) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT LOCALTIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_tenant_name ON tenants (name);
CREATE UNIQUE INDEX IF NOT EXISTS uk_tenant_slug ON tenants (slug);

INSERT INTO tenants (id, name, slug, active, created_at, updated_at)
VALUES (
    '00000000-0000-0000-0000-000000000000',
    'Legacy Default Tenant',
    'legacy-default',
    TRUE,
    LOCALTIMESTAMP,
    LOCALTIMESTAMP
)
ON CONFLICT (id) DO NOTHING;

DO $$
BEGIN
    IF to_regclass('notifications') IS NOT NULL THEN
        ALTER TABLE notifications ADD COLUMN IF NOT EXISTS tenant_id UUID;
        UPDATE notifications SET tenant_id = '00000000-0000-0000-0000-000000000000' WHERE tenant_id IS NULL;
        ALTER TABLE notifications ALTER COLUMN tenant_id SET NOT NULL;
        CREATE INDEX IF NOT EXISTS idx_notification_tenant_created_at ON notifications (tenant_id, created_at);
    END IF;

    IF to_regclass('notification_templates') IS NOT NULL THEN
        ALTER TABLE notification_templates ADD COLUMN IF NOT EXISTS tenant_id UUID;
        UPDATE notification_templates SET tenant_id = '00000000-0000-0000-0000-000000000000' WHERE tenant_id IS NULL;
        ALTER TABLE notification_templates ALTER COLUMN tenant_id SET NOT NULL;
    END IF;

    IF to_regclass('notification_providers') IS NOT NULL THEN
        ALTER TABLE notification_providers ADD COLUMN IF NOT EXISTS tenant_id UUID;
        UPDATE notification_providers SET tenant_id = '00000000-0000-0000-0000-000000000000' WHERE tenant_id IS NULL;
        ALTER TABLE notification_providers ALTER COLUMN tenant_id SET NOT NULL;
    END IF;

    IF to_regclass('outbox_events') IS NOT NULL THEN
        ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS tenant_id UUID;
        UPDATE outbox_events SET tenant_id = '00000000-0000-0000-0000-000000000000' WHERE tenant_id IS NULL;
        ALTER TABLE outbox_events ALTER COLUMN tenant_id SET NOT NULL;
    END IF;
END $$;

DO $$
DECLARE
    unique_constraint RECORD;
BEGIN
    IF to_regclass('notification_templates') IS NOT NULL THEN
        FOR unique_constraint IN
            SELECT constraint_row.conname
            FROM pg_constraint constraint_row
            WHERE constraint_row.conrelid = 'notification_templates'::regclass
              AND constraint_row.contype = 'u'
              AND constraint_row.conkey = ARRAY(
                    SELECT attribute_row.attnum
                    FROM pg_attribute attribute_row
                    WHERE attribute_row.attrelid = 'notification_templates'::regclass
                      AND attribute_row.attname = 'template_key'
                      AND NOT attribute_row.attisdropped
              )
        LOOP
            EXECUTE format('ALTER TABLE notification_templates DROP CONSTRAINT %I', unique_constraint.conname);
        END LOOP;
        ALTER TABLE notification_templates
            ADD CONSTRAINT uk_template_tenant_key UNIQUE (tenant_id, template_key);
    END IF;

    IF to_regclass('notification_providers') IS NOT NULL THEN
        ALTER TABLE notification_providers DROP CONSTRAINT IF EXISTS uk_provider_channel_name;
        ALTER TABLE notification_providers
            ADD CONSTRAINT uk_provider_tenant_channel_name UNIQUE (tenant_id, channel, name);
    END IF;
END $$;