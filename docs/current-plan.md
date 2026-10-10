# Current task plan

## Task: Implement minimum recurring escalation schedules

Started: 2026-10-11
Status: Complete — local implementation finished; deployment remains pending

### Goal and scope

Implement the minimum recurring schedule scope from the [feature plan](recurring-escalation-implementation-plan.md)
while preserving one-time escalation behavior. This task includes backend,
frontend, migrations, focused tests, and documentation updates; deployment remains
excluded and is the final release step.

### Decision-compliance note

- Preserve the existing one-time schedule and escalation lifecycle.
- Each occurrence has a new escalation ID; overlapping runs are allowed.
- The existing escalation table owns repeatType, nextRepeatAt, and repeatSourceId.
  The original row owns repetition; children never own a repeating chain.
- PostgreSQL owns calendar progress and run state; timers only provide wake-ups.
- Reuse each run's scheduledStartAt and existing start/retry/publication path.
- Exclude a new schedule entity/table, duplicate repeat boolean, separate schedule
  lifecycle, and custom intervals unless separately agreed.
- Minimum defaults are daily/weekly, scheduled first start, latest missed repeat
  only, and stop without editing. Do not introduce repeat-after-completion,
  monthly/yearly, custom intervals, or a separate schedule table.

### Acceptance criteria

- [x] Add the three recurrence fields and daily/weekly calendar calculation.
- [x] Create independent child runs atomically and recover repeat timers safely.
- [x] Add stop-repeat and the existing-screen scheduling controls.
- [x] Run focused backend/frontend checks and update readiness documentation.

### Steps and verification

- [x] Inspect existing scheduling, execution, timer guidance, and launch tracking.
- [x] Confirm the canonical fields, minimum defaults, and excluded improvements.
- [x] Implement backend persistence, occurrence creation, timer recovery, and API.
- [x] Implement the existing UI controls and source-run context.
- [x] Run focused tests, normal builds, and diff checks; record limitations.

### Verification results

`EscalationRepeatCalculatorTest` passes. Backend test compilation and packaging,
UI build, UI lint, and `git diff --check` pass; UI lint retains two existing
warnings in `FlowDetailPage.tsx`. The full Mockito-backed service suite could not
start because Byte Buddy cannot self-attach on this local JDK/process environment.
PostgreSQL, broker, SMTP, restart, and deployed checks remain intentionally last.
