# Sprint 18: NotifyFlow Dashboard

## Repository audit

The repository had no frontend application. It contains separate Maven projects for the API Gateway (`8080`) and management service (`8081`). The Gateway forwards `/api/v1/**` to the management service. Management routes require `X-API-Key` when security is enabled; the authenticated key resolves a tenant, and a mismatched `X-Tenant-Id` is rejected. The dashboard therefore does not send or select tenant IDs.

The management service implements pageable notification and template APIs, asynchronous notification submission (`202 Accepted`), notification retry, template version history, provider listing/state operations, and tenant-scoped API-key security. The Gateway exposes public health but does not route management Actuator endpoints. There was no aggregate notification metrics endpoint.

## Implementation

`frontend/` now contains a responsive Next.js App Router dashboard using React, TypeScript, Tailwind CSS, and Lucide icons. A multi-stage standalone Docker image and Compose service run it alongside the existing stack. The frontend container calls a same-origin Next.js proxy, which forwards only allowlisted API paths to the internal Gateway. The proxy enforces API-key presence for management paths, rejects traversal-like path segments, times out upstream calls, and removes message subject/body and raw failure reason from notification GET responses.

The operator enters a tenant API key in a password field. The key is verified against the real provider-list endpoint and retained only in React memory. It is not written to browser storage or frontend environment variables. Backend authorization and tenant isolation remain authoritative.

Screens include overview, notification list/details/retry, asynchronous send, template create/edit/delete/version history, provider safe-state management, and public Gateway health. Retry controls rely on the backend-computed eligibility field rather than a frontend retry limit.

## Backend addition

`NotificationDetailsResponse.retryEligible` is computed by the tracking service using the configured maximum retry count, failed status, and tenant-scoped provider enabled state. This small response addition was necessary to avoid hard-coding configuration or offering retries the backend will reject. The retry operation itself remains unchanged.

## API inventory

| Screen | Operations |
| --- | --- |
| Overview | `GET /api/v1/notifications` with status/page/size; `GET /actuator/health` |
| Notifications | `GET /api/v1/notifications` with backend status/channel/recipient filters |
| Detail/retry | `GET /api/v1/notifications/{id}`; `POST /api/v1/notifications/{id}/retry` |
| Send | `GET /api/v1/templates`; `GET /api/v1/templates/{id}/variables`; `POST /api/v1/notifications/send` |
| Templates | `GET|POST /api/v1/templates`; `PUT|DELETE /api/v1/templates/{id}`; `GET /api/v1/templates/{id}/versions` |
| Providers | `GET /api/v1/providers`; `PATCH /api/v1/providers/{id}/enable`, `/disable`, or `/default` |
| Health | `GET /actuator/health` on the Gateway only |

See [frontend/README.md](../../frontend/README.md) for request DTO fields, environment setup, route behavior, and limitations.

## Verification and remaining work

- Frontend runtime: Node.js 20.19+ (Node 22 recommended).
- Frontend typecheck: passed with the repository-local TypeScript compiler.
- Frontend tests: 12 passed, including API request contracts, proxy allowlist/authentication/redaction, and overview loading/success/empty/error states.
- Frontend production build: passed; all dashboard routes compiled.
- Management tracking tests: 13 passed, including eligibility with configured retry count and disabled providers.
- Live end-to-end workflows were not verified because the API Gateway was not available at `localhost:8080` during this sprint work.

Sprint 19 should add a gateway-backed integration environment and browser tests for valid/invalid tenant keys, notification send-to-delivery status, retry acceptance and state refresh, template version creation, provider toggles, and mobile/desktop navigation. Add load tests for paginated reads and dashboard fan-out before enabling frequent refresh in production.