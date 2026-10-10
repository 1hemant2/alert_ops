# ReplyTrail

**Keep every response on track.**

ReplyTrail is a self-hosted web application for team response coordination. Turn an alert or request into a task, choose an ordered path of people to notify, and track the response from the first email to acknowledgement or resolution. If nobody responds within the configured wait, the run advances to the next person.

It helps teams answer three questions: **Who should respond? When should the next person be notified? What happened to this request?** A team member can start a run in the UI, schedule it for later, or let an external system trigger it through a webhook. This repository contains the React UI, Spring Boot API and workers, database migrations, and deployment configuration.

## Use cases

Use ReplyTrail when you want someone to follow up on a problem, a request, or a
reminder. It sends an email and, if nobody responds in time, can email the next
person or remind the same person again.

- Website or app problems: Email the person who looks after the service. If they do not respond, notify their team lead.
- Helping new customers: Ask someone to help a customer finish setting up their account. Notify another team member if no one responds.
- IT help: Follow up on requests such as a login problem or access to a work tool.
- Failed jobs: Ask the person responsible to check a failed data import or other background job.
- Unanswered support requests: Remind the support team about a customer who is still waiting for help.
- Failed backups: Ask someone to check why a backup failed or could not be restored.
- Security problems: Notify the person who should review a reported security issue.
- Payment or invoice problems: Ask the billing team to follow up on a failed payment or an incorrect invoice.
- Upcoming renewals: Schedule a reminder before a certificate expires or a contract needs renewing.
- Late deliveries: Ask the purchasing team to follow up when a supplier misses a delivery date.
- Office repairs: Follow up on a broken printer, an office repair, or another maintenance request.
- Future follow-ups: Schedule a reminder to check a task at a later date.
- Personal reminders: Send yourself a reminder about the same task roughly once a day for ten days.

Add a task describing what needs attention, choose who should receive the
emails and how long to wait, then start it now or choose a future start time.
Everyone receiving emails must be a member of your ReplyTrail team.

You can also connect another tool through a webhook to send tasks to ReplyTrail.
That tool detects the problem and sends the details; ReplyTrail emails the
people you choose. They carry out the work in the relevant app or system.

### Example: daily reminders for ten days

Create a flow with ten steps and put your own team-member email on every step.
Set each wait to **1,440 minutes (24 hours)** and leave **resolution timeout**
disabled. Start it with the task you want to be reminded about.

The first reminder is scheduled for 24 hours after you start. Each later
reminder is scheduled for 24 hours after the previous email is sent. If you
click **Acknowledge** and confirm, the remaining reminders stop.

If you do not acknowledge, the ten-step sequence continues and then ends;
it does not repeat automatically. Delivery delays or downtime can change when
emails arrive, so they may not reach you at the same clock time every day.

## Core concepts

| Concept | Meaning |
| --- | --- |
| Team | A workspace containing its members, tasks, paths, webhooks, and runs. |
| Task | The work that needs a response: title, description, source, and optional context. |
| Response path | A reusable, ordered sequence of team-member recipients and configured timing. Called a flow in the API and code. |
| Escalation run | One execution of a task through a path, with saved progress and history. |
| Acknowledgement | A recipient accepts responsibility; it completes the run or starts a resolution window, depending on the path settings. |
| Resolution | Completion of the issue after acknowledgement when resolution timeout is enabled. |

## Features

- **Accounts and teams:** Registration requires email verification before login. Users can belong to multiple teams. Owners and admins can invite members; admins can invite users only. Acceptance requires the invited, verified email address.
- **Tasks:** Name, description, and source, plus optional priority, category, and HTTP(S) reference URL. Priority is a user-defined label. Manually created tasks default to source `Manual`.
- **Response paths:** Ordered steps with a team-member recipient and wait time, plus optional resolution timeouts. Add, edit, delete, and drag steps to reorder them. Started runs retain their saved task context, step details, and timing when a task or path changes.
- **Immediate or scheduled runs:** Start a run from a task and path immediately, or schedule a one-time start using a date, time, and IANA timezone. Reschedule, cancel, or start a scheduled run early before it begins. The first step keeps its configured wait.
- **Webhooks:** Owners and admins can create a webhook with a default path, rotate its secret, and enable or disable it. An incoming event creates a task and starts a run. The complete JSON event is stored with links to both records.
- **Acknowledgement and resolution:** Recipient-scoped email links open a preview before confirmation. Acknowledgement either completes the run or pauses later steps for a configured resolution window. Unresolved timeouts resume escalation; resolution stops remaining steps.
- **Manual escalation:** Escalate now makes the next eligible response step due immediately from the signed-in UI or an eligible email action link.
- **Activity history:** Run details show saved step progress and chronological milestones, including notification attempts, acknowledgement, resolution, and manual actions. Repeated delivery retries are grouped for readability.
- **Recovery:** PostgreSQL-backed schedules and pending publications recover after restart. Scheduled-start failures create durable email-notification obligations for the scheduler owner and team administrators.

