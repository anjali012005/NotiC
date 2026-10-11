# Sprint 14: Provider Rate Limiting, Throttling & Circuit Breaker

Sprint 14 strengthens NotifyFlow against provider overload, slow downstream responses, and repeated failure patterns. The project has already established the transactional outbox, RabbitMQ delivery, retry queues, and dead-letter handling. Sprint 14 focuses on protecting external providers such as SendGrid from being overwhelmed by a burst of work and on making repeated failures fail fast rather than compounding the problem.

## The problem

NotifyFlow already supports asynchronous processing and retry flows. That means a failing provider can be retried repeatedly, but without protection this can create a dangerous feedback loop:

```text
NotifyFlow
    ↓
Provider (SendGrid / SMTP / Twilio / Firebase)
    ↓
Provider is slow, rate-limited, or unavailable
    ↓
Consumers keep sending requests
    ↓
More load on provider
    ↓
More errors and longer latency
```

At scale, a single externally unhealthy provider can create downstream congestion, unnecessary retry traffic, and false operational confidence. We need to protect both sides:

- NotifyFlow should not flood external APIs
- external providers should not be forced to absorb unpredictable traffic spikes
- retries should be intelligent, not infinite
- a repeated failure should eventually trigger a circuit-breaker response

## Current NotifyFlow architecture

The current system supports:

- notification template CRUD and versioning
- template rendering
- provider management
- notification lifecycle tracking
- provider dispatchers
- SendGrid integration
- async processing through RabbitMQ
- transactional outbox publishing
- retry routing and DLQ handling

High-level flow:

```text
Client
   ↓
Management API
   ↓
PostgreSQL Transaction
   ├── Notification = QUEUED
   └── Outbox Event = PENDING
   ↓
HTTP 202
   ↓
Outbox Publisher
   ↓
RabbitMQ
   ↓
Notification Consumer
   ↓
PROCESSING
   ↓
Dispatcher Factory
   ↓
Provider
   ↓
SENT / FAILED / RETRY / DLQ
```

The key design choice is that the database remains the system of record, RabbitMQ is the transport layer, and provider dispatchers remain a thin execution boundary. Sprint 14 adds protection around the provider call itself, not around the database lifecycle.

## Why rate limiting matters

Rate limiting exists because a provider is not an infinite-capacity dependency. A provider may define limits such as:

```text
SendGrid: 100 requests / second
Twilio: 50 requests / second
SMTP server: configured throughput
Firebase: project quotas and platform limits
```

If NotifyFlow ignores these limits, the platform becomes the source of its own failure:

- provider latency increases
- provider errors become more frequent
- retries create more request pressure
- worker capacity is wasted on doomed sends

Rate limiting is therefore not just a nice-to-have. It is a control mechanism that ensures a healthy steady-state throughput instead of a provider-shaped DDoS.

## Rate limiting vs throttling

The two terms are related but not identical.

### Rate limiting

Rate limiting is the rule that defines how many requests are allowed in a period of time.

Example:

```text
Provider limit: 100 requests / second
```

The system should enforce a hard ceiling so it does not exceed the agreed rate.

### Throttling

Throttling is the operational behavior used to smooth traffic when the demand is higher than the allowed rate.

Example:

```text
10,000 notifications queued
provider capacity: 100 requests / second
```

NotifyFlow should not reject everything immediately, but should instead control the outgoing flow so requests are spaced and processed without creating a pile-up. Throttling is a strategy for controlled processing rather than uncontrolled sending.

## Provider-specific limits

Different providers have different limits and operational constraints.

Examples:

- SendGrid may rate-limit by endpoint and region
- Twilio may enforce message throughput differently by channel
- SMTP may be constrained by server throughput and IP reputation
- Firebase push delivery may depend on platform quotas and project-level quota plans

These limits should not be hardcoded into business logic. They should be bound to provider configuration and selected as part of the provider setup.

The existing provider model already separates provider metadata from notification logic. Sprint 14 fits naturally into that architecture by making provider behavior configurable through provider-specific settings rather than ad-hoc constants.

A reasonable configuration shape is:

```yaml
providers:
  sendgrid:
    rate-limit:
      requests-per-second: 100
      burst: 20
    circuit-breaker:
      failure-threshold: 5
      open-duration-ms: 30000
      half-open-max-calls: 2
```

