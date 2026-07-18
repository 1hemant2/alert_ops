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

## Reproducible image pinning (completed)

Broad image tags such as `postgres:16-alpine` are mutable labels: the same Git commit
could download different patch releases or operating-system packages at different
times. AlertOps now combines a readable exact tag with an immutable content digest:

```text
repository:exact-version@sha256:exact-image-content
```

The selected service versions are PostgreSQL 16.14, RabbitMQ 3.13.7, and Redis 7.4.9.
The Dockerfile also pins both stages of the application build:

- Maven 3.9.9 with the Temurin 17 JDK compiles and packages the fat JAR.
- Temurin JRE 17.0.19+10 runs the JAR in the final image.

Tags communicate the version to humans; digests ensure that developers, CI, and
deployment nodes receive identical image content. Pinning does not mean avoiding
upgrades. It makes upgrades deliberate Git changes that can be tested and rolled back.

The application image does not bake in `SPRING_PROFILES_ACTIVE`. Compose supplies
`prod` when creating this local container, and future Kubernetes manifests will select
the profile at runtime. This lets the same immutable application image move through
development, staging, and production with environment-specific configuration.

Verification included a clean test run, an image rebuild with `--pull`, all four
containers reporting healthy, Java 17.0.19 inside the app container, the `prod` profile
in Spring startup logs, an `UP` Actuator response, and the durable RabbitMQ queue still
present.

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
RabbitMQ container   -> rabbitmq_data -> broker definitions and data survive recreation
Redis container      -> redis_data    -> append-only data survives recreation
```

Docker manages the physical location of named volumes. We normally refer to them by
name instead of editing their files directly.

RabbitMQ also has a stable `hostname: rabbitmq`. RabbitMQ stores its node database
under an identity derived from its node name/hostname. A named volume alone is not
enough when every recreated container starts with a different generated hostname.

### RabbitMQ persistence exercise (completed)

The RabbitMQ service now declares both stable storage and stable node identity:

```yaml
rabbitmq:
  hostname: rabbitmq
  volumes:
    - rabbitmq_data:/var/lib/rabbitmq

volumes:
  rabbitmq_data:
```

The first test used the named volume without a stable hostname. Docker preserved the
volume, but after container recreation RabbitMQ selected a new node data directory and
the durable test queue appeared to be lost. Adding the stable hostname fixed that
node-identity problem.

The successful test was:

1. Recreate RabbitMQ with the named volume and stable hostname.
2. Declare the durable queue `phase1.persistence.test`.
3. Stop and remove only the RabbitMQ container.
4. Create RabbitMQ again from the Compose configuration.
5. Wait for its health check to pass.
6. List queues and confirm `phase1.persistence.test` still exists and is durable.

The container was new, while `alert_ops_rabbitmq_data` remained. This proves that the
queue definition lived in persistent storage rather than only in the removed
container. A durable queue is only part of message durability: publishers must also
mark important messages as persistent, and production RabbitMQ still needs backups,
replication, monitoring, and restore testing.

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
  test: ["CMD", "curl", "--fail", "--silent", "http://localhost:8096/actuator/health/liveness"]
  interval: 10s
  timeout: 5s
  retries: 5
  start_period: 30s
```

The check uses `localhost` because it is executed inside the AlertOps container. It
uses the container port, not a host port. `curl --fail` returns a non-zero exit code
for an HTTP error, which Docker interprets as a failed check. The original aggregate
health URL was changed to liveness after the dependency-failure drill proved that
remote database and broker outages should not classify the Java process as dead.

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

## Graceful shutdown exercise (completed)

Docker sends its main container process `SIGTERM` when stopping it. Because the
Dockerfile uses the exec-form `ENTRYPOINT`, Java is PID 1 and receives that signal
directly. Spring Boot can then stop accepting new requests, wait for active work, and
close application resources before exiting.

Production configuration allows Spring up to 30 seconds per shutdown phase. Compose
allows the container slightly longer so it does not send a forced `SIGKILL` while
Spring is still cleaning up:

```yaml
app:
  stop_grace_period: 40s
```

The applied container configuration reported a 40-second stop timeout. Its logs
showed graceful Tomcat shutdown beginning and completing, followed by closure of the
JPA entity manager and Hikari database pool. The container then started again and
returned to `healthy`.

`docker compose start app` and `docker compose up -d app` are not interchangeable:

