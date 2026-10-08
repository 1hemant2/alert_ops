# Current task plan

## Task: Implement the ReplyTrail public rebrand

Started: 2026-10-08
Status: Complete — local public rebrand implemented; domain/deployed rollout pending

### Goal and scope

Implement the selected public name ReplyTrail across visible application surfaces,
outgoing email copy, current product documentation, and human-readable metadata.
Keep existing API, storage, package, queue, cache, environment, and deployment
identifiers compatible. This task does not purchase a domain or deploy a release.
Previous task: the CI container image fix is complete; its outcome remains in
[the changelog](../CHANGELOG.md#2026-10-08--fix-ci-container-image-build).

### Decision-compliance note

- [Launch readiness](product-launch-readiness.md) remains the release source of truth.
- Preserve agreed task, escalation, acknowledgement/resolution, scheduling,
  webhook, audit, and durable-state behavior in the existing feature plans.
- Distinguish visible branding from existing integration and storage identifiers;
  exclude blanket renames, new features, schema changes, and infrastructure moves.
- ReplyTrail is the selected public name for this implementation; its `.com` and
  `.app` are registered, so no domain claim or purchase is part of this task.
- Keep `alertops.*`, `ALERTOPS_*`, `X-AlertOps-Webhook-Secret`, `com.alertops`,
  session keys, event names, and deployment resource identifiers unchanged.

### Acceptance criteria

- [x] Visible UI, metadata, email copy, and maintained guidance use ReplyTrail.
- [x] Existing integration, storage, package, queue, cache, and deployment identifiers
  remain unchanged and executable.
- [x] Focused tests, UI build/lint, full Maven verification, and diff checks pass.
- [x] Residual AlertOps references are reviewed and independently verified.

### Steps

- [x] Confirm the selected public name and record compatibility constraints.
- [x] Update visible frontend, email, metadata, README, and current checklist copy.
- [x] Update focused assertions and run UI/backend verification.
- [x] Review residual references, obtain independent verification, and record outcome.

### Intended verification and limitations

UI build passes; lint reports two existing FlowDetailPage warnings. Focused
branding/email tests pass (14 tests), and full Maven clean verification passes
(240 tests, 0 failures/errors, 17 environment-gated skips). `git diff --check`
passes. Remaining AlertOps references are compatibility identifiers, historical
records, or planned domain/deployment work; an independent read-only review is
independent read-only verdict is **Achieved**: 132 relative links/anchors were
valid, retained-resource paths were unchanged, and no substantive regression was
found. Domain purchase, real email delivery, and deployed browser/smoke checks
remain outside this local task.
