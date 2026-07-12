# Phase 1: Docker and Compose foundations

This note explains what Docker Compose creates for AlertOps, why each resource exists,
and which commands are safe to use. It is intended as a reference to revisit while
learning; memorizing every command is not required.

## The problem Docker solves

AlertOps needs a compatible Java runtime, PostgreSQL, RabbitMQ, and eventually Redis.
Installing and configuring each dependency directly on every developer machine leads
to inconsistent environments. Docker packages software and its runtime dependencies
into **images**, then runs those images as isolated **containers**.

Docker Compose describes several related containers in one `docker-compose.yml` file.
It acts as a small local orchestrator: we declare the desired services and Compose
creates and connects them.

Compose is excellent for development and learning on one machine. It is not our final
production orchestrator; Kubernetes will fill that role in later phases.

## The core mental model

```text
Dockerfile --build--> image --run--> container
                                      |-- environment variables
                                      |-- network connection
                                      |-- published ports
                                      `-- mounted volume

docker-compose.yml describes all of the above for multiple services.
```

An **image** is a read-only application template, such as `postgres:16-alpine`.
A **container** is a running instance of an image. A useful Java comparison is:

```text
Java class  -> Java object
Docker image -> Docker container
```

Deleting a container does not delete its image. A new container can be created from
the same image.

## What `docker compose up -d` does

From the repository root, run:

```powershell
docker compose up -d
```

Compose then:

1. Reads `docker-compose.yml` and substitutes values from the local `.env` file.
2. Downloads the PostgreSQL, RabbitMQ, and Redis images if they are missing.
3. Builds the AlertOps image using the `Dockerfile`.
4. Creates a project network and the declared named volumes.
5. Creates the four containers and connects them to the network.
6. Starts PostgreSQL and RabbitMQ and evaluates their health checks.
7. Starts AlertOps after those two dependencies report healthy.
8. Runs everything in the background because `-d` means **detached**.

The files have different responsibilities:

| File | Responsibility |
|---|---|
| `Dockerfile` | Instructions for building the AlertOps image |
| `docker-compose.yml` | Services, ports, variables, health checks, networks, and volumes |
| `.env` | Local configuration and real local secrets; never commit it |
| `.env.example` | Safe template documenting the required variable names |

Compose creates resources from this configuration. It does not invent the application
settings: we declare them in the files above.

## Services in the AlertOps stack

| Compose service | Container | Purpose |
|---|---|---|
| `app` | `alertops-app` | Spring Boot AlertOps application |
| `postgres` | `alertops-postgres` | Relational application data |
| `rabbitmq` | `alertops-rabbitmq` | Asynchronous message broker |
| `redis` | `alertops-redis` | Shared cache infrastructure for a later code change |

Redis is running as infrastructure, but AlertOps still uses its in-memory cache at
this stage. Running a dependency and integrating application code with it are separate
tasks.

## Networks, DNS, and `localhost`

Compose creates a private project network, normally named `alert_ops_default`, and
connects the containers to it:

```text
                     Compose private network
        +------------------------------------------------+
        | app ---- postgres                              |
        |  |  \---- rabbitmq                             |
        |  `------- redis                                |
        +------------------------------------------------+
             |
             | published application port
             v
         host/laptop
```

Docker provides DNS on this network. A container reaches another container using its
**Compose service name**:

```text
postgres:5432
rabbitmq:5672
redis:6379
```

For example, the application's JDBC URL uses `postgres`, not an IP address:

```text
jdbc:postgresql://postgres:5432/alert_ops
```

Container IP addresses may change when containers are recreated, but service names
remain stable. Hard-coding a container IP is therefore incorrect.

Inside the app container, `localhost` means **the app container itself**. It does not
mean the laptop, PostgreSQL, or another container. This is one of the most common
container networking mistakes.

## Ports: host side versus container side

A Compose port mapping has this form:

```text
HOST_PORT:CONTAINER_PORT
```

For AlertOps, `${APP_HOST_PORT:-8096}:8096` means that a request to port 8096 on the
laptop is forwarded to port 8096 inside the app container. `${NAME:-default}` means
"use the value from `.env`, or use the default when it is absent."

Published ports are needed when the laptop must access a container. Communication
between containers uses the private network, service name, and container port; it does
not need the published host port.

Examples:

| Connection | Address |
|---|---|
| Browser on laptop to AlertOps | `http://localhost:8096` |
| App container to PostgreSQL | `postgres:5432` |
| App container to RabbitMQ | `rabbitmq:5672` |
| Browser on laptop to RabbitMQ UI | `http://localhost:15672` |

## Volumes and data persistence

A container's writable filesystem is temporary. When a container is deleted and
recreated, data stored only inside it is lost. A **volume** stores data independently
of the container lifecycle.

This project currently declares:

```yaml
volumes:
  postgres_data:
  redis_data:
```

They are mounted as follows:

```text
PostgreSQL container -> postgres_data -> database files survive recreation
Redis container      -> redis_data    -> append-only data survives recreation
```

Docker manages the physical location of named volumes. We normally refer to them by
name instead of editing their files directly.

RabbitMQ does not currently have a named volume in this Compose file. Its local broker
data will therefore be lost if its container is removed. Adding and testing RabbitMQ
persistence is a later Phase 1 improvement.

### Safe and destructive cleanup

```powershell
docker compose down
```