The final shape should be derived from the actual project provider configuration model and environment strategy.

## Where rate limiting belongs

Rate limiting should not be scattered across unrelated layers. The right place is on the provider boundary, close to the actual dispatch operation.

A suitable boundary is:

```text
RabbitMQ Consumer
    ↓
NotificationWorker / Retry Policy
    ↓
Provider Protection Layer
    ├── Rate Limiter
    ├── Retry / Backoff
    └── Circuit Breaker
    ↓
Dispatcher Factory
    ↓
Provider-specific Dispatcher
    ↓
Provider API
```

This is stronger than putting rate limiting inside every dispatcher because it keeps provider access control centralized. A dispatcher is responsible for format and protocol details, while the provider protection layer decides whether a provider call is currently allowed to proceed.

The rate limiter should sit in front of the actual provider HTTP request rather than inside unrelated app logic.

## Multi-instance problem

If NotifyFlow runs with multiple service instances, each instance may independently decide it is allowed to send at a local rate:

```text
Instance A → 100 requests/sec
Instance B → 100 requests/sec
Instance C → 100 requests/sec
```

The provider may see a combined total of:

```text
300 requests/sec
```

This is the distributed rate-limiting problem.

### Local rate limiting

A local limiter uses in-memory state inside one process. It is easy to implement and works well when there is only a single instance. It is not enough when the system is horizontally scaled.

### Distributed rate limiting

A distributed limiter requires shared state across instances. This is where Redis, a centralized token bucket, or another coordination mechanism may become necessary.

For Sprint 14, the simplest production-reasonable approach is:

- start with provider-local throttling and circuit state
- keep the implementation centralized in a provider protection abstraction
- do not add Redis unless the project clearly needs shared cross-instance quotas

If a multi-instance deployment later requires strict global provider quotas, Redis is the most natural next step because it provides shared state across application nodes.

## Circuit breaker pattern

The circuit breaker is meant to prevent repeated calls to a provider that is already failing.

Without it, the system behaves like this:

```text
SendGrid is down
    ↓
Every notification tries SendGrid again
    ↓
Retry storm
    ↓
More load on provider
    ↓
More failures
```

This creates a self-reinforcing problem. The circuit breaker breaks that loop.

The expected states are:

```text
CLOSED
    ↓
Failure threshold reached
    ↓
OPEN
    ↓
Wait period
    ↓
HALF-OPEN
    ↓
Limited test request
    ↓
SUCCESS → CLOSED
FAILURE → OPEN
```

### CLOSED

Provider is healthy and requests are allowed.

### OPEN

Provider is considered unhealthy for a defined cool-down period. Requests are rejected or bypassed before reaching the provider.

### HALF-OPEN

The system allows a very small number of probes to verify whether the provider has recovered.

This pattern is especially useful for SLO-sensitive external dependencies that are expensive to call and expensive to fail repeatedly.

## Circuit breaker behavior

A typical flow:

```text
CLOSED
    ↓
Failure count grows
    ↓
Threshold reached
    ↓
OPEN
    ↓
After cooldown
    ↓
HALF-OPEN
    ↓
Small number of test calls
```

If the test passes, the breaker moves back to closed. If it fails, it reopens. This prevents a provider from being hammered while it is recovering.

For Spring-based projects, a Spring-compatible resilience library may be appropriate if it matches the project style and does not introduce unnecessary complexity. The key decision is not whether a library exists, but whether it fits the architecture cleanly without hiding provider-specific behavior.

## Circuit breaker vs retry

These concepts are distinct:

### Retry

Retry means "this operation may succeed on a subsequent attempt. Try it again later."

### Circuit breaker

Circuit breaker means "this provider is failing repeatedly. Stop calling it for a while."

These should complement each other, not fight each other.

Example:

```text
Provider call fails
    ↓
Retry policy decides whether to retry
    ↓
Repeated failures
    ↓
Circuit opens
    ↓
Further requests are blocked for a controlled time
```

If the retry layer and circuit-breaker layer both keep reattempting continuously, the system can destabilize itself. The design should be explicit: retries happen while the circuit is healthy, and the circuit prevents retry storms when the provider is repeatedly unhealthy.

## Provider fallback

A future NotifyFlow deployment may support multiple providers for the same channel.

Example:

```text
Email providers:
- SendGrid
- SMTP
```