- `start` starts an existing stopped container with its existing configuration. It
  does not build an image, create a missing container, or apply Compose-file changes.
- `up` reconciles the declared Compose state. It creates missing resources and may
  recreate a container when its image or configuration has changed. It also processes
  declared dependencies; `-d` leaves the services running in the background.

Use `start` after a simple manual stop when nothing changed. Use `up -d` after editing
Compose, changing an image/build, or when the resources might not exist.

## Container resource limits (completed)

Containers are isolated processes, but without resource controls any one service can
consume most of the Docker host. CPU and memory behave differently:

```text
CPU demand above limit    -> execution is throttled; work becomes slower
Memory usage above limit  -> process may be OOM-killed; container can exit with 137
```

The local learning limits were selected after measuring the idle stack:

| Service | CPU limit | Memory reservation | Memory limit |
|---|---:|---:|---:|
| AlertOps | 1 CPU | 512 MiB | 1 GiB |
| PostgreSQL | 1 CPU | 256 MiB | 512 MiB |
| RabbitMQ | 1 CPU | 256 MiB | 512 MiB |
| Redis | 0.5 CPU | 64 MiB | 256 MiB |

Compose expresses these settings as:

```yaml
app:
  cpus: 1.0
  mem_reservation: 512m
  mem_limit: 1g
```

The limit is a hard control. A Compose memory reservation is a soft target used under
host memory pressure; it is not the same scheduling guarantee as a Kubernetes request.
These values are learning defaults, not production capacity recommendations.
Production values require load tests, peak measurements, and safety headroom.

### CPU cores, millicpu, and parallel work

One CPU represents the processing capacity of one logical CPU over time. An eight-core
or eight-logical-CPU machine can execute roughly eight CPU instruction streams at one
instant, while the operating-system scheduler lets many more processes and threads
make progress by rapidly sharing those CPUs. Work waiting for a database, disk, or
network does not continuously occupy a CPU.

Compose uses decimal CPU units while Kubernetes commonly uses millicpu:

| CPU capacity | Compose | Kubernetes |
|---:|---:|---:|
| Quarter CPU | `0.25` | `250m` |
| Half CPU | `0.5` | `500m` |
| One CPU | `1.0` | `1000m` or `1` |
| Two CPUs | `2.0` | `2000m` or `2` |

Here `m` means one-thousandth of a CPU, not megabytes, milliseconds, or minutes.
Docker does not normally dedicate a particular physical core; all container threads
share an aggregate processing quota unless CPU affinity is configured separately.

### CPU quota and scheduling periods

A simplified Linux CPU limit uses a quota and a period. The verified one-CPU AlertOps
configuration reported:

```text
cpu.cfs_quota_us  = 100000
cpu.cfs_period_us = 100000
```

This permits up to 100,000 microseconds of aggregate CPU time per 100,000-microsecond
quota period: one CPU. A `0.5` CPU limit is conceptually about 50,000 microseconds per
100,000-microsecond period.

The quota applies to the whole container, not independently to each Java task. The
scheduler can distribute it across many shorter executions and multiple threads. Two
threads running simultaneously for 25 ms can together consume 50 ms of CPU time. If
the container exhausts its quota while work remains runnable, Linux throttles it until
more quota becomes available. It is not a rule that every task runs exactly 50 ms and
then waits.

### Reading CPU in Grafana

A CPU panel may display cores, percentage of one core, percentage of the Kubernetes
request, percentage of the limit, or percentage of the node. Always identify the
denominator. For example, usage of `500m` is 50% of a one-CPU limit but 200% of a
`250m` request.

High CPU usage is not automatically a defect. Correlate it with:

- request or message rate;
- response latency and errors;
- CPU throttled periods/time;
- JVM garbage-collection time and heap pressure;
- deployments and configuration changes;
- whether one replica or every replica is affected.

Rising traffic with rising CPU and stable latency can be healthy. Unchanged traffic
followed by sustained CPU at the limit, throttling, and increased latency can indicate
an expensive code path, retry storm, excessive logging, garbage collection, or a
regression. Investigate before merely increasing the limit.

### Resource-limit verification

Before limits, every container saw approximately 3.6 GiB of Docker Desktop memory and
the JVM estimated a 924 MiB maximum heap. After recreation, Docker reported the exact
configured byte and CPU quotas. AlertOps saw a 1 GiB cgroup memory limit, and
container-aware Java 17 recalculated its estimated maximum heap to 247.5 MiB.

