# Product rebranding plan

Created: 2026-10-08
Status: In progress — ReplyTrail selected for the local public rebrand
Owner: Project owner
Release priorities: [Product launch checklist](product-launch-readiness.md)

## Goal and product positioning

Replace the AlertOps public identity with one consistent, easy-to-pronounce brand.
Describe the existing product as team response coordination: tasks and incoming
webhook events follow ordered email response paths with clear ownership, timing,
acknowledgement, resolution, and history. This includes operational alerts and
onboarding requests without promising unimplemented features.

This initiative covers the product identity, UI, emails, maintained docs, and
release configuration. New notification channels, workflow features, a full UI
redesign, and internal infrastructure migrations are separate work.

## Name and identity decisions

Record these decisions here before implementing name-dependent changes:

| Decision | Current state | Completion condition |
| --- | --- | --- |
| Product name and capitalization | **ReplyTrail** | Use this exact spelling in public UI, email, metadata, and current guidance |
| Primary domain and budget | Pending | Confirm registrar availability, first-year and renewal price, and control of the chosen domain |
| Repository slug and technical prefix | Pending; retain existing identifiers initially | Decide whether a later repository/artifact rename is needed |
| Positioning and tagline | Team response coordination; “Keep every response on track.” | Keep copy accurate to shipped behavior |
| Wordmark and favicon | Pending | Approve readable desktop/mobile/email variants using the existing visual style |
| Sender identity and public origins | Pending | Record verified email sender and UI/API URLs for each deployed environment |

