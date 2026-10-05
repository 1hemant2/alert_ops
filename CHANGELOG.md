# Changelog

Completed repository changes, newest first. Tracking begins on 2026-10-05;
earlier work has not been reconstructed. Planning and archive rules are in
[AGENTS.md](AGENTS.md#task-plans-and-documentation).

## 2026-10-06 — Document Spring test-context dependency checks

- Add [AGENTS.md](AGENTS.md#verification-and-handoff) guidance to update `@WebMvcTest`/slice-test mocks and direct constructor tests whenever Spring constructor dependencies change.
- Require focused context verification and diagnosis of the first underlying `UnsatisfiedDependencyException`; no product behavior changed.
- Verification: whitespace, plan length, and diff review pass. Changes remain uncommitted.

## 2026-10-06 — Restore security test context wiring

- Add the missing `EscalationStartScheduler` Mockito bean to the MVC security test after the controller gained that constructor dependency.
- Anonymous escalation, schedule, reschedule, and cancel requests continue to verify `401` responses; production wiring is unchanged.
- Verification: focused `EscalationSecurityTest` and backend packaging pass. Changes remain uncommitted.

## 2026-10-06 — Require separate commits by change category

- Update [AGENTS.md](AGENTS.md#scope-and-version-control) to keep backend, frontend, test, and documentation changes in separate commits, requiring confirmation before committing to split mixed staging and using a fixed backend-to-documentation order.
- Preserve unrelated unstaged changes while separating commits; no product behavior changed.
- Verification: documentation links/anchors, whitespace, plan length, and diff review pass. Changes remain uncommitted.

## 2026-10-06 — Require explicit mode APIs in agent guidance

- Update [AGENTS.md](AGENTS.md#changes) to prohibit overloaded methods and boolean mode flags for materially different business behavior; require explicit enums/request objects or clearly named operations with focused mode tests.
- This documents the `startFlowExecution` refactoring practice for future work. No product behavior or existing staged changes were changed.
- Verification: documentation links/anchors, whitespace, and current-plan length checks pass; no behavior tests or builds were needed.

## 2026-10-06 — Simplify flow execution start modes

- Replace the overloaded/boolean `startFlowExecution` API with one explicit `FlowExecutionStartMode` enum covering idle, due scheduled, and early scheduled starts.
- Update all callers and focused tests without changing claims, transaction boundaries, actor auditing, first-step delays, or timer behavior.
- Verification: focused Maven tests, backend packaging, UI build, and diff/whitespace checks pass. The implementation is recorded in the preceding implementation commit.

## 2026-10-06 — Implement Start now for scheduled escalations

- Allow same-team users to start a scheduled escalation early through the existing start API, using a conditional `SCHEDULED → OPEN` claim, normal first-step delay, actor audit, and post-commit timer cancellation.
- Add scheduled-run UI wording and focused use-case, service, controller, and scheduling tests. Automatic due-time starts remain due-gated; PostgreSQL/deployed verification remains pending.
- Verification: focused Maven tests, backend packaging, UI build, and diff/whitespace checks pass. Resolution, Escalate now, webhook gaps, and activity timeline remain separate tasks.

## 2026-10-06 — Correct webhook launch progress from implementation evidence

- Audit existing webhook/task backend, UI, migrations, and email code; replace stale unchecked tracking in [Feature 2](docs/product-launch-readiness.md#feature-2-create-and-start-an-escalation-through-a-webhook) with completed implementation pieces and explicit remaining metadata, event-history, validation, and verification gaps.
- Mark the existing webhook plan partially implemented, not release-ready. No product code was changed or repaired in this audit.
- Verification: the existing mocked-SMTP Notification test, backend packaging, UI build, and document checks pass. Webhook-specific tests are absent; PostgreSQL webhook behavior and deployed end-to-end checks were not run.

## 2026-10-06 — Defer Send test escalation until after launch

- Move Send test escalation to the [feature backlog](docs/feature-backlog.md#send-test-escalation) and remove it from initial-launch requirements. Revisit after real users start using AlertOps; detailed test behavior remains undecided.
- Verification: documentation links/anchors and whitespace checks only. No product code, lifecycle states, or other release requirements changed.

## 2026-10-06 — Clarify the shared acknowledgement and delivery wait

- Correct the [lifecycle plan](docs/resolution-timeout-implementation-plan.md#agreed-uniform-node-waits-and-final-step-outcome) and Requirement 3: existing delivery delay is the acknowledgement wait, not another duration to stack or compare. Resolution duration starts at acknowledgement when enabled; unresolved expiry advances immediately.
- Remove the reopened timing question and add a shared-wait verification example. No new setting or state is planned; implementation and deployed verification remain pending.
- Verification: documentation links/anchors and whitespace checks only; product code is unchanged and no behavior or deployed tests ran.

## 2026-10-06 — Settle email Escalate now eligibility

- Record current-recipient acknowledgement/resolution windows for [email Escalate now](docs/resolution-timeout-implementation-plan.md#escalate-now-from-email); links stay viewable after expiry or advancement, but cannot advance another step through repeated confirmation.
- Update Requirements 3/4 and planned boundary, race, restart, and duplicate-click checks. Carry forward the closed resolution-access decision using existing same-team rules, with no new roles or states.
- Verification: documentation links/anchors and whitespace checks only; implementation and deployed verification remain pending. No product code or commits changed.

## 2026-10-06 — Record viewable links with deadline-gated response actions

- Record [acknowledgement/resolution action rules](docs/resolution-timeout-implementation-plan.md#agreed-viewable-links-and-action-deadlines): keep links viewable, reject new late/obsolete actions, and show saved outcomes for previously accepted actions without restarting timers.
- Update Requirement 3 and acceptance cases for independent deadlines, stale links, delayed timers, and restart. Correct planning wording to retain the already-agreed two node response durations without another setting; resolution permissions and email Escalate now eligibility remain open.
- Verification: documentation links/anchors and whitespace checks; product implementation remains pending, with no behavior or deployed tests run.

## 2026-10-05 — Plan uniform node waits and final-step exhaustion

- Record [uniform node rules](docs/resolution-timeout-implementation-plan.md#agreed-uniform-node-waits-and-final-step-outcome): the last recipient gets the same acknowledgement/resolution waits; expired waits with no next step exhaust the run, without resending.
- Update Requirement 3 and implementation/recovery acceptance cases. Acknowledgement-window configuration remains open because the current node duration is a pre-send delay; no special final-node timeout is planned.
- Verification: documentation checks only; no product code or implementation-readiness claims changed, and no behavior or deployed tests ran.

## 2026-10-05 — Plan readable incident activity history

- Add the agreed user/automatic event history, important milestones, and expandable
  failure/retry summaries to the [shared lifecycle plan](docs/resolution-timeout-implementation-plan.md#agreed-incident-activity-timeline).
- Mark Requirement 5 agreed and plan audit coverage, a team-scoped history API, and
  detail-page presentation; implementation and deployed verification remain pending.
- Verification: documentation checks only; product code is unchanged and no behavior
  or deployed tests ran. Existing passing backend/UI builds are unchanged.

## 2026-10-05 — Plan early scheduled starts and manual escalation from UI or email

- Extend the [shared lifecycle plan](docs/resolution-timeout-implementation-plan.md)
  with Start now for scheduled runs and Escalate now for waiting/paused next steps,
  including email-token preview/POST confirmation, recipient audit, stale-link checks,
  transactions, timer cleanup, races, and focused implementation tasks.
- Mark Requirement 4 agreed while keeping implementation pending; the activity
  timeline and remaining resolution edge cases still need discussion.
- Verification: documentation links/anchors and whitespace checks passed. Product code
  is unchanged; prior passing backend/UI builds were reused, and no behavior tests ran.

## 2026-10-05 — Plan resolution timeout after acknowledgement

- Record the agreed flow toggle, per-node timeouts, runtime snapshots, single
  step-status enum, and immediate next-step delivery when resolution expires in
  the [feature plan](docs/resolution-timeout-implementation-plan.md).
- Link Requirement 3 to ordered implementation tasks and identify the remaining
  product decisions; implementation and release verification remain pending.
- Verification: local document-link targets, whitespace checks, backend packaging,
  and UI build passed. No behavior tests or deployed checks ran for this documentation task.

## 2026-10-05 — Require concise task plans and maintain a changelog

- Require a plan before each implementation and reuse one current task plan to
  limit document growth.
- Define when to reuse feature plans, maintain lasting documentation, and archive
  completed plans and older changelog entries.
- Verification: document links and whitespace checks, backend packaging, and UI
  build passed. Tests and deployed checks were not run for this documentation-only change.
