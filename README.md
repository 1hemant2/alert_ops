# AlertOps

AlertOps helps teams turn tasks or incoming webhook events into ordered email response paths. A task can represent an operational alert, an onboarding request, or any other item that needs a response. A team member can start an escalation manually, or a webhook can create the task and start it automatically. Email recipients can acknowledge a run to stop later steps.

The product is being prepared for release. See the [product launch checklist](docs/product-launch-readiness.md) for remaining work.

## Features

- **Accounts and teams:** Registration requires email verification before login. Users can belong to multiple teams. Owners and admins can invite members; admins can invite users only. Acceptance requires the invited, verified email address.
- **Tasks:** Name, description, and source, plus optional priority, category, and HTTP(S) reference URL. Priority is a user-defined label. Manually created tasks default to source `Manual`.
- **Response paths:** Ordered steps with a team-member recipient and wait time. Add, edit, delete, and drag steps to reorder them. Runs already started retain their saved step details when a path changes.
- **Escalations:** Create a run from a task and response path, then start it. The UI shows the run and saved state of each step.
- **Webhooks:** Owners and admins can create a webhook with a default path, rotate its secret, and enable or disable it. An incoming event creates a task and starts a run. The complete JSON event is stored with links to both records.
- **Email acknowledgement:** Each escalation email links to a preview page. The recipient confirms there to acknowledge the run and stop later steps.

## How an escalation runs

```text
Manual task + selected path ─┐
                             ├─> task + run ─> step due time saved in PostgreSQL
Webhook event + path ─────────┘                         │
                                                       v
                                            in-memory timer reaches due time
                                                       │
                                                       v
                                            RabbitMQ ready-work queue
                                                       │
                                                       v
                                            consumer checks saved state
                                                       │
                                                       v
                                         SMTP email, next step or acknowledgement
```

PostgreSQL stores tasks, paths, events, runs, step states, and due times. The backend uses in-memory timers for waiting steps, then publishes ready work to RabbitMQ. It confirms publication and retries failed publications. On startup it reconstructs pending timers from saved database state. RabbitMQ holds ready work and handles consumer retries; it is not a delayed queue. Redis holds short-lived workflow state and supports shared webhook rate limiting.

`SENT` means the SMTP server accepted the email, not that it reached an inbox. If SMTP accepts a message and the app stops before saving `SENT`, a retry can send it again.

## Run locally

### Prerequisites

- Docker and Docker Compose v2 for PostgreSQL, RabbitMQ, and Redis
- Java 17 to run the backend from source
- Node.js 22 and npm for the UI
- An SMTP account and verified sender for registration, invitation, and escalation emails

Copy the example configuration, replace the password and JWT placeholders, and keep `.env` out of source control. Generate a JWT key with `openssl rand -base64 32`.

```bash
cp .env.example .env
```

### Option A: backend in Docker

From the repository root:

```bash
docker compose up --build -d
```

Compose starts the backend, PostgreSQL, RabbitMQ, and Redis. It does **not** start the UI. Default addresses: API `http://localhost:8096`, RabbitMQ management `http://localhost:15672`, PostgreSQL `localhost:5432`, and Redis `localhost:6379`. Host ports can be changed in `.env`.

### Option B: backend from the terminal

Start only the dependencies, load `.env`, and map its service credentials to the Spring production profile. This profile runs Flyway migrations and validates the schema.

```bash
docker compose up -d postgres rabbitmq redis
set -a
source .env
set +a
export SPRING_PROFILES_ACTIVE=prod
export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:${POSTGRES_HOST_PORT:-5432}/${POSTGRES_DB}"
export SPRING_DATASOURCE_USERNAME="$POSTGRES_USER"
export SPRING_DATASOURCE_PASSWORD="$POSTGRES_PASSWORD"
export SPRING_RABBITMQ_HOST=localhost
export SPRING_RABBITMQ_PORT="${RABBITMQ_HOST_PORT:-5672}"
export SPRING_RABBITMQ_USERNAME="$RABBITMQ_USER"
export SPRING_RABBITMQ_PASSWORD="$RABBITMQ_PASSWORD"
export SPRING_DATA_REDIS_HOST=localhost
export SPRING_DATA_REDIS_PORT="${REDIS_HOST_PORT:-6379}"
export SPRING_DATA_REDIS_PASSWORD="$REDIS_PASSWORD"
./mvnw spring-boot:run
```

Use `SERVER_PORT` for a different backend port. If changed, update the Vite proxy in `ui/vite.config.ts`.

### Start the UI

In another terminal:

```bash
cd ui
npm ci
npm run dev
```

Open `http://localhost:5173`. With `VITE_API_BASE_URL` empty, Vite proxies `/api` and `/actuator` to `http://localhost:8096`. For a separate UI deployment, set `VITE_API_BASE_URL` to the public API origin at build time and allow the UI origin with `ALERTOPS_CORS_ALLOWED_ORIGINS` on the backend. See the [UI README](ui/README.md).

### Configure email

Set these in `.env` before starting the backend:

```dotenv
SPRING_MAIL_HOST=smtp.example.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=your-smtp-login
SPRING_MAIL_PASSWORD=your-smtp-password
ALERTOPS_EMAIL_FROM=verified-sender@example.com
ALERTOPS_UI_BASE_URL=http://localhost:5173
```

