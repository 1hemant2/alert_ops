# Phase 0: production readiness

Phase 0 makes failures visible before deployment automation is introduced. Kubernetes
cannot repair an application that starts with invalid configuration, silently changes
its database schema, or shares a signing key committed to Git.

## What changed

### Secrets are injected at runtime

`JwtUtil` now receives `security.jwt.secret-base64`, which is populated from the
required `JWT_SECRET_BASE64` environment variable. There is no signing-key fallback:
a missing secret stops startup immediately.

Generate a new 256-bit secret and store it only in your local `.env` or secret manager.

PowerShell:

```powershell
$bytes = New-Object byte[] 32
[Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
[Convert]::ToBase64String($bytes)
```

Linux:

```bash
openssl rand -base64 32
```

Add the result locally as:

```dotenv
JWT_SECRET_BASE64=generated-value
```

Do not reuse the old `JWT_SECRET`. Because `.env` was tracked previously, treat all
values it contained as compromised. Removing the file from the latest commit does not
erase it from Git history; rotate the credentials. History rewriting is a separate,
coordinated operation and is not required for local learning.

### Profiles have explicit responsibilities

- `dev`: local PostgreSQL/RabbitMQ defaults and useful development logging.
- `prod`: environment-only connection settings, restrained logging, graceful shutdown,
  health probes, Flyway, and Hibernate schema validation.

No profile is activated from inside the application artifact. The runtime selects it
with `SPRING_PROFILES_ACTIVE`. The container defaults to `prod`; Compose supplies its
dependencies and secret.

### Flyway owns database evolution

`V1__create_initial_schema.sql` describes the first schema. On a fresh database,
Flyway applies it before Hibernate starts. Hibernate then validates entity mappings.

For an existing non-empty development database, `baseline-on-migrate` records a
baseline rather than trying to recreate existing tables. Back up meaningful data before
the first migration-enabled startup. Future schema changes must be new files such as:

```text
V2__add_escalation_index.sql
V3__add_notification_channel.sql
```

Never edit an applied migration: Flyway checksums make that drift visible.

### Health endpoints are narrow

Only `/actuator/health/**` is anonymously accessible. The application exposes health
and info, not every Actuator endpoint. Kubernetes will later use:

```text
/actuator/health/liveness
/actuator/health/readiness
```

Liveness answers “should this process be restarted?” Readiness answers “should this
instance receive traffic?” A database outage should usually make the app unready, not
cause an endless restart loop.

### The build has an actual test

The JWT test verifies claim round-tripping and rejects an undersized HMAC key. This is
only the first quality gate; service and database integration tests remain future work.

## Run Phase 0 locally

1. Copy `.env.example` to `.env` if necessary.
2. Generate and set a new `JWT_SECRET_BASE64`.
3. Use `SPRING_PROFILES_ACTIVE=dev` when running directly from the IDE.
4. Run the quality gate:

```powershell
.\mvnw.cmd clean test
```

5. Complete Phase 0 by running Compose and verifying the database migration, Redis
   persistence, and health endpoints against real containers.

## Why local services still use credentials

Docker containers share a private network, but they remain independent processes.
Authentication protects against accidental access from another container and keeps
local configuration structurally similar to production. Compose reads the values from
`.env` and passes them at runtime; the image does not contain them.

Environment variables are acceptable for this local learning stack, but they are not a
complete production secret solution: container metadata and privileged host users can
inspect them. Kubernetes will later receive secrets from a dedicated secret-management
workflow such as OCI Vault rather than from a committed `.env` file.

## Redis infrastructure boundary

Phase 0 provisions an authenticated Redis 7 container, an append-only persistence file,
a named data volume, and a health check. This proves the infrastructure contract before
horizontal application scaling is introduced.

At the Phase 0 boundary, AlertOps did not connect to Redis and the `intent` cache
remained process-local. Phase 1 has since added the Redis client, JSON serialization,
expiry policy, integration tests, and atomic consume operation. This history preserves
the distinction: installing Redis alone does not make application state shared; the
application must explicitly use it.

## Interview notes

- Configuration is externalized so one immutable image can move between environments.
- Secrets are injected at runtime and should originate from OCI Vault (AWS Secrets
  Manager equivalent), not ConfigMaps or Git.
- Flyway makes database changes ordered, reviewable, repeatable, and checksum-verified.
- Hibernate `validate` detects mapping drift without mutating production.
- Readiness removes unhealthy instances from service; liveness is a restart signal.
- A CI job must report how many tests ran; “build success” with zero tests is misleading.

## Definition of done

- [x] JWT signing key removed from source code.
- [x] `.env` removed from Git tracking and covered by `.gitignore`.
- [x] Production configuration fails fast when required values are missing.
- [x] Flyway owns schema creation; production Hibernate mode is `validate`.
- [x] Public Actuator access is limited to health.
- [x] Liveness and readiness endpoints are enabled.
- [x] At least one meaningful unit-test class runs in Maven.
- [x] Authenticated, persistent Redis is declared in the local infrastructure stack.
- [x] Local credentials have been rotated.
- [x] PostgreSQL migration, Redis persistence, and probes have been exercised locally.
