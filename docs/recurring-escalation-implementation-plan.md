# Recurring escalation implementation plan

Created: 2026-10-11
Updated: 2026-10-11 — minimum first version.
Status: Implemented locally; deployment verification remains pending.

## Minimum scope

Add repetition to an escalation without a new table or scheduling subsystem.
The original escalation is the first run and owns repetition. Every later
occurrence has a new ID and independent steps, emails, acknowledgement, and
resolution. Runs may overlap; completion of an earlier run does not block repeats.

Use these defaults instead of another requirements-agreement phase:

- Support NONE, DAILY, and WEEKLY. Repeat every calendar day or week, not after
  the previous run completes.
- Enable recurrence only with Schedule for later. Use its future local date/time
  and IANA timezone. Existing immediate and one-time starts remain unchanged.
- Repeat until stopped. Stopping prevents future creation; existing runs,
  including the original, continue independently.
- After downtime or a delayed callback, create only the latest missed repeat,
  then advance to the next future occurrence. Do not replay a backlog. Saved
  SCHEDULED runs still recover through the existing start scheduler.
- Future runs reuse the original task/path references and current start behavior.
  Started response steps keep their existing execution snapshots.
- Failed occurrences use existing scheduled-start retries and START_FAILED
  notifications. Repetition continues; do not add automatic series-pausing logic.
- Start now and cancel affect one run only. Do not allow rescheduling a recurring
  original or generated run in this version; preserve the calendar anchor and
  occurrence identity. Ordinary one-time rescheduling remains available.

