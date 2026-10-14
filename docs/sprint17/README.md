# Sprint 17: Deployment Readiness, Runtime Health & Local Stack

Sprint 17 turns NotifyFlow from a working service set into a deployable local runtime. The system already has the notification pipeline, retry logic, provider protection, tenant-aware security, and observability. What it still lacked was the operational packaging needed to start the stack end-to-end: runtime configuration, container orchestration, health checks, and CI verification.

This sprint brings the platform to a state where a developer can stand up PostgreSQL, RabbitMQ, the management service, and the gateway from a single configuration and verify the stack is healthy before any production deployment is considered.

## The problem

The core platform already implemented the business flow:

```text
Client
  ↓
Gateway
  ↓
Management service
  ├── PostgreSQL transaction
  ├── transactional outbox
  └── RabbitMQ publication
  ↓
Notification worker
  ├── processing and retries
  └── provider dispatch
```

But the application was still missing several deployment essentials:

- no single runtime stack definition for local operation
- localhost-only configuration assumptions
- no Docker build definitions for the Java services
- no environment template for DB and broker credentials
- no CI workflow to validate the stack on every change
- no clear deployment and operational documentation for service startup and health

As a result, the project could build in isolation but not yet be started in an end-to-end operational model.

## Sprint 17 goals

Sprint 17 focuses on four concrete outcomes:

### 1. Local stack orchestration

The repo now includes a Compose stack that provisions the required runtime dependencies and brings the application services together.

- PostgreSQL 18
- RabbitMQ 4
- management-service
- gateway-service

This allows the project to start as a coherent environment instead of depending on manually started services in different terminals.

### 2. Environment-driven configuration

The services now read dynamic environment values instead of hardcoded hostnames and credentials.

- database connection uses service names inside Compose
- RabbitMQ connection uses the container network
- API keys and security settings are supplied from `.env`
- host ports remain configurable and local-loopback bound by default

This keeps the same code clean while making it suitable for both local execution and future deployment targets.

### 3. Health and readiness checks

Each service exposes readiness endpoints and Docker health checks so the runtime can tell when the platform is actually ready.

- `http://localhost:8080/actuator/health/readiness` for the gateway
- `http://localhost:8081/actuator/health/readiness` for the management service
- PostgreSQL and RabbitMQ health checks ensure dependencies are available before app services start

This gives the team a real answer to the operational question: "Is the platform ready to accept traffic?"

### 4. CI validation for the real runtime contract

The project adds a GitHub Actions workflow that verifies both Maven projects and builds the Docker images.

The CI path ensures:

- the management service starts with PostgreSQL and RabbitMQ present
- the gateway service still builds cleanly
- both images compile successfully
- the project is validated as a service set, not only as individual modules

## Architecture of the Sprint 17 runtime

```text
Developer machine
  ↓
docker compose
  ├── postgres (database)
  ├── rabbitmq (messaging)
  ├── management-service
  │    ├── Flyway migrations
  │    ├── JPA + PostgreSQL
  │    ├── outbox polling
  │    └── RabbitMQ consumer
  └── gateway-service
       └── Spring Cloud Gateway routes to management service
```

The runtime model remains faithful to the core NotifyFlow design:

- PostgreSQL stays the authoritative data store
- RabbitMQ stays the async transport layer
- the gateway stays the external entry point
- Docker and Compose simply provide the orchestration layer needed for repeatability

## Local setup

Start by copying the example environment file:

```powershell
Copy-Item .env.example .env
```

Then validate and start the stack:

```powershell
docker compose config
docker compose build
docker compose up -d
docker compose ps
```

The Compose file binds infrastructure and service ports to loopback by default:

- Gateway: `http://localhost:8080`
- Management service: `http://localhost:8081`
- PostgreSQL: `localhost:5432`
- RabbitMQ: `localhost:5672`
- RabbitMQ management UI: `http://localhost:15672`

Use the values in the `.env` file to override host ports when needed.

## Service health and operational checks

Check the status of the full stack:

```powershell
docker compose ps
docker compose logs -f management-service
docker compose logs -f gateway-service
```

Verify the readiness endpoints:

```powershell
Invoke-RestMethod http://localhost:8081/actuator/health/readiness
Invoke-RestMethod http://localhost:8080/actuator/health/readiness
```

The management service requires tenant-bound API-key authentication on protected routes. Use the configured key in the headers for requests that hit the protected API.

## Build and verification

The project validates the Java services directly as well as in CI.

From the management service:

```powershell
cd backend/services/management-service
./mvnw -B verify
```

From the gateway service:

```powershell
cd backend/services/gateway-service
./mvnw -B verify
```

The GitHub workflow runs the same checks with disposable PostgreSQL and RabbitMQ containers:

```yaml
- name: Verify management service
- name: Verify API Gateway
- name: Build management image
- name: Build gateway image
```

This is a practical verification path for merge confidence and for future pipeline expansion.

## Configuration and secrets

Important settings belong in `.env` and not in the repository itself.

Required values include:

- PostgreSQL credentials
- RabbitMQ credentials
- API-key map for tenant security
- optional SendGrid credential for real-provider testing
- feature toggles such as `NOTIFYFLOW_SECURITY_ENABLED`

The repo includes `.env.example` as the template. For production use, move secrets into a managed secret store instead of local environment files.

## Design decisions for Sprint 17

### Keep deployment simple and explicit

The sprint intentionally does not add a full platform abstraction. Instead it creates a clear local runtime model with Compose, Dockerfiles, and health checks. This keeps the deployment story understandable and easy to evolve.

### Keep the app runtime environment-driven

The runtime does not rely on developer-specific machine state. Service addresses, credentials, and operating flags are injected from configuration. This is essential for running in containers and for any future staging or production deployment.

### Separate runtime readiness from business success

A service can be healthy while a notification can still fail downstream. The readiness endpoint tells whether the application and dependencies are available, but it does not guarantee provider delivery. This is the correct operational boundary for a notification platform.

## Operational notes

- Docker Compose is the local runtime starter for the project.
- named volumes preserve PostgreSQL and RabbitMQ data across container restarts
- `docker compose down` stops the stack without deleting volume data
- `docker compose down --volumes` removes local data intentionally
- the project is not yet a production deployment target; it is a validated local deployment baseline
- production operation still needs TLS, ingress, secret rotation, backups, and monitoring

## Deployment status

Sprint 17 establishes the end-to-end runtime baseline for NotifyFlow:

- services can be composed locally
- dependencies start in the correct order
- health checks validate readiness
- Docker images build successfully
- CI verifies the service stack and packaging

This is a strong operational foundation, and it leaves the repo ready for the next phase of environment hardening or production deployment planning.
