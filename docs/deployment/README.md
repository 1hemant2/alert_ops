# ReplyTrail first-release deployment

The first release will run on one AWS EC2 machine using Docker Compose. The
application image, PostgreSQL, RabbitMQ, and Redis will share a private Docker
network. The React UI and an HTTPS reverse proxy will provide the public entry
point. Email delivery will use an external SMTP provider.

PostgreSQL remains the source of truth for escalation state and schedules.
RabbitMQ delivers ready notification work. Redis stores temporary workflow
intents and supports shared webhook rate limiting. In-memory timers are rebuilt
from saved database state after application startup.

## Existing foundations

- [Dockerfile](../../Dockerfile) builds the backend image.
- [Docker Compose](../../docker-compose.yml) runs the local backend and dependencies.
- [UI Dockerfile](../../ui/Dockerfile) builds and serves the React application.
- [GitHub Actions](../../.github/workflows/ci.yml) verifies, scans, and publishes
  backend images on its configured events.
- [Production properties](../../src/main/resources/application-prod.properties)
  read runtime configuration from environment variables.

## Remaining deployment work

The existing Compose file is a local stack. The production configuration has not
been implemented or deployed yet. Before release:

- Pull an immutable application image from GHCR instead of building on EC2.
- Serve the UI and API through the public domain with HTTPS.
- Keep database, broker, and Redis ports private.
- Supply production credentials, JWT configuration, email links, and SMTP settings.
- Configure persistent volumes, restart policies, and log rotation for the services.
- Back up PostgreSQL outside the EC2 machine and verify restoration.
- Verify health, email delivery, scheduling, and recovery after a host restart.

One machine is a single point of failure. Persistent volumes do not replace
external backups. Deployment and end-to-end verification remain the final release
step; readiness is tracked in the [product launch checklist](../product-launch-readiness.md#release-check).

## Kubernetes archive

The old `k8s/` manifests and deployment learning guides were moved into the separate
local Git repository `/Users/hemant/pers/replytrail-kubernetes` on 2026-10-11, so the
deployment learning journey can continue later. They are also recoverable from
this repository's Git history. Kubernetes is deferred beyond the first release.
