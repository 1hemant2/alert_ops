# Changelog

Completed repository changes, newest first. Tracking begins on 2026-10-05;
earlier work has not been reconstructed. Planning and archive rules are in
[AGENTS.md](AGENTS.md#task-plans-and-documentation).

## 2026-10-08 — Fix Spring Boot 4 integration-test imports

- Update integration-test Redis, JDBC, JPA, AMQP, and entity-scan imports to
  the Spring Boot 4 packages and class names, restoring clean CI compilation.
- Verification: clean test compilation and `./mvnw --batch-mode
  --no-transfer-progress clean verify` pass with 238 tests, 0 failures/errors,
  and 17 environment-gated skips; `git diff --check` passes.

## 2026-10-08 — Complete webhook event history UI

- Add expandable webhook event history and link each event to its specific task
  detail page while preserving the existing run and payload links.
- Verification: UI typecheck and production build pass; lint passes with two
  pre-existing flow-editor warnings, and `git diff --check` passes. PostgreSQL,
  Redis, browser, and deployed verification remain pending.

## 2026-10-08 — Complete task and run context UI

- Add team-scoped task detail/editing, metadata links, and saved task context on
  escalation details using the run snapshot rather than mutable task values.
- Verification: UI typecheck and production build pass; lint passes with two
  pre-existing flow-editor warnings. Browser/deployed verification and older
  webhook event navigation remain pending.

## 2026-10-08 — Snapshot task metadata in executions and emails

- Persist priority, category, and reference URL in each execution-step snapshot
  at run start and include the saved values in escalation email content without
  changing later runs when the task is edited.
- Add migration and focused coverage; `./mvnw -q package` passes with 260 tests,
  0 failures/errors, and 17 environment-gated skips. PostgreSQL migration and
  deployed email verification remain pending; task/webhook UI gaps remain open.

## 2026-10-08 — Fix backend image vulnerabilities

- Upgrade the backend to Spring Boot `4.0.8`/Spring Framework `7.0.9`, apply fixed
  Jackson, RabbitMQ, and Tomcat versions, refresh pinned container bases, and update
  the Hibernate 7 and MVC test compatibility imports.
- Verification: backend Maven dependencies report no HIGH or CRITICAL findings,
  `./mvnw -q package` passes with 260 tests and 17 environment-gated skips, and
  `git diff --check` passes. Final Docker image build/scan remains for CI because
  Docker is unavailable locally; the UI has a separate `source-map-js` finding.

## 2026-10-08 — Refresh launch checklist reassessment date

- Update the product launch checklist header to reflect the latest readiness review date without changing any implementation or production-verification status.
- Verification: documentation diff review and `git diff --check` pass; no runtime tests were needed and the change remains uncommitted.

## 2026-10-08 — Harden webhook task validation and ingress limits

- Align manual and webhook task limits, validate manual priority and description updates, inject the application clock for webhook event timing, and reject oversized declared webhook bodies before JSON parsing.
- Add focused coverage for webhook metadata, required/optional validation, same-team flow selection, secret state, replay/conflict, failure paths, size/rate limits, and manual task limits.
- Verification: focused tests and `./mvnw -q package` pass (260 tests, 0 failures/errors, 17 environment-gated skips); PostgreSQL/Redis multi-instance and deployed verification remain pending, and changes are intentionally uncommitted.

## 2026-10-08 — Complete incident lifecycle test coverage

- Add focused authorization and lifecycle-boundary tests for anonymous access, foreign-team starts, early scheduled starts, and preserving scheduler wake-ups when a start fails; retain the existing race, idempotency, acknowledgement, resolution, and timeout coverage.
- Mark incident lifecycle implementation complete locally while keeping PostgreSQL and deployed verification open.
- Verification: lifecycle-focused tests, full Maven package, and diff checks pass; PostgreSQL/deployed integration and independent read-only verification remain unavailable. Changes are intentionally uncommitted.

## 2026-10-08 — Add escalation activity timeline UI

- Add a read-only escalation activity section backed by the paginated history API, with plain milestone wording, actor/time/details, retry grouping, expandable attempts, empty/error/loading states, refresh, and load-more behavior.
- Keep current saved step progress separate and invalidate activity history after lifecycle actions; token-like and raw metadata remain excluded from the UI.
- Verification: UI build, UI lint, full Maven package, and diff checks pass; lint has two pre-existing flow-page warnings, browser/deployed verification remains pending, and no independent read-only verifier was available. Changes are intentionally uncommitted.

## 2026-10-08 — Add escalation activity history API

- Add a read-only, same-team paginated escalation history endpoint with stable ordering, explicit user/system actors, structured safe details, and removal of token-like metadata.
- Verification: focused history tests, full Maven package (232 tests, 0 failures/errors, 17 environment-gated skips), and diff checks pass; PostgreSQL/deployed verification remains pending. Changes are intentionally uncommitted.

## 2026-10-08 — Extend escalation audit event coverage

- Add creation, notification acceptance/failure/retry, and final exhaustion events with safe step, recipient, attempt, deadline, and system/actor context; preserve existing acknowledgement, manual escalation, resolution, timeout, scheduling, start, cancellation, and start-failure events.
- Verification: focused lifecycle/audit tests, full Maven package, and diff checks pass; infrastructure-gated and deployed verification remain pending. Changes are intentionally uncommitted.

## 2026-10-08 — Complete escalation and email UI

- Add team and recipient resolution controls, saved resolution-deadline display, lifecycle-aware acknowledgement messaging, and readable paused/sent/failed/skipped step explanations.
- Improve Escalate now stale, expired, and unavailable-action messaging and expose the existing resolution API through the UI.
- Verification: UI production build, focused acknowledgement/resolution tests, and backend package build pass; PostgreSQL/deployed verification remains pending.

## 2026-10-08 — Simplify manual Escalate now code

- Remove four redundant helper methods and rename the remaining internal helpers to explicit business names while preserving authorization, locking, deadline, token, audit, and idempotency behavior.
- Update `AGENTS.md` to prefer the smallest readable implementation and to remove wrappers or abstractions that do not protect a real boundary.
- Verification: focused manual-action/security tests, backend package build, and `git diff --check` pass; PostgreSQL/deployed verification remains pending.

## 2026-10-08 — Clarify Escalate now transition naming

- Rename the private manual-action helper from `applyLocked` to `applyEscalateNowTransition` so its business responsibility is explicit while preserving behavior.
- Verification: focused manual-action tests, backend package build, and `git diff --check` pass.

## 2026-10-08 — Implement Escalate now

- Add authenticated same-team and recipient-token Escalate now actions for OPEN and ACKNOWLEDGED runs, with expected-step validation, immediate durable scheduling, resolution-wait cancellation, explicit token capability, and actor/source/target audit details.
- Add signed-in and email preview/confirmation UI plus an Escalate now email action for non-final notifications; stale, terminal, expired, and repeated actions remain scoped and idempotent.
- Verification: focused manual-action, acknowledgement, notification, consumer, and security tests; full Maven suite (224 tests, 0 failures/errors, 17 environment-gated skips); backend package; UI build; and diff checks pass. PostgreSQL/RabbitMQ/Redis and deployed verification remain pending; no independent read-only verifier was available.

## 2026-10-07 — Make naming guidance business-focused

- Update `AGENTS.md` to prioritize business responsibilities and user-visible outcomes over implementation mechanisms when naming classes and methods, with concise timeout examples.
- Verification: naming-guidance review, `git diff --check`, plan-length check, and backend compile pass; no runtime behavior changed, so the full test suite was not rerun.

## 2026-10-07 — Use clear timeout terminology for escalation waits

- Replace internal `deadline` component names with `EscalationTimeoutService`, `EscalationTimeoutScheduler`, and related timeout messages; explain that acknowledgement timeout exhausts an open escalation while resolution timeout resumes the next step or exhausts the run.
- Preserve the canonical persisted `resolutionDeadline` field and `dueAt` boundary; only internal names, comments, and audit detail labels changed.
- Verification: focused timeout, acknowledgement, and resolution tests; full Maven suite (190 tests, 0 failures/errors, 17 environment-gated skips); backend package; UI build; compile; and diff/name checks pass. PostgreSQL/deployed verification remains pending, and no independent read-only verifier was available.

## 2026-10-07 — Clarify previous deadline component naming

- Rename the durable deadline transition component to `EscalationDeadlineTransitionService` and the in-memory timer owner to `EscalationDeadlineWakeUpScheduler`; clarify their class documentation and dependency names without changing lifecycle behavior.
- Verification: focused deadline, acknowledgement, and resolution tests; full Maven suite (190 tests, 0 failures/errors, 17 environment-gated skips); backend package; UI build; and diff/name checks pass. PostgreSQL/deployed verification remains pending, and no independent read-only verifier was available.

## 2026-10-07 — Clarify method names and comments in deadline handling

- Add concise explicit naming and one-line method-comment rules to `AGENTS.md`.
- Rename deadline recovery, wake-up, delivery, and scheduling methods to state their purpose; replace `rearm` terminology with `rescheduleDeadlineWakeUp` and update related tests/docs without changing lifecycle behavior.
- Verification: focused tests, full Maven suite (190 tests, 0 failures/errors, 17 environment-gated skips), backend package, UI build, and diff/document checks pass. PostgreSQL/deployed verification remains pending.

## 2026-10-07 — Implement deadline expiry and recovery

- Persist shared acknowledgement boundaries after successful sends, recover final acknowledgement/resolution deadlines after restart, and safely expire or resume/exhaust runs under the database lock.
- Enforce acknowledgement deadlines on POST and record automatic acknowledgement/resolution expiry audit events without adding parallel timing fields.
- Verification: full Maven tests (190 tests, 0 failures/errors, 17 environment-gated skips), backend package, UI build, focused expiry/recovery tests, and documentation checks pass. PostgreSQL/deployed concurrency, rollback, and restart verification remains pending.

## 2026-10-07 — Implement explicit resolution

- Add locked team-member and recipient-token resolution from `ACKNOWLEDGED` to `RESOLVED`, with actor/time persistence, active-owner clearing, unsent-step skipping, audit history, and post-commit wake-up cancellation.
- Enforce the saved resolution deadline and exact current recipient/step ownership; use the saved deadline rather than token TTL for action eligibility and require exact accepted token-hash/step/source audit evidence for idempotent recipient replays.
- Verification: focused resolution/security tests, full Maven tests (177 tests, 0 failures/errors), backend package, UI build, and documentation checks pass. Independent read-only review was **Inconclusive** only for PostgreSQL/deployed migration, rollback, and concurrency behavior because PostgreSQL/RabbitMQ/Redis integration checks remain environment-gated.

## 2026-10-07 — Implement acknowledgement pause

- Add the enabled `ACKNOWLEDGED` lifecycle transition, exact current-step validation, saved resolution owner/deadline, next-step pause, and post-commit wake-up cancellation while preserving disabled terminal acknowledgement behavior.
- Keep successful final sends open for acknowledgement and record the acknowledgement audit event; `dueAt` remains the shared acknowledgement/delivery boundary with no redundant acknowledgement-deadline field.
- Verification: focused tests, full Maven tests, backend package, UI build, and documentation checks pass. PostgreSQL/RabbitMQ/Redis integration checks remain environment-gated; independent read-only verifier verdict: **Achieved**.

## 2026-10-06 — Clarify agreed-decision implementation guardrails

- Require implementation plans to identify binding agreed decisions and canonical sources of truth before adding fields, columns, timers, or API properties.
- Require conflicting earlier implementation and tests to be removed or revised when the user clarifies a decision, with focused re-verification before handoff.
- Verification: documentation whitespace, plan-length, anchor, and diff checks pass.

## 2026-10-06 — Persist runtime resolution snapshots

- Add V13 durable per-step resolution snapshots, run-level acknowledgement ownership/resolution deadlines, and exact execution-step binding for acknowledgement tokens; reuse `dueAt` as the canonical shared acknowledgement/delivery deadline.
- Propagate the flow/node snapshot through immediate and scheduled starts, with focused coverage for enabled/disabled timing, failed writes, same-recipient steps, and mismatched tokens.
- Follow-up correction removes the redundant per-step acknowledgement-deadline column so the shared wait has one durable time source.
- Verification: focused tests, full `mvn test`, backend packaging, UI build, documentation checks, and diff review pass. PostgreSQL/RabbitMQ/Redis integration remains environment-gated and skipped; independent read-only verifier verdict: **Achieved**. PostgreSQL rollback/migration execution remained unavailable.

## 2026-10-06 — Add flow/node resolution timing configuration

- Add the flow-level resolution-timeout toggle, nullable positive per-node timeout storage, version-checked atomic timing updates, and matching create/edit validation.
- Add flow-editor controls for enabling the timeout and setting each node's resolution window while retaining the existing shared wait duration.
- Update the PostgreSQL scheduling fixtures to pass the unified execution-step enum, preserving full-suite compilation after the preceding status migration.
- Verification: focused flow tests, full `mvn test`, backend packaging, UI build, documentation checks, and diff review pass. PostgreSQL/Redis/RabbitMQ integration tests remain environment-gated and skipped; independent read-only verification was unavailable.

## 2026-10-06 — Unify execution-step statuses

- Replace the independent execution/notification status strings with the persisted `FlowExecutionStepStatus` enum, migrate legacy rows with V11, rebuild publication recovery queries, and reject unsupported database values.
- Update delivery/retry/duplicate-message paths, disabled acknowledgement skip handling, execution-state API/UI mappings, and focused tests so each step has one authoritative status.
- Verification: focused tests, full `mvn test`, backend packaging, and `npm run build` pass. PostgreSQL integration remains skipped by its environment gate (13 skipped tests); independent read-only verification was inconclusive because two dispatched verifier attempts did not return reports and were shut down.

## 2026-10-06 — Settle resolution lifecycle storage semantics

- Decide that `RESOLVED` is self-describing, while `COMPLETED` requires `ACKNOWLEDGED` or `EXHAUSTED`; clear active acknowledgement ownership when the run leaves that state and retain history in audit events.
- Mark the resolution-timeout requirements prerequisite complete in the [launch checklist](docs/product-launch-readiness.md#3-resolution-timeout-after-acknowledgement); implementation remains pending, with execution-step status unification selected as the next task.
- Verification: focused documentation diff review, link/anchor, whitespace, and plan-length checks passed. Product tests/builds were not applicable; independent subagent verification was skipped because no usable subagent mechanism was exposed.

## 2026-10-06 — Require independent subagent completion verification

- Update [AGENTS.md](AGENTS.md#verification-and-handoff) to require a read-only verification subagent for each repository task, with acceptance-criteria evidence, an explicit verdict, and documented limitations before handoff.
- Require the primary agent to reconcile the verifier's report and record when the environment cannot provide a subagent; no product behavior changed.
- Verification: local documentation links/anchors, whitespace, plan length, and diff review passed. An independent verifier initially returned `Not achieved` because the task plan and changelog still said verification was pending; that report was reconciled, and a final verifier returned `Achieved`. A later audit was `Inconclusive` only because its child context could not recursively verify the parent-run subagent mechanism; the parent had successfully used that mechanism, and recursive verification is not required. Documentation linters and product tests/builds were not applicable or unavailable.

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
