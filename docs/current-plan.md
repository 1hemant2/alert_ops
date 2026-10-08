# Current task plan

## Task: Prepare local backend and UI environment configuration

Started: 2026-10-08
Status: In progress

### Goal and scope

Make the repository runnable locally as one application: reconcile the existing
server `.env` with Docker Compose's service variables, add the UI Vite env file,
check required local tooling, and verify startup prerequisites. Preserve all
existing user-provided secret values and keep ignored local env files untracked.

### Decision-compliance note

- The [launch checklist](product-launch-readiness.md) remains the release source
  of truth; this is local development configuration, not deployment readiness.
- Docker Compose is the canonical local dependency stack for PostgreSQL,
  RabbitMQ, and Redis; the Vite proxy remains the canonical local API route.
- Never replace existing JWT, SMTP, database, or broker credentials; add only
  missing local defaults and record any unavailable external prerequisite.

### Acceptance criteria

- [ ] Root `.env` contains the Compose service variables needed for local startup.
- [ ] `ui/.env` points the UI at the local Vite proxy without exposing secrets.
- [ ] Existing secrets remain unchanged and env files stay ignored by Git.
- [ ] Required tooling and configuration validation pass; full startup is tested
  when a local container runtime is available.

### Steps

- [x] Inspect the env examples, Compose file, Spring profiles, UI proxy, and
  installed local toolchain.
- [ ] Add missing local Compose defaults and the UI env file without overwriting
  user-provided credentials.
- [ ] Validate the resolved Compose configuration and run available checks.
- [ ] Update the changelog and complete this plan with verification results or a
  clearly documented runtime prerequisite.

### Intended verification and limitations

Validate the env key set, `docker compose config`, UI build, backend
compilation, and full Compose health once Docker is available. Do not print or
commit secret values. SMTP delivery and external email-provider behavior remain
outside local configuration verification.