The name screen on 2026-10-08 found little indexed use of ReplyTrail, but
[`replytrail.com`](https://rdap.verisign.com/com/v1/domain/replytrail.com) and
[`replytrail.app`](https://pubapi.registry.google/rdap/domain/replytrail.app) were
already registered. Website content could not be verified. Do not treat the name
as unused or those domains as available. Recheck any chosen name before purchase
or public rollout, including relevant product, repository, package, and trademark
conflicts. ReplyTrail is approved for this local implementation. Domain ownership and
trademark clearance remain open before any public launch.

## Decision compliance and canonical sources

- Preserve the agreed lifecycle, manual actions, exact-step email tokens, shared
  `FlowExecutionState.dueAt` wait boundary, and resolution window documented in
  [the lifecycle plan](resolution-timeout-implementation-plan.md). PostgreSQL
  remains authoritative; branding does not change saved statuses or timestamps.
- Preserve the task/run snapshot, webhook authentication and idempotency behavior
  in [the webhook plan](webhook-escalation-implementation-plan.md) and the launch
  checklist. Keep task, path, escalation, and response-step domain terminology.
- Preserve the separate frontend/backend deployment boundary in
  [the UI plan](ui-implementation-plan.md#stack-and-deployment-boundary).
- Existing integration names remain canonical until a separately planned migration.
  Do not introduce parallel headers, queues, databases, caches, or storage keys.
- Preserve historical changelog entries, archived plans, and applied Flyway files.
  Update current guidance and code links rather than rewriting historical evidence.

## Repository inventory and proposed treatment

| Surface | Current source | Treatment |
| --- | --- | --- |
| Public identity and metadata | [Landing page](../ui/src/pages/PublicPage.tsx), [HTML entry](../ui/index.html), [favicon](../ui/public/favicon.svg) | Update wordmark, title, description, footer, and favicon; keep claims accurate |
| Signed-in and email-action pages | [App shell](../ui/src/components/AppShell.tsx), [auth pages](../ui/src/features/auth/AuthPages.tsx), [join page](../ui/src/features/teams/JoinTeamPage.tsx), [acknowledgement page](../ui/src/features/escalations/AcknowledgeEscalationPage.tsx), [Escalate now page](../ui/src/features/escalations/EscalateNowPage.tsx), [run detail](../ui/src/features/escalations/EscalationDetailPage.tsx), [404 page](../ui/src/App.tsx) | Update repeated branding, fallback labels, captions, system actor text, and safe errors |
| UI authentication coordination | [Session provider](../ui/src/app/Session.tsx), [API client](../ui/src/api/client.ts), auth/join pages above | Retain `alertops.token`, `alertops.team`, pending-invite/verification keys, and `alertops:unauthorized` |
| Outgoing email | [Notification](../src/main/java/com/alertops/messaging/Notification.java), [verification mailer](../src/main/java/com/alertops/messaging/EmailVerificationMailer.java), [invitation mailer](../src/main/java/com/alertops/messaging/TeamInvitationMailer.java) | Update subjects, HTML/plain text, headers/footers, and sender display name; preserve action behavior |
| External webhook contract | [Event controller](../src/main/java/com/alertops/webhook/controller/WebhookEventController.java), [CORS](../src/main/java/com/alertops/security/SecurityConfig.java), [API examples](../README.md) | Keep `X-AlertOps-Webhook-Secret`, routes, payloads, and secret handling compatible |
| Configuration and durable runtime identity | [Properties](../src/main/resources/application.properties), [environment example](../.env.example), [RabbitMQ configuration](../src/main/java/com/alertops/messaging/RabbitMqConfig.java), [intent cache](../src/main/java/com/alertops/caching/IntentCache.java), [webhook service](../src/main/java/com/alertops/webhook/service/WebhookService.java) | Retain `ALERTOPS_*`, `alertops.*`, queue/exchange/routing names, and Redis prefixes |
| Build and deployment identity | [Maven](../pom.xml), [Dockerfile](../Dockerfile), [Compose](../docker-compose.yml), [Kubernetes base](../k8s/base/), [CI](../.github/workflows/ci.yml) | Update human-readable descriptions; retain coordinates, `com.alertops` packages, resource names/selectors, volumes, and image paths initially |
| Current documentation | [README](../README.md), [UI README](../ui/README.md), [launch checklist](product-launch-readiness.md), [deployment guide](deployment/README.md), [agent instructions](../AGENTS.md) | Update current product references and explain retained compatibility identifiers |

Several pages repeat the split wordmark markup. The initial implementation updates
those visible surfaces directly to `REPLY`/`TRAIL` and avoids introducing a new
abstraction for a fixed display name. Backend templates use their existing literal
copy, while technical configuration remains unchanged. HTML metadata and maintained
docs match the agreed spelling where the public identity is shown.

## Ordered implementation tasks

### 1. Settle the identity

- [x] Select the public name, exact capitalization, and working tagline: ReplyTrail;
  “Keep every response on track.”
- [ ] Select the domain, sender, and public URLs before deployment.
- [ ] Confirm domain ownership and record the compatibility identifiers to retain.
- [ ] Review a wordmark/favicon preview and representative page/email copy.
- **Done when:** the decision table is complete enough to implement without guessing.

### 2. Rebrand the frontend

- [x] Update the repeated wordmark and all visible surfaces listed above, including
  the HTML title and description; the existing favicon has no old-name text.
- [x] Update landing-page footer/tagline copy to the team-response positioning while
  preserving navigation, form labels, domain terminology, accessibility, and style.
- [x] Keep session storage and internal event identifiers unchanged.
- **Done when:** all relevant pages use the selected identity, including public action
  pages and loading/error states, with no clipped wordmark on mobile or desktop.

### 3. Rebrand notifications and maintained guidance

- [x] Update verification, invitation, escalation, and scheduled-start-failure emails
  in both HTML and available plain-text bodies; update their focused coverage.
- [x] Preserve recipient scopes, expiry, deliberate POST confirmation, and SMTP
  acceptance semantics. Sender address changes require provider verification first.
- [x] Update maintained docs, project descriptions, and CI display labels. Document
  old configuration/header names explicitly so examples remain executable.
- **Done when:** users see one brand across the UI and emails; existing integrations,
  configured deployments, and current documentation examples still work.

### 4. Verify and roll out

- [x] Run focused rendering/notification and affected API tests, full builds, and a
  search that classifies every remaining old-name reference as intended compatibility,
  historical evidence, or an omission to fix.
- [ ] Verify the deployed UI → webhook → task/run → email → acknowledgement journey,
  resolution/Escalate now links, invite/verification links, and restart recovery.
- [ ] Deploy through the existing release process, monitor, and record evidence in
  the launch checklist and changelog. Keep local and deployed results separate.
- **Done when:** the acceptance criteria below are supported by evidence.

## Domain cutover and rollback

If an existing deployment changes domain, prepare DNS/TLS, approved CORS origins,
`VITE_API_BASE_URL`, `ALERTOPS_UI_BASE_URL`, and verified email sending before launch.
Record the actual old/new endpoints and immutable UI/backend release artifacts.
Keep the old API/webhook endpoint working; do not rely on redirects for webhook
POSTs. Preserve old UI deep links and query tokens through at least the longest
configured lifetime of already-issued verification, invitation, acknowledgement,
and manual-action links. Record a retirement date only after clients have moved.

Browser session storage is origin-scoped: a domain change requires signing in and
selecting the team again; do not transfer authentication tokens through URLs.
Retaining existing storage keys avoids a forced session reset on the same origin.

Rollback restores the previous UI/backend artifacts, origins, sender configuration,
and traffic routing. Keep old and new deep-link endpoints reachable for emails
sent by either release. Because the initial rebrand retains database, broker,
cache, resource, and package identifiers, rollback needs no data migration. Any
later technical rename must have its own dependency inventory and migration plan;
renaming namespaces or PVCs is not a cosmetic operation.

## Acceptance criteria and intended verification

- [ ] Exact name, capitalization, domain choice, copy, and assets are agreed and used
  consistently in UI, emails, and current guidance; no unsupported feature claims.
- [ ] Brand changes preserve authentication, team isolation, navigation, webhook
  compatibility, email token safety, lifecycle behavior, and saved execution state.
- [ ] Existing config/storage/queue/deployment identifiers are retained and documented;
  old public endpoints and issued links survive any domain cutover.
- [ ] All residual AlertOps references are reviewed and classified; historical records
  and applied migrations are preserved.
- [ ] Local UI build/lint, focused backend tests, and full Maven verification pass;
  deployed smoke checks and rollback are proven
  before release readiness is marked complete.

Implementation commands: `npm --prefix ui run build`, `npm --prefix ui run lint`,
`./mvnw -Dtest=NotificationTest,EmailVerificationServiceTest,TeamInvitationServiceTest test`,
and `./mvnw --batch-mode --no-transfer-progress clean verify`. Add focused email
rendering coverage where current service tests do not exercise the changed content.
Run `git diff --check`; search tracked sources with
`git grep -n -i -E 'alertops|alert_ops|alert-ops'` and separately inspect split wordmarks.

Current outcome: ReplyTrail is implemented across the local public identity surfaces,
email templates, metadata, and current guidance. Application/API/storage/deployment
identifiers remain unchanged; domain ownership, sender verification, deployed smoke
checks, and rollback evidence are still pending.
