# Sprint 11: RabbitMQ notification delivery

## Concepts in NotifyFlow

A message broker transports work between independently running parts of an application. PostgreSQL polling remains useful as recovery, but is not ideal as the primary delivery mechanism because it repeatedly queries for work, adds polling latency, and makes database load grow with the polling frequency. RabbitMQ lets the API publish work immediately and lets workers consume it as capacity becomes available.

NotifyFlow uses this simple topology:

```text
NotificationService (producer)
        |
        v
notifyflow.notifications (direct exchange)
        |
        | notification.created
        v
notifyflow.notification-created (durable queue)
        |
        v
NotificationWorker (consumer)
```

The producer publishes `NotificationCreatedMessage` after the notification has been persisted as `QUEUED`. The message intentionally contains only:

```json
{
  "notificationId": "uuid",
  "eventType": "NOTIFICATION_CREATED"
}
```

The ID is enough to locate authoritative data in PostgreSQL and avoids copying recipient, body, provider, or template data into a transport message that could become stale or expose more information than necessary.

An exchange routes messages; it does not hold work for consumers. The queue durably holds messages until a consumer handles them. The consumer loads the notification, conditionally claims `QUEUED -> PROCESSING`, and reuses `NotificationDispatcherFactory` and the existing provider dispatchers.

## Acknowledgment and duplicates

The listener uses manual acknowledgment. It ACKs only after processing returns successfully. A provider failure is recorded as `FAILED` by the existing application logic and then the message is ACKed: the database retry endpoint controls provider retries. If the consumer process or connection dies before the ACK, RabbitMQ makes the unacknowledged message available again.

Delivery is at-least-once, so duplicate messages are expected. The conditional database claim succeeds for only one delivery. A second delivery sees a non-`QUEUED` row, performs no provider call, and is ACKed. The database remains the source of truth.

The application retry count and maximum retry count are not replaced by RabbitMQ. A manual retry changes `FAILED` to `QUEUED`, increments the database retry count, and publishes another ID message. RabbitMQ redelivery is for consumer failure, not an unbounded second provider retry loop.

## Failure scenarios

- Normal flow: `QUEUED` -> message -> atomic claim -> `PROCESSING` -> provider success -> `SENT` -> ACK.
- Provider failure: `QUEUED` -> `PROCESSING` -> provider exception -> `FAILED` with reason -> ACK. The existing retry endpoint can queue it again while under the configured limit.
- Consumer crash before ACK: RabbitMQ requeues the unacknowledged message when the connection closes. The processing lease and atomic claim protect the database, although a provider may receive a duplicate request if it accepted the request before the crash.
- Duplicate message: only the first `QUEUED -> PROCESSING` claim wins; later deliveries are acknowledged without dispatch.
- RabbitMQ unavailable: PostgreSQL can commit `QUEUED` while publishing fails. Sprint 11 catches the publish failure and leaves the row queued; the slower reconciliation poll republishes/reprocesses queued work. This reduces, but does not eliminate, the database/broker gap.

A durable production solution is the transactional outbox: save the notification and an outbox event in the same PostgreSQL transaction, then publish unsent outbox rows and mark them published. Sprint 12 should replace the best-effort publish plus reconciliation approach with that pattern. A dead-letter queue is also a future Sprint 13 concern for poison messages or transport attempts that exceed a defined limit; it should not silently replace the database retry policy.

## Run locally

Start RabbitMQ and its management UI:

```powershell
docker compose -f infrastructure/docker/rabbitmq/docker-compose.yml up -d
```

RabbitMQ is available at `localhost:5672`; the management UI is at `http://localhost:15672` with `guest` / `guest` for local development. The management service defaults to those connection values and can override them with `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME`, and `RABBITMQ_PASSWORD`.

## Verification checklist

1. Create a valid template and enabled default SendGrid provider.
2. POST `/api/v1/notifications/send`; confirm `202` and database status `QUEUED`.
3. Confirm the message appears in `notifyflow.notification-created`, then confirm `PROCESSING` and `SENT`.
4. Use a failing provider configuration; confirm `FAILED` and `failureReason`.
5. Stop the consumer before ACK, restart it, and confirm the message is delivered again.
6. Publish the same ID twice; confirm only one provider dispatch occurs.
7. Stop RabbitMQ during creation; confirm the notification remains `QUEUED` and is recovered by reconciliation after RabbitMQ returns.
8. Retry a failed notification; confirm `retryCount` increments and a new message is published.

Unit coverage currently exercises normal dispatch, provider failure, duplicate claim protection, notification publication seams, and the existing retry flow. A broker-backed integration test should be added with Testcontainers when the project begins running infrastructure-dependent CI tests.
