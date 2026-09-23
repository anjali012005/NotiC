# Sprint 12: Transactional Outbox Pattern

Sprint 12 removes the database-to-RabbitMQ consistency gap from notification creation.

## The problem

Before the outbox, the management service could complete these operations separately:

```text
1. Save Notification as QUEUED in PostgreSQL
2. Publish notification.created to RabbitMQ
```

If RabbitMQ was unavailable after step 1, the notification remained `QUEUED` without a reliable broker message. A reconciliation poll could reduce the impact, but the database row and the publish intent were still not committed atomically.

## The Transactional Outbox Pattern

NotifyFlow now commits two PostgreSQL records in the notification creation transaction:

```text
NotificationService
        |
        v
PostgreSQL transaction
        +-- notifications: QUEUED
        +-- outbox_events: PENDING
        |
        v
     COMMIT
        |
        v
OutboxPublisher -> RabbitMQ -> NotificationWorker -> Provider
```

The notification service is responsible for validation, rendering, provider selection, notification creation, and outbox creation. It does not publish directly to RabbitMQ and does not send email.

The outbox publisher is responsible only for moving persisted events from PostgreSQL to RabbitMQ. The existing notification worker remains responsible for claiming notifications, invoking `NotificationDispatcherFactory`, and recording `SENT` or `FAILED`.

## Why one @Transactional method cannot cover RabbitMQ

A Spring `@Transactional` method coordinates the PostgreSQL transaction through the configured database transaction manager. RabbitMQ uses a separate connection, broker, acknowledgment protocol, and transaction boundary. RabbitMQ is not automatically enlisted in the PostgreSQL transaction.

Therefore, this does not provide atomicity by itself:

```java
@Transactional
public void send() {
    notificationRepository.save(notification);
    rabbitTemplate.convertAndSend(exchange, routingKey, message);
}
```

The database can commit while the broker publish fails. Conversely, RabbitMQ can accept the message and the application can crash before PostgreSQL commits. Coordinating both systems would require distributed transaction infrastructure and would add substantial operational complexity. The outbox instead makes PostgreSQL the durable source of truth for the intent to publish.

## Outbox event model

The `outbox_events` table contains:

| Field | Purpose |
| --- | --- |
| `id` | Identifies the outbox event. |
| `event_type` | Describes the event, currently `NOTIFICATION_CREATED`. |
| `aggregate_type` | Identifies the domain type, currently `NOTIFICATION`. |
| `aggregate_id` | References the notification UUID without duplicating the notification. |
| `payload` | Stores the small event payload, currently `{"notificationId":"UUID"}`. |
| `status` | Uses the simple lifecycle `PENDING` or `PUBLISHED`. |
| `attempt_count` | Counts publisher attempts for diagnostics and retry limits. |
| `created_at` | Preserves event creation order and supports oldest-first polling. |
| `updated_at` | Tracks the latest database update. |
| `published_at` | Records when the publisher marked the event published. |
| `last_error` | Keeps the latest RabbitMQ failure for investigation. |

The payload intentionally contains only the notification ID. PostgreSQL remains authoritative for recipient, rendered content, provider, and lifecycle state.

## Outbox statuses

### `PENDING`

The database transaction committed the event, but the publisher has not successfully recorded a publish yet. It is eligible for polling and retry, subject to the configured maximum attempt count.

If RabbitMQ is temporarily unavailable, the publisher records `attempt_count` and `last_error`, then leaves the event `PENDING`. The intent is not silently lost.

### `PUBLISHED`

The publisher's RabbitMQ call returned successfully and the event was updated with `published_at`. This means the publisher completed its handoff attempt; it does not mean the provider has delivered the notification.

## Publisher polling and concurrency

`OutboxPublisher` polls every five seconds by default and processes up to 20 events per batch. It selects pending rows oldest first and uses a pessimistic database lock equivalent to PostgreSQL `FOR UPDATE` behavior. Multiple NotifyFlow instances therefore do not select the same pending row concurrently while a publisher transaction is processing it.

The publisher has a configurable automatic limit of 10 attempts by default. Events that reach the limit remain persisted as `PENDING` for inspection or an explicit recovery policy; they are not deleted.

This polling is different from Sprint 10 polling:

```text
Sprint 10:
NotificationWorker -> poll QUEUED notifications -> process providers

Sprint 12:
OutboxPublisher -> poll PENDING outbox events -> publish RabbitMQ messages
```

The Sprint 12 poller moves durable publish intent from PostgreSQL to RabbitMQ. It does not send notifications.

## Failure scenarios

### Database transaction fails

If notification creation or outbox creation fails, the PostgreSQL transaction rolls back. Neither the notification nor its outbox event remains persisted.

### Crash after commit, before publishing

Both records exist:

```text
Notification: QUEUED
Outbox event: PENDING
```

When the publisher restarts, it finds the pending event and retries it.

### RabbitMQ is temporarily unavailable

The event remains `PENDING`, with its attempt count and latest error recorded. A later publisher run can retry it until the configured automatic limit is reached.

### RabbitMQ accepts the message, then the application crashes

This is the important limitation:

```text
1. Publisher sends the message
2. RabbitMQ accepts it
3. Application crashes before the outbox row is marked PUBLISHED
4. PostgreSQL still says PENDING
5. Publisher restarts and may publish the event again
```

The outbox provides reliable persistence of the intent to publish. It does not provide exactly-once delivery. Duplicate RabbitMQ messages are possible.

## Consumer idempotency

The notification worker already uses an atomic conditional update:

```text
UPDATE notifications
SET status = PROCESSING
WHERE id = :id AND status = QUEUED
```

Only a delivery that successfully changes `QUEUED` to `PROCESSING` proceeds to provider dispatch. A duplicate message for a notification that is already `PROCESSING`, `SENT`, or `FAILED` does not claim the row and does not dispatch it again.

This protects notification processing where the database state allows it. It does not guarantee exactly-once email delivery: a provider may accept a request before a worker crashes, and a later retry can still result in another provider request. Provider-side idempotency support is a separate concern.

## End-to-end flow

```text
POST /api/v1/notifications/send
        |
        v
NotificationService
        |
        +-- PostgreSQL: Notification = QUEUED
        +-- PostgreSQL: OutboxEvent = PENDING
        |
        v
HTTP 202 Accepted
        |
        v
OutboxPublisher
        |
        v
RabbitMQ notification.created
        |
        v
NotificationWorker
        |
        v
QUEUED -> PROCESSING -> SENT / FAILED
        |
        v
NotificationDispatcherFactory -> SendGrid/provider
```

The API does not wait for outbox publishing, RabbitMQ consumption, or provider delivery.

## Verification

The management-service test suite covers:

- Notification creation with outbox persistence.
- Retry creation of a new pending outbox event.
- Successful pending-to-published publishing.
- RabbitMQ failure retaining a pending event and recording diagnostics.
- Atomic notification claims and duplicate message handling.
- Recovery of stale processing notifications.
- Application context startup and PostgreSQL outbox query initialization.

Run the tests from the management service:

```powershell
cd backend/services/management-service
.\mvnw.cmd test
```

RabbitMQ should be running for a live end-to-end check:

```powershell
docker compose -f infrastructure/docker/rabbitmq/docker-compose.yml up -d
```

Then verify that a send request returns `202`, creates a `QUEUED` notification and `PENDING` outbox row, and eventually moves through RabbitMQ processing to `SENT` or `FAILED`.
