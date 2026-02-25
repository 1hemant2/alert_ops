# AlertOps

**AlertOps** is a multi-tenant backend alerting and escalation platform designed to model real-world incident flows—where alerts are delayed, retried, escalated, and recovered reliably under failure.

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
- Team invitations are supported even if the invited user does not yet exist.
- User intent is preserved and resolved when the invited user registers.

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
- A backend-focused system design project
- A practical exploration of alerting and escalation workflows
- A demonstration of queue-based, time-aware execution

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

```bash
docker compose up --build
```

### Services and ports

- App: `http://localhost:8096`
- Actuator: `http://localhost:8096/actuator`
- RabbitMQ UI: `http://localhost:15672` (username: `guest`, password: `guest`)
- PostgreSQL: `localhost:5432` (db: `alert_ops`, user: `root`, password: `root`)

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
- App runtime settings are passed through environment variables in `docker-compose.yml`.
