# Sprint 10: Asynchronous notification processing

`POST /api/v1/notifications/send` now validates, renders, chooses a provider, and persists a `QUEUED` notification before returning `202 Accepted`. Delivery runs separately in `NotificationWorker`.

The worker polls every five seconds by default, reads a small oldest-first batch, and atomically claims each candidate with a conditional database update: `WHERE id = ? AND status = QUEUED`. With multiple application instances, the update succeeds for exactly one worker; the other workers skip the row. This is intentionally simpler than holding a `SELECT FOR UPDATE SKIP LOCKED` transaction open around a provider network call.

The claim and state transitions are short database transactions. The SendGrid call happens outside a database transaction, then the worker records `SENT` or `FAILED`. This avoids connection/row locks being held during external I/O.

`PROCESSING` rows older than `notification.worker.processing-timeout-minutes` (10 by default) are recovered on every poll. They are re-queued and their retry count is incremented; rows whose retry limit is exhausted become `FAILED`. A worker crash after SendGrid has accepted a request but before `SENT` is stored can still cause an at-least-once delivery on recovery. Eliminating that final ambiguity requires provider-side idempotency support or an idempotency/outbox design, not merely a database lock.

## Test

1. Start the management service with a valid enabled default provider and active template.
2. Import `docs/postman_sprint10_collection.json` into Postman.
3. Run **Queue notification**; it should return `202` and `QUEUED`.
4. Run **Get notification status** after a worker interval; it should move through `PROCESSING` to `SENT` (or `FAILED`).
5. For a failed notification, run **Retry failed notification**. A successful retry request returns `202` and the worker performs delivery asynchronously.

Worker settings are in `application.yaml`: `fixed-delay-ms`, `batch-size`, and `processing-timeout-minutes`.
