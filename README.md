# NotifyFlow

Centralized notification infrastructure with a Spring Cloud API Gateway and a Spring Boot management service. The management service stores notifications and outbox events in PostgreSQL, then dispatches asynchronously through RabbitMQ.

## Services and Versions

- API Gateway: Java 21, Spring Boot 3.5.4, Spring Cloud 2025.0.0, port 8080.
- Management service: Java 21, Spring Boot 4.0.7, port 8081.
- Dashboard: Next.js 16, React 19, Node.js 20.19+, port 3000 in Compose.
- PostgreSQL 18 and RabbitMQ 4 are local infrastructure dependencies.
- Each service is an independent Maven project with its own Maven wrapper. There is no Maven root reactor.
- `backend/services/notification-worker` and Redis are currently empty/not used and are not started by Compose.

## Prerequisites

- Java 21 for builds run directly on the host.
- Docker Desktop or Docker Engine with the Docker Compose v2 plugin for the full local stack.
- PowerShell examples below work in Windows; Compose commands also work in Bash.

## Local Environment

Create your private local configuration from the example and replace every `replace-...` value:

```powershell
Copy-Item .env.example .env
```

The `.env` file is ignored by Git and excluded from both image build contexts. The example provisions an API key for the migration's legacy tenant ID. You can add more tenant UUID-to-key entries in `SPRING_APPLICATION_JSON`. Never use the example key outside an isolated local environment.

Validate and start the stack:

```powershell
docker compose config
docker compose build
docker compose up -d
docker compose ps
```

Wait until all five services report healthy. Open the dashboard at `http://localhost:3000`; it reaches the API through the internal Compose Gateway at `http://gateway-service:8080`. Host ports bind to loopback by default: Dashboard `3000`, Gateway `8080`, Management `8081`, PostgreSQL `5432`, RabbitMQ `5672`, and RabbitMQ management UI `15672`. Override host mappings in `.env` when a port is already occupied. Only the dashboard and API Gateway need to be exposed outside a developer machine in a typical deployment; infrastructure and management ports are loopback-bound in the local stack.

Stop without deleting persistent data:

```powershell
docker compose down
```

To delete local database and broker data, explicitly remove named volumes:

```powershell
docker compose down --volumes
```

## Health, Logs, and API

- Gateway health: `http://localhost:8080/actuator/health/readiness`
- Management readiness (database and RabbitMQ): `http://localhost:8081/actuator/health/readiness`
- Prometheus metrics are exposed by both services; other management endpoints are not exposed by the Gateway.
- RabbitMQ management UI: `http://localhost:15672` with the credentials configured in `.env`.

View logs independently:

```powershell
docker compose logs -f gateway-service
docker compose logs -f management-service
docker compose logs -f postgres
docker compose logs -f rabbitmq
docker compose logs -f frontend
```

The management service requires `X-API-Key` on protected routes. For example, submit a notification through the existing Gateway route:

```powershell
Invoke-RestMethod -Method Post `
	-Uri http://localhost:8080/api/v1/notifications/send `
	-Headers @{ 'X-API-Key' = '<configured-key>' } `
	-ContentType 'application/json' `
	-Body '{"templateKey":"WELCOME_EMAIL","recipient":"developer@example.test","variables":{"name":"Developer"}}'
