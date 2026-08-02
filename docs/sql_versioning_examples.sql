-- Example SQL demonstrating version history for notification templates

-- 1) Create a template (notification_templates)
INSERT INTO notification_templates (id, template_key, name, channel, subject, body, active_version, created_at, updated_at)
VALUES ('11111111-1111-1111-1111-111111111111', 'welcome_email', 'Welcome Email', 'EMAIL', NULL, NULL, NULL, now(), now());

-- 2) Create Version 1
INSERT INTO notification_template_versions (id, template_id, version_number, subject, body, active, created_at)
VALUES ('22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111', 1, 'Welcome, {{firstName}}', 'Hello {{firstName}}, welcome', true, now());

-- Update active_version on template
UPDATE notification_templates SET active_version = 1, subject = 'Welcome, {{firstName}}', body = 'Hello {{firstName}}, welcome' WHERE id = '11111111-1111-1111-1111-111111111111';

-- 3) Update to Version 2 (do NOT overwrite v1)
-- Deactivate previous active versions
UPDATE notification_template_versions SET active = false WHERE template_id = '11111111-1111-1111-1111-111111111111' AND active = true;

-- Insert v2
INSERT INTO notification_template_versions (id, template_id, version_number, subject, body, active, created_at)
VALUES ('33333333-3333-3333-3333-333333333333', '11111111-1111-1111-1111-111111111111', 2, 'Welcome (v2) {{firstName}}', 'Hello {{firstName}}, welcome v2', true, now());

-- Update active_version on template
UPDATE notification_templates SET active_version = 2, subject = 'Welcome (v2) {{firstName}}', body = 'Hello {{firstName}}, welcome v2' WHERE id = '11111111-1111-1111-1111-111111111111';

-- 4) Insert Version 3 (repeat deactivation + insert)
UPDATE notification_template_versions SET active = false WHERE template_id = '11111111-1111-1111-1111-111111111111' AND active = true;
INSERT INTO notification_template_versions (id, template_id, version_number, subject, body, active, created_at)
VALUES ('44444444-4444-4444-4444-444444444444', '11111111-1111-1111-1111-111111111111', 3, 'Welcome (v3) {{firstName}}', 'Hello {{firstName}}, welcome v3', true, now());
UPDATE notification_templates SET active_version = 3, subject = 'Welcome (v3) {{firstName}}', body = 'Hello {{firstName}}, welcome v3' WHERE id = '11111111-1111-1111-1111-111111111111';

-- Verify history: all versions remain
SELECT id, template_id, version_number, active, created_at FROM notification_template_versions WHERE template_id = '11111111-1111-1111-1111-111111111111' ORDER BY version_number;
