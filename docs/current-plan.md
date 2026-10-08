# Current task plan

## Task: Explain ReplyTrail and its use cases in the README

Started: 2026-10-08
Status: Complete

### Goal and scope

Rewrite the thirteen [README use cases](../README.md#use-cases) in simple,
plain language, including setup and personal reminders. Remove the complete
customer onboarding walkthrough requested by the user; retain onboarding as
one use case. Scope: README, this plan, and the existing task changelog entry.
Preserve all other worktree changes.

### Decision-compliance note

- [Launch readiness](product-launch-readiness.md) remains the release source of
  truth; local implementation does not establish deployed readiness.
- [Lifecycle decisions](resolution-timeout-implementation-plan.md#agreed-resolution-timeout-behavior)
  govern acknowledgement/resolution; reuse existing task, flow, and run concepts.
- [Webhook decisions](webhook-escalation-implementation-plan.md#task-fields)
  govern context: use source, priority, category, referenceUrl, and saved payload.
- Recipients belong to the team; notifications use email; schedules are one-time.
  Exclude new integrations, domain-specific schemas, approval states, recurring
  schedules, and changes to canonical lifecycle fields or timing boundaries.
- Repeated recipients use distinct existing nodes and execution steps. Waits use
  relative durations and saved dueAt; a 24-hour interval is 1,440 minutes.
  Acknowledgement stops a disabled-resolution run; calendar recurrence is excluded.

### Acceptance criteria

- [x] Thirteen use cases and reminder instructions use everyday language.
- [x] The customer onboarding walkthrough is removed completely.
- [x] Timing, acknowledgement, team membership, and external-tool detection stay accurate.
- [x] Documentation checks and normal builds pass; limitations are recorded.

### Steps

- [x] Inspect current README, plan, changelog, and existing changes.
- [x] Simplify use cases/setup/reminders and remove the requested walkthrough.
- [x] Check links, examples, scope, and whitespace; run UI build and Maven verify.
- [x] Update the existing changelog entry and mark this plan complete.

### Verification and limitations

Pass: thirteen plain-language use cases, complete walkthrough removal, 32
relative links/anchors, eight fenced examples (Bash/JSON syntax), whitespace,
and final scope review. UI build and Maven verify pass: 240 tests, zero failures
or errors, 17 environment-gated skips. Maven ran outside the sandbox for its
JVM test agent. Live email, Docker startup, PostgreSQL/Redis, and deployed
journeys remain unverified.