```

The API accepts a request only when the referenced template/provider exists for the authenticated tenant. A successful request is accepted asynchronously; it is not proof of provider delivery.

## Build and Tests

The management application-context test requires PostgreSQL and RabbitMQ. Start just those dependencies, provide database settings to Maven, and then test each standalone project:

```powershell
docker compose up -d postgres rabbitmq
$env:SPRING_DATASOURCE_URL = 'jdbc:postgresql://localhost:5432/notifyflow'
$env:SPRING_DATASOURCE_USERNAME = '<POSTGRES_USER from .env>'
$env:SPRING_DATASOURCE_PASSWORD = '<POSTGRES_PASSWORD from .env>'
$env:SPRING_RABBITMQ_HOST = 'localhost'
$env:SPRING_RABBITMQ_USERNAME = '<RABBITMQ_DEFAULT_USER from .env>'
$env:SPRING_RABBITMQ_PASSWORD = '<RABBITMQ_DEFAULT_PASS from .env>'
Push-Location backend/services/management-service
./mvnw -B verify
Pop-Location
Push-Location backend/services/gateway-service
./mvnw -B verify
Pop-Location
```

The same commands are run in GitHub Actions with disposable PostgreSQL and RabbitMQ service containers. CI also builds both Docker images; it does not publish or deploy them.

## Database Migrations

Flyway is the schema authority and Hibernate runs with `ddl-auto: validate`. New databases apply V16 (tenant table and legacy tenant) followed by V17 (the complete application schema). For an existing database with no Flyway history, `baseline-on-migrate` baselines it at version 15, after which V16 backfills existing rows and V17 ensures the full schema exists. This baseline assumes the pre-existing tables represent the legacy application schema; take a backup and inspect schema/data before upgrading a database with a different history.

Migrations run during management-service startup. Back up the database before deploying migrations to a persistent environment. Keep Flyway history and applied migration files intact; do not edit an already-applied version. For a fresh local database, `docker compose down --volumes` and `docker compose up -d` starts a clean install, but permanently deletes local DB and RabbitMQ data.

## Configuration and Secrets

Compose reads the root `.env` file. For non-Compose execution, set the equivalent Spring environment variables directly:

- `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`.
- `SPRING_RABBITMQ_HOST`, `SPRING_RABBITMQ_PORT`, `SPRING_RABBITMQ_USERNAME`, `SPRING_RABBITMQ_PASSWORD`.
- `SPRING_APPLICATION_JSON` for `notifyflow.security.api-keys`, mapping tenant UUIDs to API keys.
- `NOTIFYFLOW_SECURITY_ENABLED` (defaults to `true`); do not disable outside isolated local development.
- `SENDGRID_API_KEY` only when deliberately testing real SendGrid delivery; use a fake/stub provider for routine testing.
- `SERVER_PORT`, `MANAGEMENT_SERVICE_URL`, `SPRING_LIFECYCLE_TIMEOUT`, and the documented connection timeout variables when overriding defaults.

Store production secrets in the platform's secret manager. The current API-key configuration is an in-memory map of plaintext values, and there is no key expiry/revocation API. Production deployments need a controlled rotation process and appropriately restricted access to configuration and Actuator endpoints.

## Recovery and Operations

- PostgreSQL and RabbitMQ data survive container recreation in named volumes. Back up PostgreSQL independently; volumes are not backups.
- If dependencies restart, Compose restarts containers and the management service allows missing queues during startup. Check readiness and logs; RabbitMQ consumers reconnect, while the outbox poller continues retrying unpublished events.
- The consumer acknowledges a message only after its processing path returns. A crash around an external provider call can leave delivery ambiguous and may lead to a duplicate attempt; exactly-once email delivery is not guaranteed.
- The outbox records publish attempts and retains failed/unpublished rows for retry. Inspect pending events, RabbitMQ retry/DLQ queues, and notification status when recovering an incident.
- Graceful shutdown is enabled with a 30-second default phase timeout. Validate this against production request volume and provider latency before selecting deployment termination grace periods.
- No deployment target, registry credentials, or production environment is configured. CI builds images but does not deploy them.

## Troubleshooting

- **PostgreSQL connection failure:** check `docker compose ps`, the Postgres health state, `SPRING_DATASOURCE_*`, and host port conflicts.
- **RabbitMQ unavailable:** inspect `docker compose logs rabbitmq`; confirm configured credentials and that the `rabbitmq` service is healthy.
- **Unhealthy management readiness:** inspect management logs and database/broker health; readiness intentionally includes both dependencies.
- **Flyway validation/migration failure:** stop rollout, take/verify a backup, check `flyway_schema_history` and actual table definitions, and do not delete migration history to force startup.
- **Port conflict:** override the corresponding `*_HOST_PORT` in `.env` and use that host port in URLs.
- **Provider configuration error:** verify the tenant's provider configuration includes `fromEmail`; set a SendGrid credential only through a secret source, never in Git.

## Deployment Status

The Gateway, management service, and dashboard have container build definitions and a local Compose topology. This is not a production deployment. Before production, configure managed secrets, TLS/ingress, restricted network access, durable/monitored backups, log/metric collection, API-key lifecycle management, load-tested resource limits, and a tested restore and rollback procedure.


#Author 
#Anjali Daharwal
