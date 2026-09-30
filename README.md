# AlertOps

**AlertOps** is a multi-tenant alerting and escalation product being built for production use. It coordinates incident response across teams, with delayed steps, retries, and recovery after failures.

The system treats **time, retries, and ownership** as first-class concerns, using queue-driven execution instead of cron-based scheduling or in-memory timers.

---

## Why AlertOps Exists

In production systems, alerts rarely fail loudly.  
They fail quietly—emails bounce, services restart, consumers crash.

AlertOps exists to answer a single hard question:

> **How do you guarantee escalation correctness when time and failure are unavoidable?**

Rather than relying on periodic polling or best-effort schedulers, AlertOps models escalation as deterministic workflows backed by durable state and delayed message queues.

---

## Core Concepts

### Multi-Tenant Teams & Access Control

- Users can register and authenticate using JWT.
- A user can belong to multiple teams.
- Teams enforce role-based access control (admin, member, etc.).
- Team owners and admins can send invitation emails to existing or new users; admins can invite users only.
- Invite links are previewed before acceptance and require sign-in with the invited email address.

This mirrors how real SaaS systems handle collaboration and ownership.

---

### Alert Flows

An **alert flow** represents a deterministic escalation path.

Each flow consists of ordered **nodes**, where every node defines:
- the recipient (email / user)
- delay duration before execution
- retry behavior and limits

Nodes can be reordered or removed safely without corrupting the flow state.

Escalation logic is modeled as **data**, not hardcoded control flow.

---

### Delayed Execution & Escalation

AlertOps uses **RabbitMQ delayed queues** to schedule alert execution.

This enables:
- precise delays without active polling
- retry handling without duplicate sends
- clean separation between scheduling and execution

Escalation becomes event-driven rather than time-loop-driven.

---

### Reliability & Recovery

AlertOps assumes that failures will happen.

On application startup:
- in-progress alert executions are recovered
- pending nodes are revalidated
- escalation resumes without manual intervention

This prevents silent drops and duplicate executions after restarts.

---

## High-Level Architecture

- Java & Spring Boot backend
- JWT-based authentication and authorization
- RabbitMQ for delayed execution and retries
- Postgres for data storage
- Persistent database-backed state
- Event-driven processing model

The system favors explicit state transitions over implicit timing assumptions.

---

## Design Tradeoffs

- Delayed queues were chosen over cron jobs to avoid polling and race conditions.
- State is persisted aggressively to enable crash-safe recovery.
- Additional complexity is accepted in favor of correctness under failure.

AlertOps optimizes for **reliability and determinism**, not minimal code.

---

## What This Project Is (and Is Not)

### It Is
- A product for teams to configure and run incident escalation workflows
- An open-source service being prepared for real users
- A backend and UI that together support the full response workflow

### It Is Not
- A UI-centric application
- A simple notification sender

---
## Project Status

AlertOps is an actively evolving system. Core escalation workflows,
delayed execution, and recovery mechanisms are implemented.

Additional delivery channels and operational controls are explored
incrementally as part of the project’s evolution.

---

## Run With Docker

This repository includes:
- `Dockerfile` for building the Spring Boot application image
- `docker-compose.yml` for running app + PostgreSQL + RabbitMQ together

### Prerequisites

- Docker
- Docker Compose (v2)

### Start the full stack

Copy `.env.example` to `.env`, replace every placeholder, and then run:

```bash
docker compose up --build
```

### Services and ports

- App: `http://localhost:8096`
- Actuator: `http://localhost:8096/actuator`
- RabbitMQ UI: `http://localhost:15672` (credentials come from `.env`)
- PostgreSQL: `localhost:5432` (database and credentials come from `.env`)
- Redis: `localhost:6379` (password comes from `.env`)

The host ports above are defaults and can also be overridden in `.env`.

### SMTP email delivery

Escalation nodes send a styled HTML email generated from Markdown, with a Markdown plain-text fallback, using the configured SMTP server. Set these optional values in `.env` before starting the stack:

```dotenv
SPRING_MAIL_HOST=smtp.example.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=alerts@example.com
SPRING_MAIL_PASSWORD=your-smtp-password
ALERTOPS_EMAIL_FROM=alerts@example.com
```

SMTP authentication and STARTTLS default to enabled. For an implicit TLS server on port `465`, set `SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE=true` and `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=false`. Configure `ALERTOPS_EMAIL_FROM` with a sender address verified by your SMTP provider; the SMTP login may not be a valid sender. Keep provider credentials out of source control.

New registrations receive a one-time email verification link before they can use team features. The link points to `ALERTOPS_UI_BASE_URL` and expires after 30 minutes by default; change that window with `ALERTOPS_EMAIL_VERIFICATION_TTL`.

Team invitation links point to the UI origin configured with `ALERTOPS_UI_BASE_URL` (defaults to `http://localhost:5173` for local development). Set this to the deployed UI origin when sending invitations outside local development.

If SMTP is not configured or the SMTP server rejects a send, the notification is recorded as failed and follows the node's configured retry/fallback path. A node marked `SENT` means the SMTP server accepted the message; AlertOps does not claim that it reached the recipient's inbox.

### Stop services

```bash
docker compose down
```

### Stop and remove database volume

```bash
docker compose down -v
```

### Notes

- The app connects to containers using Docker service names:
  - PostgreSQL host: `postgres`
  - RabbitMQ host: `rabbitmq`
  - Redis host: `redis`
- App runtime settings are passed through environment variables in `docker-compose.yml`.
- One-time workflow intents use authenticated Redis with JSON values, a configurable
  TTL, and atomic consumption so application replicas share the same state.

---

## Cloud Deployment Course

The staged OCI deployment and GitOps learning plan is documented in
[`docs/deployment/README.md`](docs/deployment/README.md). It starts with production
readiness, progresses through Docker and local Kubernetes, then adds GitHub Actions CI,
OCI networking/OKE, Argo CD, reliability, and advanced delivery. AWS equivalents
are included for interview preparation.