All four health checks passed, Actuator returned `UP`, and RabbitMQ's durable queues
remained after recreation. Current idle usage fits the limits, but that does not prove
production capacity; load testing remains necessary.

Depending on the host, cgroup files differ. Cgroup v2 commonly exposes `memory.max`
and `cpu.max`; this Docker Desktop environment uses cgroup v1 files such as
`memory.limit_in_bytes`, `cpu.cfs_quota_us`, and `cpu.cfs_period_us`. Docker inspection
and `docker stats` provide a more portable high-level view.

## Dependency failure and recovery drill (completed)

`depends_on` controls Compose startup ordering; it is not continuous supervision.
Stopping PostgreSQL or RabbitMQ after startup left the AlertOps Java process running.
This demonstrated why process state, liveness, readiness, and dependency health are
different signals.

### PostgreSQL outage

With PostgreSQL stopped, the aggregate `/actuator/health` endpoint became unavailable
or `DOWN`, while `/actuator/health/liveness` remained `UP`. Hikari attempted to obtain
a connection, timed out after 30 seconds, and logged the database failure. Starting
the existing PostgreSQL container restored the connection and AlertOps recovered
without container recreation.

The test exposed an operational problem: the Docker health check called the aggregate
endpoint every ten seconds. During the outage, overlapping checks repeatedly triggered
slow database health queries. The Compose app probe now calls the lightweight
`/actuator/health/liveness` endpoint instead.

### RabbitMQ outage

RabbitMQ behaved differently from JDBC. Its connection closed, the Spring AMQP
listener reported connection refusal, and the listener container repeatedly attempted
consumer recovery. After RabbitMQ restarted and became healthy, AlertOps created a new
AMQP connection automatically. The durable test and application queues remained.

The aggregate health request timed out during the broker outage while liveness stayed
`UP`. Docker does not automatically restart a container merely because it is marked
unhealthy; `restart: unless-stopped` applies when its main process exits. A dependency
outage should not cause a liveness failure and restart loop.

### Redis boundary

Redis was not failure-tested against AlertOps because the application is not connected
to it yet. Stopping Redis currently cannot affect AlertOps health or behaviour. The
Redis failure drill belongs with the later cache integration, when it can validate
timeouts, cache fallback policy, and reconnection.

Readiness policy will be made explicit in the Kubernetes phase. Whether an external
dependency should make a pod unready is a service-design decision: it can fail traffic
quickly, but it can also remove every replica during a shared dependency outage.
Liveness should normally answer whether this process is stuck and needs restarting,
not whether a remote dependency is available.

## Full-stack persistence drill (completed)

Individual service restarts do not fully prove Compose lifecycle persistence. The
complete stack was removed with `docker compose down` (without `-v`) and recreated
with `docker compose up -d`. `down` removed all four containers and the project
network, while these named volumes remained:

```text
alert_ops_postgres_data
alert_ops_rabbitmq_data
alert_ops_redis_data
```

Before teardown, the drill recorded independent storage markers:

- PostgreSQL cluster system identifier `7661185378394243107`;
- durable RabbitMQ queue `phase1.persistence.test`;
- Redis key `phase1:persistence` with value `survived`.

After recreation, PostgreSQL returned the same system identifier, RabbitMQ listed the
same durable queues, and Redis returned the marker value. Flyway reported that schema
`public` was already up to date instead of initializing a new database. AlertOps and
all infrastructure health checks returned healthy.

The PostgreSQL system identifier is a read-only fingerprint generated when a database
cluster is initialized. Matching values before and after recreation provide evidence
that PostgreSQL reused the same data directory. On PowerShell, avoiding nested
`sh -c` quoting made the check more reliable:

```powershell
$pgUser = (docker compose exec -T postgres printenv POSTGRES_USER).Trim()
$pgDb = (docker compose exec -T postgres printenv POSTGRES_DB).Trim()
docker compose exec -T postgres psql -U $pgUser -d $pgDb -tA -c "SELECT system_identifier FROM pg_control_system();"
```

Named-volume persistence is not a backup. A volume can still be deleted with
`docker compose down -v`, corrupted, lost with its Docker host, or damaged by an
application error. Production requires independent backups stored outside the host,
retention policy, encryption, and tested restoration procedures.

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
| `docker stats --no-stream` | Snapshot container CPU, memory, network, process, and I/O usage |
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
