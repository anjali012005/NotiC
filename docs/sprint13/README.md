# Sprint 13: Retry routing and dead-letter handling

Sprint 13 completes the transport reliability story around RabbitMQ delivery by adding explicit retry timing and dead-letter semantics for exhausted failures.

## The problem

The project already had a durable outbox in Sprint 12 and a worker that consumed `notification.created` events. However, the retry story was still too weak for operational reliability:

- a provider timeout or transient failure was not consistently classified as retryable
- failed work had no structured retry delay path
- exhausted retries did not have a clear dead-letter destination
- operators had no simple recovery path for poison or permanently failed messages

Without this, a transient provider issue could either loop incorrectly in the main queue or disappear into a generic failure state without a deliberate recovery mechanism.

## Retry model

NotifyFlow now treats retry behavior as a broker-level control concern rather than as an ad hoc database-only retry loop.

```text
NotificationWorker
        |
        | dispatch provider call fails
        v
shouldRetry(exception)
        |
        +-- no -> FAILED
        |
        +-- yes -> next retry delay based on attempt count
                      |
                      v
               notifyflow.retry exchange
                      |
                      +-- notification.retry.5s
                      +-- notification.retry.30s
                      +-- notification.retry.2m
                      |
                      v
                Retry queue / delayed routing
                      |
                      v
               NotificationWorker re-consumes
```

The retry policy is table-driven and configurable:

- `notification.retry.max-attempts`
- `notification.retry.delays`
- `notification.rabbitmq.retry-exchange`
- queue names for delay buckets

A worker failure is considered retryable when the exception text indicates a transient condition such as:

- timeout
- unavailable
- temporary failure
- connection refused
- network issue
- rate limit
- service unavailable

Non-retryable validation problems continue to be recorded as permanent `FAILED` notifications without broker requeueing.

## Dead-letter queue behavior

When a notification has reached the configured retry limit, it is not silently lost. The worker marks the notification as `FAILED` and publishes a message to the dead-letter exchange.

```text
NotificationWorker
        |
        +-- retry exceeded
        v
FAILED status persisted
        |
        v
notifyflow.dlx
        |
        +-- routing key: notification.dlq
        v
notifyflow.dlq queue
        |
        v
manual or operator recovery flow
```

This preserves the operational distinction between:

- transient transport or provider outage
- exhausted retries that need human investigation
- permanently invalid provider/content configuration

## Why this matters

The worker remains responsible for the actual provider dispatch. RabbitMQ is used for delivery and retry flow control, while PostgreSQL remains the source of truth for:

- notification status
- retry count
- failure reason
- final result history

This keeps the system aligned with the transactional outbox design from Sprint 12 and adds the missing operational safety net for repeated transport failures.

## Manual recovery and reprocessing

The management service exposes a recovery endpoint that allows an operator to requeue failed work from the DLQ path for a controlled manual retry.

```text
POST /api/v1/notifications/{id}/retry
POST /api/v1/notifications/dlq/{id}/reprocess
```

This is useful when:

- a downstream provider recovers
- a temporary outage is resolved
- a problem was expected to be transient but exhausted the broker retry path

## RabbitMQ topology

The broker configuration defines:

- main notification exchange and queue
- retry exchange and delay-specific retry queues
- DLX and DLQ queue

The retry queues are not random retries; they intentionally encode delay buckets such as:

```text
notification.retry.5s
notification.retry.30s
notification.retry.2m
```

These values are resolved from the configured retry policy and drive the final reprocessing cadence.

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
OutboxPublisher
        |
        v
RabbitMQ notification.created
        |
        v
NotificationWorker
        |
        +-- transient provider failure -> retry exchange
        +-- permanent issue -> FAILED
        +-- retry limit reached -> DLQ publish
        |
        v
Database status + failure reason + retry count
```

## Verification

The management-service test suite exercises the retry and DLQ flow:

- retryable failure is routed to the retry queue
- non-retryable failure stays permanent
- retry exhaustion publishes to the DLQ
- worker behavior remains idempotent under duplicate claims

Run the suite from the service root:

```powershell
cd backend/services/management-service
.\mvnw.cmd test
```

A full broker-backed check should be done with RabbitMQ running locally:

```powershell
docker compose -f infrastructure/docker/rabbitmq/docker-compose.yml up -d
```

Then verify:

1. a notification is created and consumes normally
2. a transient provider error schedules a retry
3. repeated transient failures eventually route to DLQ
4. the reprocess endpoint requeues a DLQ item successfully
5. the database status and retry count remain consistent with the retry policy

## Outcome

Sprint 13 closes the operational gap between transient instability and permanent failure. NotifyFlow now has a deliberate retry path and a reviewable dead-letter path, while still maintaining PostgreSQL as the source of truth for notification state.