SMTP authentication and STARTTLS default to enabled. For implicit TLS on port 465, set `SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE=true` and `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=false`. The sender must be permitted by your SMTP provider and can differ from the SMTP login. The UI URL is used in verification, invitation, and acknowledgement links.

The terminal startup loads all `.env` values. The current Compose file forwards SMTP values and the sender, but uses the application default UI URL (`http://localhost:5173`). For a different UI origin in Docker, add `ALERTOPS_UI_BASE_URL` to the `app.environment` section of `docker-compose.yml` or pass it through your deployment configuration. Without working SMTP settings, email-dependent flows cannot complete.

### First use

1. Register, verify the address from the email, and log in.
2. Create or select a team. Invite team members from **Members** if they should receive response steps.
3. Create a task with a meaningful source and description.
4. Create a response path with at least one step assigned to a team member.
5. Create a run from the task and path, open its details, and start it.
6. Follow the step state and email. The recipient can open the acknowledgement link and confirm to stop remaining steps.

## Trigger a task through a webhook

An owner or admin creates a webhook on **Webhooks** and selects its default response path. Copy the generated secret when it appears; later list responses do not include it. Rotate the secret if lost or exposed. Send a JSON object to the webhook URL shown in the UI:

```bash
curl -X POST "http://localhost:8096/api/v1/webhooks/<webhook-id>/events" \
  -H 'Content-Type: application/json' \
  -H 'X-AlertOps-Webhook-Secret: <webhook-secret>' \
  -d '{
    "eventId": "request-123",
    "taskName": "New onboarding request",
    "description": "A new customer needs a welcome call.",
    "source": "Signup form",
    "priority": "Today",
    "category": "Onboarding",
    "referenceUrl": "https://example.com/requests/123",
    "customerId": "cust-456"
  }'
```

`eventId`, `taskName`, `description`, and `source` are required. `priority`, `category`, and `referenceUrl` are optional; other JSON fields remain in the saved event. Add `flowId` to use another response path from the same team; otherwise the default is used. The response includes `taskId`, `escalationId`, `flowId`, and `replayed`. A new event returns HTTP 202. Retrying the same `eventId` and payload returns the existing task and run with HTTP 200; reusing the ID with different data returns HTTP 409.

The event route does not require a login JWT, but it checks `X-AlertOps-Webhook-Secret`. The server stores only the secret hash and shows the raw value only on creation or rotation. The default accepted serialized JSON size is 64 KiB (`ALERTOPS_WEBHOOK_MAX_BODY_BYTES=65536`). The default rate limit is 120 requests per webhook per minute (`ALERTOPS_WEBHOOK_RATE_LIMIT=120`), shared through Redis when available. If Redis is unavailable, the fallback counter is per application instance.

## Configuration and operations

| Setting | Purpose | Default |
| --- | --- | --- |
| `JWT_SECRET_BASE64` | Required JWT signing key | None |
| `ALERTOPS_CORS_ALLOWED_ORIGINS` | Allowed browser UI origins, comma separated | `http://localhost:5173` |
| `ALERTOPS_UI_BASE_URL` | Origin used in emailed links | `http://localhost:5173` |
| `ALERTOPS_EMAIL_VERIFICATION_TTL` | Verification link lifetime | `30m` |
| `ALERTOPS_ACKNOWLEDGEMENT_TTL` | Acknowledgement link lifetime | `72h` |
| `ALERTOPS_WEBHOOK_MAX_BODY_BYTES` | Maximum serialized webhook JSON size | `65536` |
| `ALERTOPS_WEBHOOK_RATE_LIMIT` | Requests per webhook per minute | `120` |
| `ALERTOPS_SCHEDULER_MAX_ACTIVE_TIMERS` | Maximum admitted active timers | `10000` |
| `ALERTOPS_SCHEDULER_RECOVERY_BATCH_SIZE` | Pending steps per recovery batch | `10000` |

The backend also reads `SPRING_DATASOURCE_*`, `SPRING_RABBITMQ_*`, `SPRING_DATA_REDIS_*`, and `SPRING_MAIL_*` settings. See [`application.properties`](src/main/resources/application.properties) and [`application-prod.properties`](src/main/resources/application-prod.properties) for other scheduler, consumer, timeout, and port settings. Compose forwards only the variables listed in its `app.environment`; add entries for other overrides when running in Docker.

The backend exposes Actuator health at `http://localhost:8096/actuator/health` and scheduling status at `/actuator/scheduling`. The production profile runs Flyway migrations and checks entity mappings against the schema.

Stop Compose services with `docker compose down`. `docker compose down -v` also deletes their PostgreSQL, RabbitMQ, and Redis volumes and stored data.

## Repository and release status

- `src/main/java/com/alertops/`: Spring Boot API and workers (Java 17, Spring Boot 3.5).
- `src/main/resources/db/migration/`: Flyway database migrations.
- `ui/`: React 19, TypeScript, and Vite client.
- `docker-compose.yml`: local backend and dependency stack.
- `.github/workflows/ci.yml`: Maven verification, container build, and Trivy image scan on pull requests and configured branch pushes.
- [`docs/product-launch-readiness.md`](docs/product-launch-readiness.md): release checklist and remaining end-to-end checks.
- [`docs/deployment/README.md`](docs/deployment/README.md): staged deployment learning plan.

The core manual and webhook response flows are implemented. The release checklist still calls for a deployed end-to-end run, including restart and duplicate-delivery scenarios.