Stops and removes this project's containers and network. Named volumes remain, so
PostgreSQL and Redis can reuse their data after the next `up`.

```powershell
docker compose down -v
```

Also deletes the project's named volumes. For this stack, that erases the local
PostgreSQL database and Redis data. Use `-v` only when intentionally resetting the
environment. It is correct to skip this command during normal work.

## Health checks and startup order

A running process is not necessarily ready to serve requests. PostgreSQL may be
starting, recovering files, or applying initialization before accepting connections.
A health check runs a small command repeatedly to classify the container:

```text
starting -> healthy
         -> unhealthy (after repeated failures)
```

The current Compose file checks PostgreSQL, RabbitMQ, and Redis. The app has
`depends_on` conditions for healthy PostgreSQL and RabbitMQ, so Compose waits before
starting it. This improves startup behavior, but it is not a substitute for resilient
application retries.

An AlertOps container health check will be added during Phase 1. Spring Actuator
already supplies the endpoints that it can test.

### AlertOps health-check exercise (completed)

The `app` service now calls the Actuator health endpoint from inside its own container:

```yaml
healthcheck:
  test: ["CMD", "curl", "--fail", "--silent", "http://localhost:8096/actuator/health"]
  interval: 10s
  timeout: 5s
  retries: 5
  start_period: 30s
```

The check uses `localhost` because it is executed inside the AlertOps container. It
uses the container port, not a host port. `curl --fail` returns a non-zero exit code
for an HTTP error, which Docker interprets as a failed check.

The configuration was applied by recreating the app container and verified with:

```powershell
docker compose config --quiet
docker compose up -d --no-deps --force-recreate app
docker compose ps app
```

The observed status was `Up ... (healthy)`. The JWT filter initially logged every
probe request, so `/actuator/health` and its subpaths are now excluded from JWT token
processing. Spring Security still explicitly controls whether those endpoints are
public; filter exclusion and endpoint authorization are different responsibilities.

## Command reference

Run these commands from the repository root:

| Command | Meaning and benefit |
|---|---|
| `docker compose up -d` | Create/start the stack in the background |
| `docker compose up -d --build` | Rebuild the app image, then create/start the stack |
| `docker compose config` | Show the resolved Compose configuration and catch configuration errors |
| `docker compose config --services` | List the service names Compose found |
| `docker compose ps` | Show containers, state, health, and published ports |
| `docker compose logs -f app` | Follow application logs; `Ctrl+C` only stops following |
| `docker compose restart app` | Restart only AlertOps, leaving dependencies and volumes intact |
| `docker compose stop` | Stop containers without removing them |
| `docker compose down` | Remove containers and project network, preserving named volumes |
| `docker compose down -v` | Remove containers, network, and volumes; deletes local persisted data |
| `docker volume ls` | List Docker-managed volumes |
| `docker network ls` | List Docker networks |
| `docker network inspect alert_ops_default` | Show attached containers and low-level network details |
| `docker compose exec app <command>` | Run a command inside the running app container |

Examples of Docker DNS checks:

```powershell
docker compose exec app getent hosts postgres
docker compose exec app getent hosts rabbitmq
docker compose exec app getent hosts redis
```

These commands prove that service names resolve inside the Compose network. The long
IDs, IP addresses, endpoint IDs, and labels printed by `inspect` are diagnostic detail.
At this stage, understand what resource the output describes; do not memorize every
field.

## How to read `docker compose ps`

Focus on these columns:

- **SERVICE/NAME**: which declared service and container the row represents.
- **STATUS**: running time and health, such as `Up ... (healthy)`.
- **PORTS**: host-to-container forwarding rules.

Useful meanings:

| Output | Meaning |
|---|---|
| `Created` | Container exists but has not started |
| `Up` | Main process is running |
| `Up (starting)` | Process is running; health check has not passed yet |
| `Up (healthy)` | Health check is passing |
| `Up (unhealthy)` | Process runs, but the health check repeatedly fails |
| `Exited (code)` | Main process stopped; inspect logs for the reason |

## Recommended learning exercises

1. Run `docker compose ps` and identify every service, health state, and host port.
2. Follow app logs, press `Ctrl+C`, and confirm the app is still running.
3. Restart only the app and confirm PostgreSQL and Redis were not restarted.
4. Inspect the project network and find the four attached containers.
5. List volumes and identify the PostgreSQL and Redis volumes.
6. Run `docker compose down` without `-v`, start again, and verify database data remains.
7. Later, deliberately stop one dependency and observe logs, health, and recovery.

For each exercise, record: what you expected, what happened, which command provided
evidence, and how you would explain the result in an interview.

## Compose concepts mapped to Kubernetes

The names differ, but these foundations carry forward:

| Docker Compose concept | Later Kubernetes concept |
|---|---|
| Service container | Pod managed by a Deployment/StatefulSet |
| Compose service name/DNS | Kubernetes Service and cluster DNS |
| Environment configuration | ConfigMap and Secret |
| Health check | Liveness, readiness, and startup probes |
| Named volume | PersistentVolumeClaim/PersistentVolume |
| Published port | Service, Ingress, and cloud load balancer |
| Compose desired state | Kubernetes manifests reconciled by controllers |

The key idea is declarative operation: describe the desired state in configuration,
then let an orchestrator create, connect, monitor, and recreate resources.
