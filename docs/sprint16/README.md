# Sprint 16: API Security and Tenant Isolation

## Scope

The management service now supports stateless API-key authentication and binds each authenticated request to one tenant. Notification, template, provider, and outbox records carry a tenant ID, and repository/service paths scope reads and writes to the current tenant. Health endpoints remain public.

## Authentication

API-key security is enabled by default. Configure credentials outside the repository using deployment secrets. The API-key map key is the tenant identifier; use a canonical UUID so it maps directly to the tenant UUID. Non-UUID identifiers are mapped deterministically to UUIDs, but are not provisioned as `tenants` rows automatically.

For PowerShell, supply the map through Spring's JSON environment variable:

```powershell
$env:SPRING_APPLICATION_JSON = '{"notifyflow":{"security":{"api-keys":{"11111111-1111-1111-1111-111111111111":"replace-with-a-secret"}}}}'
```

Send the configured credential on protected requests:

```http
X-API-Key: replace-with-a-secret
```

`X-Tenant-Id` is optional. If supplied, it must match the tenant bound to the API key. Health endpoints under `/actuator/health` do not require an API key. An empty key map rejects all protected requests. To intentionally disable API-key enforcement for local development only, set `NOTIFYFLOW_SECURITY_ENABLED=false`.

## Database Migration

Flyway migration `V16__tenant_isolation.sql` creates the tenant table when needed, seeds the legacy tenant (`00000000-0000-0000-0000-000000000000`), and assigns existing notifications, templates, providers, and outbox events to that tenant before making ownership non-null. Existing non-empty schemas are baselined at version 1 so the migration can run over the JPA-created schema. Hibernate remains in `ddl-auto: update` mode for non-migration schema changes.

Existing data therefore remains together under the legacy tenant. Access to it requires an API key configured with the all-zero UUID as its tenant key; no data is automatically copied into newly configured tenants.

## Verification

From `backend/services/management-service`:

```powershell
./mvnw test
```

The security filter tests cover public health bypass, invalid and valid keys, tenant mismatch, request-context cleanup, and tenantless resource denial. The service tests cover tenant-scoped provider and notification paths.

## Operational Notes

- Keep API-key values in a secret manager or protected deployment environment; do not commit them.
- Rotate credentials by replacing the configured key and restarting or reloading the service.
- API keys are configured as plaintext values and are compared without content-dependent string equality; durable key hashing, per-key expiry/revocation, and tenant administration are not implemented in this sprint.
- Keep `NOTIFYFLOW_SECURITY_ENABLED` enabled outside local development.