# NotifyFlow Dashboard

The dashboard is a Next.js application that calls the API Gateway through a same-origin server route. It does not connect directly to the management service, PostgreSQL, RabbitMQ, or a provider.

## Requirements

- Node.js 20.19 or newer (Node 22 is recommended).
- NotifyFlow API Gateway reachable from the Next.js server.
- A tenant API key configured in the management service.

## Local setup

From this directory:

```powershell
npm install
Copy-Item .env.example .env.local
npm run dev
```

Open `http://localhost:3000`. The example points to the Compose gateway at `http://localhost:8080`. Change `NOTIFYFLOW_API_BASE_URL` in `.env.local` if the gateway uses another host or port. This is a server-side variable; do not add API keys or provider credentials to it.

The dashboard asks the operator for a tenant API key and verifies it against the provider-list endpoint. The key is held in React memory for the current page session, sent only to the same-origin Next.js route, and forwarded as `X-API-Key`. It is not stored in browser storage, cookies, URLs, or frontend environment variables. The backend resolves tenant identity from the key; the dashboard does not accept a tenant ID.

## Container setup

From the repository root, configure `.env` as described in the root README and run:

```powershell
docker compose up -d --build
docker compose ps
```

Open `http://localhost:3000`. Compose gives the frontend container `NOTIFYFLOW_API_BASE_URL=http://gateway-service:8080`; this is an internal service URL and is not exposed in the browser bundle. The frontend uses standalone Next.js output and runs as a non-root user. Its Docker build context excludes local `.env` files, dependencies, and build artifacts.

## Available screens

- `/`: API-derived notification totals, recent records, and public gateway health.
- `/notifications`: management-API pagination, status/channel filters, and recipient substring filter.
- `/notifications/{id}`: current delivery fields and server-computed retry eligibility.
- `/send`: template-backed asynchronous notification submission.
- `/templates`: channel filtering, pagination, create/edit/delete, and immutable version history.
- `/providers`: create a SendGrid EMAIL sender configuration, view credential readiness, and manage enabled/default state with channel/enabled filters.
- `/health`: public API Gateway health summary only.

## API inventory

All management routes are called through `GET|POST|PUT|PATCH|DELETE /api/backend/...` and forwarded to the API Gateway. The proxy only allows notification, template, provider, and public gateway-health paths. Management API requests include the operator's `X-API-Key`; tenant scope is derived and enforced by the backend.

| Screen | Gateway operation | Request and response contract |
| --- | --- | --- |
| Overview | `GET /api/v1/notifications?page=0&size=5`; same list endpoint with `status=QUEUED\|PROCESSING\|SENT\|FAILED&size=1`; `GET /actuator/health` | Pageable `content` plus authoritative `totalElements`; public gateway health status. Totals are not calculated from a page. |
| Notifications | `GET /api/v1/notifications?page&size&status&channel&recipient` | Returns `PageResponse<NotificationDetailsResponse>`. Filters are backend-supported. Response content is redacted by the proxy to remove subject, body, and failure reason. |
| Details/retry | `GET /api/v1/notifications/{id}`; `POST /api/v1/notifications/{id}/retry` | Detail response contains status, template/provider references, dates, retry count and computed `retryEligible`. Retry returns `202` on acceptance; current status is then refreshed. |
| Send | `GET /api/v1/templates?page&size&sortBy&sortDir`; `GET /api/v1/templates/{id}/variables`; `POST /api/v1/notifications/send` | Send body: `{ "templateKey": "...", "recipient": "...", "variables": { "key": "value" } }`. Backend returns `202 Accepted`; that is not proof of delivery. |
| Templates | `GET /api/v1/templates?page&size&sortBy&sortDir&channel`; `POST /api/v1/templates`; `PUT /api/v1/templates/{id}`; `DELETE /api/v1/templates/{id}`; `GET /api/v1/templates/{id}/versions` | Uses the backend's `templateKey`, `name`, `channel`, `subject`, `body`, and `tags` fields. Update creates a new active immutable version. No rollback is offered. |
| Providers | `GET /api/v1/providers?channel&enabled`; `PATCH /api/v1/providers/{id}/enable`; `/disable`; `/default` | Lists safe `name`, `providerType`, `channel`, `configured`, `enabled`, and `isDefault` fields. Provider configuration create/update is intentionally omitted because its API accepts an opaque config string and does not expose a field-level safe schema. |
| Health | `GET /actuator/health` | Public gateway summary. Management Actuator endpoints are not routed by the gateway and are not called from the browser. |

## Verification

Use Node 20.9 or newer from this directory:

```powershell
npm run typecheck
npm test
npm run build
```

The UI tests mock API responses. For an end-to-end check, start the root Compose stack, configure a valid tenant key in the management service, start the dashboard, then use the screens above. Do not use the sample key outside an isolated local environment and do not configure a real provider credential for routine testing.

## Confirmed limitations

- The backend does not expose a tenant-safe aggregate metrics endpoint, so notification counts use the pageable list's `totalElements` filtered by status.
- The gateway exposes only its own public health summary. Management readiness, provider health, queue/outbox depth, latency, and Prometheus data are not routed through the gateway.
- The Providers screen creates SendGrid EMAIL sender details but never accepts or returns an API key. Set `SENDGRID_API_KEY` in the backend environment (or secret manager), restart the management service, then refresh Providers; credentials must be ready before enabling/defaulting the provider.
- The frontend does not implement authentication identity, API-key issuance/rotation, or tenant switching. The current backend uses static API keys mapped to tenants.
- Live browser workflows require the gateway, management service, database, and broker to be running. A `202` response indicates acceptance for asynchronous work, not successful delivery.