## Current scope

- Notifications use **SMTP email**. SMS, phone calls, push notifications, and native Slack/Teams delivery are not implemented.
- Scheduling supports **one-time starts**, not recurring or cron schedules. A finite reminder sequence can use multiple steps assigned to the same person, as in the ten-day example above.
- A response path names individual team-member recipients; rotating on-call calendars are not implemented.
- Opening an email link does not accept responsibility automatically; the recipient must confirm the action.
- Local checks do not establish production readiness. Database/broker integration checks and deployed response journeys are tracked in the launch checklist.

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

## Technology stack

| Component | Technology and responsibility |
| --- | --- |
| Web UI | React 19, TypeScript, Vite, React Router, TanStack Query, and Tailwind CSS |
| API and workers | Java 17 target, Spring Boot 4.0.8, Spring Security, and JWT authentication |
| Durable storage | PostgreSQL with Spring Data JPA and Flyway schema migrations |
| Ready-work delivery | RabbitMQ; database-backed timers determine when work becomes ready |
| Short-lived shared state | Redis for workflow intents and webhook rate limiting |
| Email | Spring Mail with an SMTP provider |
| Packaging and deployment | Docker Compose for local services; Kubernetes manifests and GitHub Actions for backend build, verification, and image scanning |

The UI can be deployed as static files separately from the backend. Existing `com.alertops` packages, `ALERTOPS_*` settings, container/resource names, and the `X-AlertOps-Webhook-Secret` header are retained compatibility identifiers from the earlier product name. Use these exact identifiers in configuration and integrations.

## Run locally

### Prerequisites

- Docker and Docker Compose v2 for PostgreSQL, RabbitMQ, and Redis
- Java 17 or later to run the backend from source
- Node.js 22 and npm for the UI
- An SMTP account and verified sender for registration, invitation, and escalation emails

Copy the example configuration, replace the password and JWT placeholders, configure SMTP for the account-verification flow, and keep `.env` out of source control. Generate a JWT key with `openssl rand -base64 32`.

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
5. On **Escalations**, choose the task and path, then select **Start immediately** or **Schedule for later**. Creation starts or schedules the run according to that choice.
6. Open the run details to follow saved step progress and activity history. In the email, confirm acknowledgement; if the path requires resolution, resolve before the displayed deadline to stop later notifications.

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

The backend exposes Actuator health at `http://localhost:8096/actuator/health` and scheduling status at `/actuator/scheduling`. Health is public; scheduling status requires authentication. The production profile runs Flyway migrations and checks entity mappings against the schema.

Stop Compose services with `docker compose down`. `docker compose down -v` also deletes their PostgreSQL, RabbitMQ, and Redis volumes and stored data.

## Development checks

From the repository root:

```bash
./mvnw --batch-mode --no-transfer-progress verify
npm --prefix ui ci
npm --prefix ui run build
npm --prefix ui run lint
```

The backend command runs tests and packages the application; the UI build checks TypeScript and produces static assets in `ui/dist/`. Integration suites for PostgreSQL, RabbitMQ, and Redis are opt-in and need running services. A passing default build can include skipped integration tests; see the suite setup in [`src/test/java/com/alertops/`](src/test/java/com/alertops/) and the [launch checklist](docs/product-launch-readiness.md) before interpreting it as a release result.

## Repository and release status

- [`src/main/java/com/alertops/`](src/main/java/com/alertops/): Spring Boot API and workers.
- [`src/test/java/com/alertops/`](src/test/java/com/alertops/): backend unit, API, and opt-in integration tests.
- `src/main/resources/db/migration/`: Flyway database migrations.
- `ui/`: React 19, TypeScript, and Vite client.
- `docker-compose.yml`: local backend and dependency stack.
- `.github/workflows/ci.yml`: Maven verification, container build, and Trivy image scan on pull requests and configured branch pushes.
- [`docs/product-launch-readiness.md`](docs/product-launch-readiness.md): release checklist and remaining end-to-end checks.
- [`docs/deployment/README.md`](docs/deployment/README.md): staged deployment learning plan.
- [`CHANGELOG.md`](CHANGELOG.md): completed repository changes and verification notes.

Manual and webhook response flows, one-time scheduling, resolution timeouts, manual actions, and activity history are implemented locally. The release checklist still calls for deployed end-to-end runs, including restart and duplicate-delivery scenarios. Domain ownership, sender verification, and rebrand rollout are tracked in the [product rebranding plan](docs/product-rebranding-plan.md).