If SendGrid is unavailable or its circuit is open, a fallback strategy may be desirable. But a fallback is not always safe or desirable.

The system must ask:

- is a fallback provider configured?
- is the fallback compatible with the same notification channel?
- is the fallback safe for the same tenant, template, and message type?
- should fallback occur automatically or only on explicit policy rules?
- could fallback lead to duplicate delivery after a provider accepted the message but the response was lost?
- are we falling back because of a temporary availability issue or because the original provider rejected the request permanently?

Fallback is a distributed-systems decision, not just a simple if/else branch.

## Duplicate-delivery risk

This is one of the critical concerns in a provider fallback design.

Example:

```text
NotifyFlow sends to SendGrid
    ↓
SendGrid accepts the message
    ↓
The response is lost or delayed
    ↓
NotifyFlow thinks it failed
    ↓
Fallback to SMTP
```

Now the same notification may be delivered twice.

This is why fallback should not happen after every failure. It should only happen under well-defined conditions, with awareness that exactly-once delivery is not guaranteed in distributed systems.

The right approach is to avoid silent fallback for ambiguous outcomes, and to use provider idempotency or domain-aware deduplication where the provider supports it.

## Provider health tracking

The system should maintain provider health data, but it should avoid duplicating runtime state unnecessarily.

Examples of useful provider health data:

- provider name and type
- success count
- failure count
- last failure timestamp
- last success timestamp
- current circuit state
- last error classification

This information can be kept in runtime state for the circuit breaker, while persistent operational data can be logged or stored separately when needed. PostgreSQL should not be overloaded with transient runtime metrics if the circuit breaker already maintains its own state.

The balance is:

- runtime state: current breaker status, current failure counters, current throttle state
- persistent state: provider metadata, configuration, and operational status history if needed
- metrics: aggregated operational monitoring data for dashboards and analysis

## Retry integration with Sprint 13

Sprint 13 already introduces retry routing and DLQ handling. Sprint 14 adds a protection layer around the provider call so that retry behavior remains safe.

Example flow:

```text
Provider call
    ↓
Failure
    ↓
Is it retryable?
    ↓
YES
    ↓
Retry / backoff
```

But if the provider is repeatedly failing:

```text
Repeated failures
    ↓
Circuit breaks
    ↓
No more provider calls for a period
    ↓
DLQ or retry policy continues only for valid cases
```

The interaction between retry, rate limiting, circuit breaker, and DLQ must be explicit. The design should prevent a retry storm from overwhelming a bad provider while still allowing a valid recovery path when the provider resumes service.

## Classification of failure types

Not every failure should be treated the same.

### HTTP 429: Too many requests

This often signals provider throttling. It may require:

- temporary delay
- respect for Retry-After if provided
- rate-limit backoff at the provider protection layer

### HTTP 500

This may be retryable depending on provider semantics, but not always. Some 5xx errors are transient; some indicate server-side misconfiguration.

### HTTP 503

This is a classic signal for temporary unavailability. It often combines well with retry and a circuit-breaker response.

### HTTP 400

This is usually non-retryable unless the error is a temporary validation or request-shape issue that can be corrected. Do not blindly classify every 4xx as permanent, but do not treat every 4xx as retryable either.

The correct classification depends on the provider contract and the actual failure payload.

## Provider dispatcher boundary

The existing project already separates provider-specific delivery logic behind a dispatcher abstraction:

```text
NotificationDispatcher
NotificationDispatcherFactory
SendGridDispatcher
SmtpDispatcher
TwilioDispatcher
FirebaseDispatcher
```

This is the correct place to preserve separation of concerns.

The dispatcher should remain focused on:

- request formatting
- provider protocol details
- HTTP or SDK calls
- provider result interpretation

The provider protection layer should remain focused on:

- rate limiting
- throttling policy
- circuit state
- retry classification
- backoff and provider health decisions

This avoids mixing transport logic, rate policy, and application behavior inside each provider implementation.

## Target architecture

A production-ready Sprint 14 design should conceptually resemble:

```text
                         RabbitMQ
                             ↓
                    Notification Consumer
                             ↓
                    Retry / Failure Policy
                             ↓
                     Provider Protection
                   ┌─────────┴─────────┐
                   │                   │
              Rate Limiter        Circuit Breaker
                   │                   │
                   └─────────┬─────────┘
                             ↓
                     Dispatcher Factory
                             ↓
                         Provider
                             ↓
                    SendGrid / SMTP / ...
```