The [launch checklist](product-launch-readiness.md#1-scheduled-escalation-start)
tracks this extension separately from the existing one-time baseline. Defining
this scope does not claim implementation or release readiness. Deployment is last.

## Storage and ownership

Add only three fields to the existing Escalation table:

| Field | Meaning |
| --- | --- |
| repeatType | String-persisted enum: NONE, DAILY, WEEKLY. No separate repeat boolean. |
| nextRepeatAt | Nullable UTC Instant on the original. Non-null means enabled; null means no future repeats. |
| repeatSourceId | Nullable self-reference to the original. Set only on generated runs. |

The original has no repeatSourceId. Generated runs have repeatType = NONE and
nextRepeatAt = null, so they cannot create their own repeating chains.
Stopping clears nextRepeatAt but retains the rule and historical links.
EscalationStatus describes only the run; do not add a separate repeat status.

Reuse the original scheduledStartAt and scheduleTimezone as the calendar anchor.
Never overwrite that start time with nextRepeatAt, or add another anchor field.
Each generated run's scheduledStartAt is its own intended occurrence instant.
Calculate from the original local wall time, not callback completion or fixed
24-hour UTC additions. Use injected Clock and UTC Instants for actual moments.

For daylight saving, reject a nonexistent initial local time so the saved anchor
is unambiguous; choose the earlier offset for an ambiguous time. For later
occurrences in a gap, shift forward by the gap without changing the original
anchor. These are fixed defaults, not additional configurable options.

Default existing rows to NONE. Add enum/field-combination constraints, a
non-cascading self-reference, indexes for repeat recovery/source lookup, and
uniqueness on (repeatSourceId, scheduledStartAt) for generated rows.
Generated rows must link directly to the same-team original and cannot repeat.

## Execution and recovery

1. Extend existing scheduling to save the original's first-start schedule,
   repeatType, nextRepeatAt, and audit atomically. Register first-start and
   repeat timers only after commit.
2. A repeat callback reloads and locks the original. Check that repetition is
   enabled and the expected nextRepeatAt is current and due. Ignore stale or
   duplicate callbacks. The original need not still be SCHEDULED or OPEN.
3. In one transaction, create a fresh SCHEDULED child for the selected occurrence,
   set its source and non-repeating fields, advance the original's nextRepeatAt,
   and audit creation. The row lock and uniqueness guard duplicate creation.
4. After commit, register the child's start with the existing start scheduler
   and the original's next repeat timer. Reuse existing execution and immediate
   first-email behavior; do not add another start or notification engine.
5. Stop repetition under the same original-row lock, clear nextRepeatAt, and audit
   it. Cancel only the repeat timer after commit. If child creation already
   committed, that child continues. Repeated stop is idempotent.

Internal child creation accepts already-due timestamps; do not call the public
future-only scheduling API. Background work uses the saved team/scheduling actor,
not request-thread authentication.

Startup recovers original rows with a non-NONE rule and non-null nextRepeatAt
regardless of their run status, including COMPLETED, RESOLVED, CANCELLED, and
START_FAILED. Recover saved SCHEDULED children through the existing scheduler.
A crash after commit but before registration must lose neither obligation.

Keep only cancellable timer handles in memory. Use bounded failure-triggered
retry and startup recovery; no healthy periodic scan, extra outbox, dependency,
or duplicate database-owned state. The existing [single-process timer baseline](in-memory-timer-implementation-plan.md)
still applies. Stop-repeat may update metadata on a terminal original, never its
run outcome or response history.

## Minimal API and UI

- Add repeatType to the existing Schedule for later request. Return repeat fields
  through existing escalation list/detail responses.
- Add one same-team stop-repeat action on the original. Recipient email tokens
  cannot control repetition. Reuse existing validation and audit conventions.
- Add a repeat selector to the existing scheduling form. Show repeat type,
  next repeat time, and Stop future repeats on the original's detail screen.
  Explain that cancelling one run does not stop repeats, and stopping repeats
  does not cancel saved runs.
- Generated runs remain in the existing paginated escalation list, with a link
  to the original. Keep growing lists scrollable. No separate history API/view,
  preview endpoint, schedule-management screen, or email redesign.

## Implementation tasks

1. [x] Add the three fields, migration/constraints, enum, and calendar calculation.
2. [x] Extend existing scheduling/read APIs; add atomic child creation and stop-repeat.
3. [x] Connect repeat timers, after-commit registration, failure retry, and recovery.
4. [x] Add the selector, repeat context, source link, and stop action to existing UI.
5. [x] Run focused tests, normal backend build, UI tests/build/lint, and retain
   the existing one-time behavior.
6. [ ] Run PostgreSQL integration checks, then validate database/broker/SMTP
   behavior and restarts in deployment last;
   update launch readiness only with evidence.

Local verification includes the calendar unit test, backend test compilation and
packaging, UI build, UI lint, and diff checks. Mockito-backed service tests could
not execute because Byte Buddy self-attachment is unavailable in this local
environment. PostgreSQL integration and deployed checks remain pending.

## Required verification

- Daily/weekly timezone calculations and DST defaults are deterministic.
- Duplicate/stale callbacks create one child; generated rows never repeat.
- Runs overlap safely; acknowledgement/resolution of one cannot affect another.
- Child creation and calendar advancement roll back together; restart after
  commit recovers both the child start and next repeat.
- Recovery includes terminal originals, handles only the latest missed repeat,
  and ignores stopped repetition. Saved child starts/retries still recover.
- Stop/create races have one winner; repeated stop adds no duplicate audit.
- Timer/database failure retries do not lose repeats or change the anchor.
- Team isolation, missing references, invalid inputs, and source-link null paths
  are covered. Missing/deleted references use existing START_FAILED handling.
- First email, start-now/cancel isolation, and ordinary one-time scheduling,
  rescheduling, cancellation, retries, and lifecycle behavior remain correct.

## Deferred improvements

Monthly/yearly repeats; custom intervals; immediate recurring starts; editing,
pause/resume; end dates/counts; backlog replay; automatic stop on persistent
failure; frozen task/path definitions; independently rescheduling recurring runs;
occurrence previews; grouped history/filtering and a dedicated recurrence screen.

Implement these only when needed, as separate tasks. No additional approval
matrix or placeholder fields are required for the minimum version.

## Existing implementation references

- [Escalation model](../src/main/java/com/alertops/flow_execution_engine/model/Escalation.java)
- [Existing schedule operations](../src/main/java/com/alertops/flow_execution_engine/service/EscalationService.java)
- [Start scheduler and recovery](../src/main/java/com/alertops/flow_execution_engine/service/EscalationStartScheduler.java)
- [Shared start use case](../src/main/java/com/alertops/flow_execution_engine/application/StartFlowExecutionUseCase.java)
