# ReplyTrail first-release deployment

The first release will use Docker Compose on a virtual machine. Initial testing
targets Oracle Cloud Always Free A1 in Mumbai, replacing the earlier EC2-first
direction. Production hosting will be confirmed after the test deployment. The
application image, PostgreSQL, RabbitMQ, and Redis will share a private Docker
network. The React UI and an HTTPS reverse proxy will provide the public entry
point. Email delivery will use an external SMTP provider.

PostgreSQL remains the source of truth for escalation state and schedules.
RabbitMQ delivers ready notification work. Redis stores temporary workflow
intents and supports shared webhook rate limiting. In-memory timers are rebuilt
from saved database state after application startup.

## Existing foundations

The source repository is [1hemant2/replytrail](https://github.com/1hemant2/replytrail).
CI derives its image name from `github.repository`, so new builds target
`ghcr.io/1hemant2/replytrail:<commit-sha>`. The repository rename does not migrate
old container images; verify a successful image publication and package access
before configuring deployment to pull the new name.

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

- Pull an immutable application image from GHCR instead of building on the server.
- Publish ARM64-compatible images for Oracle A1 and verify dependency image support.
- Serve the UI and API through the public domain with HTTPS.
- Keep database, broker, and Redis ports private.
- Supply production credentials, JWT configuration, email links, and SMTP settings.
- Configure persistent volumes, restart policies, and log rotation for the services.
- Back up PostgreSQL outside the EC2 machine and verify restoration.
- Verify health, email delivery, scheduling, and recovery after a host restart.

One machine is a single point of failure. Persistent volumes do not replace
external backups. Deployment and end-to-end verification remain the final release
step; readiness is tracked in the [product launch checklist](../product-launch-readiness.md#release-check).

## Oracle test resource target

Use the existing `alertops` compartment and public subnet in the Mumbai home
region. The small test target is `replytrail-test`, using `VM.Standard.A1.Flex`
with 1 OCPU, 2 GB RAM, a 50 GB boot volume, and Ubuntu 24.04 ARM64.
This is a test allocation, not a measured throughput guarantee. Three waiting
escalations need little processing, but the OS, Java, PostgreSQL, RabbitMQ, and
Redis all need memory. Reduce the current local Compose limits, which total
2.25 GB before OS overhead, before deploying on this VM. Verify memory use and
the requested 5–10 requests/second with a representative load test.

The old OKE cluster, worker pool, two workers, and three 47 GB boot volumes were
removed with the owner's approval. Their boot-volume data was permanently deleted.
The compartment and network were retained.

The initial 1 OCPU / 4 GB launch failed with `Out of host capacity` on 2026-10-11.
Capacity reports for the reduced 1 OCPU / 2 GB target also show no room in any
of the three Mumbai fault domains. No new
VM was created, and ReplyTrail is not deployed. Retry when home-region capacity
becomes available; choosing a paid shape or another region requires a new decision.

The dedicated SSH key is stored locally at
`/Users/hemant/.ssh/replytrail_oci_test`; never commit or publish its private key.
Use profile `REPLYTRAIL` with `--auth security_token` for local Oracle CLI access.
When the session expires, run:

```bash
oci session authenticate --region ap-mumbai-1 --profile-name REPLYTRAIL
```

The browser callback requires local port 8181. SeaweedFS is currently stopped at
the owner's request to leave that port available. Keep test data, credentials,
queues, and volumes separate from production. CI/CD automation follows successful
manual deployment and recovery checks.

## Kubernetes archive

The old `k8s/` manifests and deployment learning guides were moved into the separate
local Git repository `/Users/hemant/pers/replytrail-kubernetes` on 2026-10-11, so the
deployment learning journey can continue later. They are also recoverable from
this repository's Git history. Kubernetes is deferred beyond the first release.
