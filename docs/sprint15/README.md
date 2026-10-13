# Sprint 15: Observability, Correlation Tracing & Health Monitoring

Sprint 15 makes NotifyFlow operationally visible. The system already handles notifications through a transactional outbox, RabbitMQ delivery, worker processing, retry policies, and provider dispatchers. What it lacked was the ability to answer the operational questions teams need in production:

- Are the services healthy?
- How do notifications move through the system?
- Where do failures occur?
- How long does processing take?
- Are RabbitMQ, the outbox, or a provider having trouble?
- Which request or notification path caused a failure?

This sprint adds observability and traceability so teams can diagnose issues quickly and understand the state of the platform under load.

## The problem

By the time a notification platform reaches production scale, failures are no longer isolated to one function or one service. They become distributed across several moving parts:

```text
Client Request
   ↓
Gateway / Management API
   ↓
Database Transaction + Outbox Entry
   ↓
Outbox Publisher
   ↓
RabbitMQ
   ↓
Notification Worker
   ↓
Provider Dispatcher
   ↓
External Provider / Retry / DLQ
```

Without correlation IDs, health checks, and metrics:

- a request may fail without knowing which notification triggered it
- retries appear random and difficult to explain
- queue lag is invisible until the backlog is severe
- latency is hard to measure by stage
- provider slowness is impossible to distinguish from queue or DB bottlenecks

Sprint 15 closes that operational gap by introducing end-to-end request tracing, metric collection, and health instrumentation.

## Why observability matters

An enterprise notification platform cannot rely on logs alone. Logs are useful, but they become difficult to reason about when traffic is distributed and asynchronous. Operational clarity requires at least three things:

1. Request correlation across components
2. Structured metrics for throughput, latency, and failures
3. Health and readiness endpoints that tell the system state with no guesswork

These capabilities are especially important in a message-based system, because important work often happens after the initial HTTP response has already returned.

## System architecture with observability

The updated architecture adds a tracing and monitoring layer around the existing document flow:

```text
Client
  ↓
Gateway / Management API
  ├── correlation ID assigned or propagated
  ├── HTTP request logs and MDC context
  └── health / metrics endpoints
  ↓
PostgreSQL Transaction
  ├── notification status updates
  └── outbox event persistence
  ↓
Outbox Publisher
  ├── publish success/failure metrics
  └── backlog tracking
  ↓
RabbitMQ
  ├── message correlation headers
  └── consumer trace continuation
  ↓
Notification Worker
  ├── processing latency metrics
  ├── retry scheduling telemetry
  ├── provider dispatch metrics
  └── worker-level failure tracking
  ↓
Provider Dispatcher
  ├── provider outcome metrics
  ├── latency timers
  └── circuit-breaker / rate-limit state awareness
  ↓
Prometheus / Actuator / Log Aggregation
```

This is an observational layer over the same data flow; it does not replace the architecture, it makes it visible.

## Core goals of Sprint 15

Sprint 15 focuses on four concrete outcomes:

### 1. Correlation IDs for traceability

Every request and asynchronous message path should carry a single correlation ID so one end-to-end workflow can be tracked across:

- HTTP request handling
- database writes
- outbox publishing
- RabbitMQ messages
- worker execution
- provider dispatch attempts

The correlation ID should be:

- generated when missing
- accepted when valid
- normalized safely
- placed into MDC for logging
- propagated across message metadata

### 2. Metrics for the real operational picture

The system needs metrics for both business and infrastructure events:

- notifications accepted
- notifications delivered
- notifications failed
- notifications retried
- successful provider dispatches
- provider dispatch failures
- processing latency
- dispatch latency
- retry attempts
- outbox backlog size
- oldest pending outbox age

These metrics provide a direct view into the behavior of the notification lifecycle without digging through logs manually.

### 3. Health and readiness visibility

Spring Actuator exposes service health signals through normal endpoints such as:

- /actuator/health
- /actuator/health/readiness
- /actuator/health/liveness
- /actuator/prometheus
- /actuator/metrics

This allows the platform to tell whether the service is:

- healthy
- ready to accept traffic
- degraded due to infra dependencies
- slow or backed up

### 4. Outbox and queue health insight

The transactional outbox is a critical reliability mechanism. If it stalls, the system can quietly stop processing work. Sprint 15 adds monitoring around:

- pending outbox count
- oldest pending event age
- event publish retries
- publish failures

This makes it possible to tell whether the platform is failing on data persistence, messaging, or downstream provider behavior.

## Implementation approach

The implementation fits directly into the existing NotifyFlow architecture without changing the core business flow.

### Correlation context

A dedicated observability utility stores the current correlation ID in a thread-local context and exposes helpers to:

- read the current value
- generate a valid safe value when absent
- validate header content
- clear the value when the thread ends

This is then used in request filters and worker listeners to keep logging and metrics aligned to the same workflow.

### Request propagation

HTTP requests receive or generate a correlation header and the same ID is retained in MDC. RabbitMQ messages also carry the correlation ID so the worker can continue the same trace when processing occurs asynchronously.

### Micrometer instrumentation

Micrometer counters and timers are used to track:

- notification lifecycle states
- dispatch outcomes
- processing durations
- provider-specific attempts and failures

This gives the service a standardized metrics surface that Prometheus can scrape without custom exporter logic.

### Outbox metrics

The outbox backlog is measured as both:

- the number of pending events
- the age of the oldest pending event

This reveals whether the platform is overwhelmed or simply waiting on a downstream dependency.

## Design decisions

### Keep tracing low-overhead

The implementation avoids heavyweight distributed tracing frameworks and instead uses a lightweight correlation model that fits the current architecture. That keeps the platform simple while providing enough context to debug failures quickly.

### Keep metrics close to business events

Metrics are recorded where the real state transition happens: when notifications are accepted, scheduled for retry, processed, dispatched, or failed. This makes the metrics trustworthy and directly tied to the business workflow.

### Preserve existing operational boundaries

The observability layer does not rewrite the notification pipeline. It adds monitoring around the current design rather than creating a second system of record or another processing model.

## Expected production outcomes

After Sprint 15, a team should be able to answer the following questions quickly:

- Which request or notification is causing a problem?
- Is the system ready to process traffic?
- Is a provider failing or is the queue blocked?
- Is the outbox falling behind?
- Did processing slow down or did dispatch slow down?
- Which stage is the bottleneck?

The sprint turns NotifyFlow from a service that works in isolation into a service that can be reasoned about under operational pressure.

## Acceptance criteria

Sprint 15 is complete when the system demonstrates the following:

- correlation IDs are assigned and propagated across HTTP and RabbitMQ flows
- logs include the same correlation context for request and worker processing
- metrics expose notification lifecycle and provider behavior
- Prometheus endpoints are available through Actuator
- health, readiness, and liveness checks are exposed
- outbox backlog and age are visible in monitoring data
- the platform can identify whether problems are in processing, dispatch, queueing, or provider connectivity

## Validation and verification

The implementation and regression checks are verified through the repository test suite. The management service test run confirms the observability and tracing changes are not breaking the existing notification flows.

Relevant verification command:

```powershell
Set-Location C:\Users\ANJALI\NotiC\backend\services\management-service
.\mvnw test
```

The current verified result is a successful build with tests passing, confirming that the observability features have been integrated without breaking the service behavior.

## Outcome

Sprint 15 establishes the platform’s operational visibility layer. It gives NotifyFlow the instrumentation needed to answer the most common production questions: health, flow, failure location, and processing time. This is a crucial step before deeper scaling, alerting, and production monitoring work.
