# Open-source test-hosting request

Status: Draft only. No application has been submitted and no sponsorship is
confirmed. Review the details below before sending them to a provider.

## Request draft

Hello,

I maintain ReplyTrail, an MIT-licensed, self-hosted application that helps teams
coordinate responses to tasks and alerts. It emails an assigned person, waits
for acknowledgement or resolution, and advances through an ordered response
path when the response window expires.

Repository: https://github.com/1hemant2/replytrail

I am requesting one always-running Linux VM for development, integration
testing, and a small demonstration environment. The project owner currently
funds no paid hosting and plans to keep the application open source without
charging users. Production hosting is outside this initial request.

The VM would run Docker Compose with a Java 17 Spring Boot backend, PostgreSQL,
RabbitMQ, Redis, and the React UI served as static files. PostgreSQL stores
durable workflow state. In-memory timers wake the backend for due work, and
RabbitMQ delivers ready notifications. The backend must remain running even
when no HTTP requests arrive, so scale-to-zero hosting is not suitable.

The initial resource request is 1 vCPU, 2 GB RAM, and approximately 40–50 GB of
persistent storage. Either x86-64 or ARM64 Linux is acceptable; ARM64 requires
compatible image builds before deployment. Two GB is an experimental target:
we still need to reduce local container limits and measure memory use.
We intend to test three simultaneous escalation runs and roughly 5–10 HTTP
requests per second, but have not benchmarked that workload on these resources.

We need SSH administration, HTTPS access for the UI/API, and permission to reach
an external SMTP provider using authenticated TLS. Database, broker, and Redis
ports will not be publicly exposed. Testing will use synthetic data and
controlled recipients, not an unrestricted public email service.

The code is actively maintained and includes tests and CI, but deployed
end-to-end verification is still pending. As checked on 2026-10-11, the public
repository has zero stars and zero forks. We do not claim an established user
community or measured adoption. Please let us know whether an early-stage
project with these needs qualifies and what attribution or usage conditions
would apply.

Thank you for considering the request.

## Before submission

- Add the maintainer's preferred contact details and the requested duration.
- Confirm repository statistics and include genuine usage evidence if available.
- Confirm whether a demonstration environment and external SMTP are allowed.
- Confirm storage, backups, IP addresses, outbound traffic, credit expiry,
  and any required payment method or automatic billing. Budget alerts are not
  a hard spending cap; do not provision paid resources without owner approval.
- Add sponsor attribution only after approval and agreement on its wording.

## Provider-specific guidance

- [Vultr](https://discover.vultr.com/open-source-credits) considers public,
  OSI-licensed, recently maintained projects with adoption evidence. Its
  advertised Starter tier is $500/year at 100+ stars; smaller projects may
  qualify through other evidence. Approval and renewal are not guaranteed.
- [OSU Open Source Lab](https://osuosl.org/services/hosting/policy/) evaluates
  community impact and active development. Explain why this workload cannot
  use static hosting; do not imply that the project already has a large community.
  For [ARM64 testing](https://osuosl.org/services/aarch64/request-hosting/), explain
  the actual architecture-validation work rather than claiming a need for ARM64
  solely to obtain hosting.
- [AWS](https://aws.amazon.com/blogs/opensource/aws-cloud-credits-for-open-source-projects-affirming-our-commitment/)
  evaluates active maintenance, community engagement, and relevance to its
  ecosystem. Credits, expiration, and eligible services require confirmation.

## Suggested GitHub repository description

ReplyTrail is an MIT-licensed, self-hosted application for team alerts and email
escalation, with PostgreSQL-backed in-memory scheduling and RabbitMQ delivery.

The GitHub description is separate from repository files. The owner can paste
this text into the repository's About settings; this draft does not update it.

Deployment status and remaining setup are maintained in the
[deployment guide](README.md) and [launch checklist](../product-launch-readiness.md).
