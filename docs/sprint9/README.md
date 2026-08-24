# Sprint 9 — Provider Integration & Dispatch

## Goal

Sprint 9 makes provider dispatch extensible and adds a real SendGrid email adapter without changing the notification API.

## Architecture

`NotificationServiceImpl` renders the active template, finds the enabled default provider, records `QUEUED`, and moves it to `PROCESSING`. `NotificationDispatcherFactory` validates the provider type/channel and resolves a `NotificationDispatcher`. The adapter either completes delivery or throws a safe provider exception. The existing tracking service records `SENT` plus `sentAt`, or `FAILED` plus a failure reason. Retry reuses exactly the same factory and dispatcher path.

The factory maps `SENDGRID` to `SendGridDispatcher`; SMTP, Twilio and Firebase remain registered for compatibility but deliberately fail rather than claim a fake delivery. AWS SES, MSG91 and OneSignal are rejected as unsupported until adapters are added.

## SendGrid configuration

Create an enabled default EMAIL provider with `providerType: "SENDGRID"`. Its stored `config` is JSON:

```json
{
  "fromEmail": "notifications@example.com",
  "fromName": "NotifyFlow",
  "apiKey": "optional-if-SENDGRID_API_KEY-is-set"
}
```

`fromEmail` is required. The API key may instead be supplied through `SENDGRID_API_KEY`. `SENDGRID_BASE_URL` defaults to SendGrid's Mail Send endpoint and is useful for a local test stub; `SENDGRID_TIMEOUT_SECONDS` defaults to 10. Provider configuration is never returned in provider API responses; responses expose only `configured`.

## Error handling and lifecycle

Malformed/missing config, mismatched channel, unsupported types, timeouts and non-2xx SendGrid responses result in a failed tracked notification with a safe reason. API keys and request bodies are not logged. A success must be a 2xx SendGrid response—there is no simulated success path.

## Manual verification

Import [the Sprint 9 Postman collection](../postman_sprint9_collection.json). Set `sendgridApiKey` (or leave it blank when the service has `SENDGRID_API_KEY`), then run the requests in order.

1. Create a SENDGRID EMAIL provider with the config above, enabled and default.
2. Create/activate an EMAIL template, then `POST /api/v1/notifications/send`.
3. Confirm the response is `SENT` and `GET /api/v1/notifications/{id}` has `sentAt`.
4. Use an invalid key or local endpoint to force failure; confirm `FAILED` and `failureReason`.
5. Call `POST /api/v1/notifications/{id}/retry`; it uses the same SendGrid dispatcher and honors `notification.max-retry-count`.

## Adding a provider

Implement `NotificationDispatcher`, register it in `NotificationDispatcherFactory`, and add its valid channel mapping there. Keep credentials in the provider config/environment, avoid logging them, and add adapter success/failure tests. No controller or notification-service provider-specific changes should be needed.

## Limitation

Live delivery requires valid SendGrid credentials and network access. The adapter can be tested without them using `SENDGRID_BASE_URL` against a local HTTP stub.