The exact placement should follow the established NotifyFlow architecture and keep provider policy centralized rather than duplicated.

## Configuration

Provider protection must stay configurable. It should not be hardcoded inside the business logic or inside individual dispatcher classes.

Examples of settings to support:

- requests-per-second
- burst capacity
- maximum provider failures before trip
- open duration
- half-open call limit
- retry delays
- provider fallback configuration

These settings should live in environment configuration or application configuration, not in code constants.

## Testing requirements

Sprint 14 should add tests for the following:

### 1. Rate limiting

Verify the provider is not called more than the configured rate.

### 2. Throttling

Verify excessive workload is controlled instead of creating a provider traffic spike.

### 3. Circuit opens

Simulate repeated provider failures and verify the breaker transitions to OPEN.

### 4. Circuit blocks calls

When OPEN, verify provider calls are stopped.

### 5. Half-open

After the configured wait duration, verify limited probe requests are allowed.

### 6. Recovery

If the probe succeeds, verify the breaker returns to CLOSED.

### 7. Continued failure

If a half-open probe fails, verify the breaker remains OPEN.

### 8. Retry integration

Verify retry behavior does not create an infinite loop while the circuit is OPEN.

### 9. 429 handling

Verify provider rate-limit responses are handled with the correct delay/backoff behavior.

### 10. Fallback

If fallback is implemented, verify it only occurs under valid conditions and that duplicate delivery risk is explicitly considered.

## Important constraints

- Do not remove RabbitMQ
- Do not remove the transactional outbox
- Do not remove the DLQ
- Do not duplicate provider dispatch logic
- Do not create unlimited retry loops
- Do not bypass the existing retry strategy
- Do not assume every provider failure should trigger fallback
- Do not claim exactly-once send guarantees
- Do not introduce Redis unless there is a clear architectural need
- Do not add unnecessary infrastructure
- Do not implement a circuit breaker from scratch if a suitable, established library is appropriate
- Keep provider configuration external and configurable
- Explain the concepts before implementation
- Implement incrementally
- Test each major behavior

## Sprint 14 completion criteria

Sprint 14 is complete when NotifyFlow can safely handle provider overload and provider outages.

The intended behavior is:

```text
Notification
     ↓
RabbitMQ
     ↓
Consumer
     ↓
Provider Protection
     │
     ├── Rate Limiting
     │
     ├── Retry
     │
     └── Circuit Breaker
     ↓
Provider
```

### Healthy provider

```text
Request
 ↓
Rate limiter
 ↓
Provider
 ↓
Success
```

### Temporary failure

```text
Request
 ↓
Provider failure
 ↓
Retry or backoff
```

### Repeated provider failure

```text
Repeated failures
       ↓
Circuit opens
       ↓
Calls stop temporarily
       ↓
Half-open check
       ↓
Recover or reopen
```

### Final failure path

```text
Retry limit reached
       ↓
DLQ
```

## Final learning goal

By the end of Sprint 14, NotifyFlow should clearly understand:

- rate limiting
- throttling
- distributed rate limiting
- provider-specific limits
- circuit breakers
- CLOSED / OPEN / HALF-OPEN states
- retry vs circuit breaker
- 429 handling
- provider health
- provider fallback
- duplicate delivery risk
- how these concepts work together in a distributed notification system

This is not just an implementation sprint. It is a resilience sprint that teaches how to protect external provider dependencies without causing a system-wide retry storm.

## Verification checklist

1. Inspect the current dispatcher and provider architecture before making changes.
2. Confirm provider-specific limits can be configured without hardcoded values.
3. Add a rate-limiting layer at the provider boundary, not across the business layer.
4. Add circuit-breaker logic to stop repeated failures.
5. Verify retry and circuit-breaker behavior do not fight each other.
6. Test 429 and temporary outage handling.
7. Add fallback logic only under clearly safe conditions.
8. Confirm the DLQ and retry path remain intact.
9. Run the management-service tests after implementation.
10. Review whether distributed throttling is needed before introducing more infrastructure.

## Expected outcome

Sprint 14 should leave NotifyFlow with a provider protection layer that makes the system more resilient under overload and increased failure conditions. This closes the gap between retrying messages and actually protecting the external provider dependency chain.
