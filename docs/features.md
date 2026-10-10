# ReplyTrail feature guide

This is the maintained, human-readable and AI-readable overview of ReplyTrail.
It explains what each feature does, who can use it, the important rules, and
where the exact implementation or release decision is recorded.

**Last reviewed:** 2026-10-11  
**Product:** ReplyTrail  
**Audience:** users, operators, contributors, and AI agents working in this repository

## How to read this guide

Each feature follows the same order:

1. **Purpose** — the user problem the feature solves.
2. **Who can use it** — the actor and team boundary.
3. **How it works** — the normal workflow.
4. **Rules and limits** — behavior that must not be inferred differently.
5. **Status and sources** — what is implemented locally and what still needs release verification.

“Implemented locally” means the repository contains the backend and UI behavior
and focused local checks have passed. It does not mean that PostgreSQL,
RabbitMQ, Redis, SMTP, restart recovery, or a deployed end-to-end journey has
been verified. The [product launch checklist](product-launch-readiness.md) is
the source of truth for release readiness.

## Product in one sentence

ReplyTrail turns an alert or request into a task, sends it through an ordered
response path, and records who responded, when the next person was notified,
and what happened afterward.

## Core vocabulary

| Term | Meaning | Do not confuse it with |
| --- | --- | --- |
| **Team** | A workspace containing members, tasks, response paths, webhooks, and escalation runs. | A user account. |
| **Task** | Reusable context describing the work that needs attention. | An escalation run. |
| **Response path** | A reusable ordered list of team-member recipients and timing. The API and Java code call this a **flow**. | The task being acted on. |
| **Response step** | One recipient and its timing inside a response path or saved run snapshot. | A whole response path. |
| **Escalation run** | One execution of a task through a response path. | The reusable task or path. |
| **Acknowledgement** | A recipient confirms that they accept responsibility. | Resolution of the underlying issue. |
| **Resolution** | The current recipient confirms that the issue is finished while a resolution window is open. | Acknowledgement. |
| **Activity event** | A saved user or system milestone in the run history. | Timer housekeeping or a raw stack trace. |

The public product name is **ReplyTrail**. Existing `com.alertops` packages,
`ALERTOPS_*` settings, `alertops.*` storage keys, and the
`X-AlertOps-Webhook-Secret` header remain compatibility identifiers. Do not
replace those technical identifiers as part of an ordinary feature change.

## End-to-end journey

```text
Register and verify email
          |
          v
Create or select a team and add members
          |
          v
Create a task --------------+
          |                 |
          v                 v
Create a response path   Configure a webhook
          |                 |
          +--------+--------+
                   v
          Create an escalation run
             |                 |
       Start immediately   Schedule for later
             |                 |
             +--------+--------+
                      v
          Send response steps by email
                      |
       Acknowledge, resolve, or escalate now
                      |
             View saved activity history
```

## Feature catalog

| ID | Feature | Primary outcome | Current status |
| --- | --- | --- | --- |
| F-01 | Accounts and teams | A verified user works inside the correct team. | Implemented locally; membership-policy and deployed verification remain. |
| F-02 | Tasks | A request has reusable, searchable context. | Implemented locally; deployed verification remains. |
| F-03 | Response paths | A team defines ordered recipients and waits once, then reuses that path. | Implemented locally; deployed verification remains. |
| F-04 | Escalation runs | One task follows one saved path snapshot with an explicit lifecycle. | Implemented locally; PostgreSQL/deployed verification remains. |
| F-05 | Start and scheduling | A run starts now, starts later, or repeats daily/weekly. | Implemented locally; capacity and deployed verification remain. |
| F-06 | Email acknowledgement and resolution | A recipient can accept responsibility and, when enabled, resolve the issue. | Implemented locally; PostgreSQL/deployed verification remains. |
| F-07 | Escalate now | An eligible user can make the next response step due immediately. | Implemented locally; PostgreSQL/deployed verification remains. |
| F-08 | Webhook intake | An external system can create an idempotent task and run. | Implemented locally; PostgreSQL/Redis/deployed verification remains. |
| F-09 | Activity history | People can investigate the complete saved run timeline. | Implemented locally; PostgreSQL/deployed verification remains. |
| F-10 | Recovery and duplicate safety | Restart, retry, and duplicate delivery do not silently lose workflow state. | Implemented locally; infrastructure and deployed verification remain. |

## F-01 — Accounts and teams

### Purpose

Keep users, recipients, tasks, paths, webhooks, and runs inside an explicit team
boundary.

### Who can use it

- A new user registers and verifies an email address before normal sign-in.
- A verified user can create or select a team and can belong to more than one team.
- Team owners and administrators can invite members. The invited email address
  must be the verified address that accepts the invitation.
- The server, not the browser, is authoritative for membership and permissions.

### How it works

1. Register and follow the email verification link.
2. Sign in and select the team to work in.
3. Invite the people who may receive response steps.
4. Create tasks and response paths using members of that team.

### Rules and limits

Team-scoped reads and writes must not expose another team's data. A token that
contains a team identifier does not replace a live membership check. Email
action tokens are scoped to their specific run and recipient; they do not grant
access to the team's full history.

### Status and sources

The core account, team selection, invitation preview, and invitation acceptance
journeys are present locally. The remaining role-management policy is tracked
in the [team management task list](team-management-ui-task-list.md). See the
[authentication controller](../src/main/java/com/alertops/auth/controller/UserController.java)
and [team controller](../src/main/java/com/alertops/team/controller/TeamController.java).

## F-02 — Tasks

### Purpose

Give every response run a clear description of what needs attention and enough
context for the recipient to act.

### Who can use it

Authenticated members of the selected team can create and view team tasks.

### How it works

1. Create a task with a name and description.
2. Set a source. Manually created tasks default to `Manual` when no source is supplied.
3. Optionally add priority, category, and an HTTP(S) reference URL.
4. Edit reusable task details later without changing the context already saved in a running run.

### Rules and limits

When a run starts, ReplyTrail snapshots the task context into its response
steps. Later edits to the reusable task do not rewrite history or change an
active run's saved context. A webhook must provide `eventId`, `taskName`,
`description`, and `source`; priority, category, and `referenceUrl` are optional.

### Status and sources

Task creation, editing, listing, and detail UI are implemented locally. See the
[task service](../src/main/java/com/alertops/task/service/TaskService.java),
[task controller](../src/main/java/com/alertops/task/controller/TaskController.java),
and the [webhook feature plan](webhook-escalation-implementation-plan.md).

## F-03 — Response paths

### Purpose

Define the ordered people and timing used whenever a task needs a response.

### Who can use it

Authenticated members of the selected team can create, view, and configure
paths. Every recipient in a path must be a member of that team.

### How it works

1. Create a named response path.
2. Add one or more ordered response steps.
3. For each step, choose a team-member email recipient and a wait duration.
4. Optionally enable resolution timeout for the path and set a positive resolution timeout on every step.
5. Reorder, edit, or remove steps before starting a run.

### Timing rules

- The first response email is sent immediately when the run starts.
- The configured step duration is the acknowledgement wait after delivery is accepted.
- If nobody acknowledges, the next eligible step becomes due after that same wait.
- Resolution timeout is an optional path-level mode, not a separate run-wide timer.
- With resolution timeout enabled, acknowledgement pauses the next step and
  starts the current recipient's resolution window.
- A started run keeps a runtime snapshot of path recipients and timing. Editing
  the reusable path does not rewrite the active run.

### Status and sources

Path and step editing, ordering, timing, and runtime snapshots are implemented
locally. The exact timing decisions are binding in the
[resolution timeout plan](resolution-timeout-implementation-plan.md) and the
[flow controller](../src/main/java/com/alertops/flow/controller/FlowController.java).

## F-04 — Escalation runs and lifecycle

### Purpose

Execute one task through one saved response path while making every lifecycle
transition explicit, auditable, and safe under concurrent requests.

### Lifecycle states

| State | Meaning | Terminal? |
| --- | --- | --- |
| `IDLE` | Created but not started or scheduled. | No |
| `SCHEDULED` | Waiting for its start time or an eligible start retry. | No |
| `OPEN` | Actively processing response steps. | No |
| `ACKNOWLEDGED` | A recipient owns an active resolution window. | No |
| `RESOLVED` | The current recipient resolved the issue within the resolution window. | Yes |
| `COMPLETED` | The run ended after acknowledgement or after all steps were exhausted. | Yes |
| `CANCELLED` | A scheduled run was cancelled before it started. | Yes |
| `START_FAILED` | All attempts to start a scheduled run failed. | Yes |

### Allowed transitions

```text
IDLE       -> OPEN                 Start escalation
IDLE       -> SCHEDULED            Schedule later
SCHEDULED  -> OPEN                 Due start or authenticated Start now
SCHEDULED  -> SCHEDULED            Reschedule or an allowed retry
SCHEDULED  -> CANCELLED            Cancel before start
SCHEDULED  -> START_FAILED         Start retries exhausted
OPEN       -> ACKNOWLEDGED          Acknowledgement with resolution enabled
OPEN       -> COMPLETED             Acknowledgement without resolution, or exhaustion
ACKNOWLEDGED -> RESOLVED            Timely resolution
ACKNOWLEDGED -> OPEN                Resolution timeout with a next step
ACKNOWLEDGED -> COMPLETED           Resolution timeout on the final step
```

The server owns the state machine. There is no supported generic status-update
operation. The first valid database transition that commits wins; a stale
request returns a conflict and a stale timer callback becomes a no-op. Terminal
runs remain visible for history and cannot be reopened in v1.

### Step outcomes

Each saved response step has one of these statuses:

`PENDING` → not yet due; `SCHEDULED` → waiting for its due time; `PAUSED` →
held during an acknowledgement resolution window; `SENDING` → delivery in
progress; `SENT` → SMTP accepted the message; `FAILED` → delivery failed after
the applicable retry behavior; `SKIPPED` → no longer eligible after a terminal
decision.

`SENT` means that the SMTP server accepted the email. It does not prove that
the message reached the recipient's inbox.

### Status and sources

Lifecycle behavior, concurrency rules, API error mapping, and audit boundaries
are defined in the [launch checklist](product-launch-readiness.md#2-incident-lifecycle),
the [resolution timeout plan](resolution-timeout-implementation-plan.md), and
the [escalation service](../src/main/java/com/alertops/flow_execution_engine/service/EscalationService.java).

## F-05 — Start, one-time scheduling, and recurring runs

### Start choices

- **Start immediately** creates the run and starts the same workflow used by a
  scheduled run when it becomes due.
- **Schedule for later** requires a future local date/time and an IANA timezone,
  such as `Asia/Kolkata`.
- **Start now** starts a scheduled run early and sends the first email immediately.
  The first step's configured acknowledgement wait begins after delivery.

ReplyTrail stores the resolved UTC instant and the submitted timezone. A
scheduled run can be rescheduled or cancelled only while it remains
`SCHEDULED`. Those operations race safely with a due start because the database
decides which transition commits first.

### Recurring runs

Daily and weekly recurrence is supported in the current local implementation.
The original escalation stores the recurrence settings. Each calendar
occurrence creates an independent child run with a different ID; child runs do
not repeat themselves, and unresolved earlier runs do not block later ones.
Stopping recurrence prevents future children while preserving existing runs.
After downtime, recovery creates only the latest missed occurrence. Monthly,
yearly, custom-interval, preview, and recurrence-editing features are outside
the current minimum scope.

### Start failure behavior

If every scheduled-start attempt fails, the run becomes `START_FAILED`. ReplyTrail
persists a pending notification obligation for the scheduling user and the team
owner or administrators. Startup recovery can deliver an obligation that was
still pending when the application stopped.

### Status and sources

The scheduling and recurrence decisions are recorded in the
[launch checklist](product-launch-readiness.md#1-scheduled-escalation-start),
the [recurring escalation plan](recurring-escalation-implementation-plan.md),
and the [scheduler](../src/main/java/com/alertops/flow_execution_engine/service/EscalationStartScheduler.java).
Local implementation is complete; Oracle capacity, PostgreSQL integration, and
deployed recovery remain open.

## F-06 — Email acknowledgement and resolution

### Purpose

Let the current recipient respond from an email without requiring a full login
and, when the path enables it, keep ownership open until the issue is resolved.

### How it works

1. ReplyTrail sends an email containing a recipient-scoped action link.
2. Opening the link shows a read-only preview. Opening a link does not change state.
3. The recipient confirms acknowledgement explicitly.
4. With resolution timeout disabled, acknowledgement is terminal for the run and later unsent steps are skipped.
5. With resolution timeout enabled, acknowledgement changes the run to
   `ACKNOWLEDGED` and displays the saved resolution deadline.
6. The current recipient or an authorized team member resolves before the
   deadline. Resolution changes the run to `RESOLVED` and skips later steps.
7. If the deadline expires first, the next step becomes due immediately. If no
   next step exists, the run completes as exhausted.

### Security and timing rules

Only the intended recipient's valid, unexpired token can confirm an email
action. Only its hash is stored. The backend rechecks recipient ownership,
expected step, current state, and deadline under the run lock. Repeating an
already accepted action returns the saved result without restarting a timer.

### Status and sources

See the [acknowledgement controller](../src/main/java/com/alertops/flow_execution_engine/controller/EscalationAcknowledgementController.java),
[resolution controller](../src/main/java/com/alertops/flow_execution_engine/controller/EscalationResolutionController.java),
and [launch requirement 3](product-launch-readiness.md#3-resolution-timeout-after-acknowledgement).

## F-07 — Escalate now

### Purpose

Allow an eligible person to make the next response step due immediately when
waiting longer is not useful.

### Who can use it

- An authenticated member of the selected team can use it while the run is `OPEN` or `ACKNOWLEDGED`.
- The current recipient can use the recipient-scoped email action while their
  acknowledgement or resolution window is still open.

### How it works

The user first sees a read-only preview of the next recipient, then explicitly
confirms. ReplyTrail advances the next eligible unsent step immediately. It
does not resend the previous email, skip unfinished steps, or let an old link
advance a later step after the run has already moved on.

There is no action when the run is terminal or no eligible next step exists.
The action differs from **Start now**: Start now begins a scheduled run, while
Escalate now advances an already open or acknowledged run.

### Status and sources

The behavior is defined in [launch requirement 4](product-launch-readiness.md#4-escalate-now)
and implemented by the [manual action controller](../src/main/java/com/alertops/flow_execution_engine/controller/EscalationManualActionController.java).

## F-08 — Webhook intake

### Purpose

Let an external monitoring or business system create a task and start its
configured response path without a signed-in browser session.

### Configuration workflow

1. An owner or administrator creates a webhook and chooses its default response path.
2. ReplyTrail shows the secret only when the webhook is created or its secret is rotated.
3. Disable the webhook or rotate its secret if it should no longer accept traffic.

### Event workflow

Send a JSON object to `POST /api/v1/webhooks/{webhookId}/events` with the
`X-AlertOps-Webhook-Secret` header. A new event creates one task and one run.
The event can include `flowId` to select another path in the same team;
otherwise the configured default path is used.

Required fields are `eventId`, `taskName`, `description`, and `source`.
Optional fields are `priority`, `category`, and `referenceUrl`. Other JSON
fields are retained as event context.

### Idempotency and limits

- A new event returns HTTP `202` and includes the created task and run IDs.
- Repeating the same `eventId` with the same payload returns HTTP `200` and the original records.
- Reusing an `eventId` with different data returns HTTP `409` and creates nothing new.
- The server stores only a hash of the webhook secret.
- The default serialized payload limit is 64 KiB and the default rate limit is 120 requests per webhook per minute.
- Redis shares the rate limit across application instances when available; the fallback counter is per instance.

### Status and sources

See the [webhook plan](webhook-escalation-implementation-plan.md),
[webhook configuration controller](../src/main/java/com/alertops/webhook/controller/WebhookConfigurationController.java),
[event controller](../src/main/java/com/alertops/webhook/controller/WebhookEventController.java),
and the [README request example](../README.md#trigger-a-task-through-a-webhook).

## F-09 — Activity history

### Purpose

Show what happened to a run in plain language after it starts, finishes, or
encounters a failure.

### What is recorded

The saved timeline includes creation, scheduling, rescheduling, start, early
start, cancellation, notification acceptance, notification failure and retry,
acknowledgement, resolution deadline, resolution timeout, Escalate now,
resolution, exhaustion, completion, and scheduled-start failure.

Each visible event includes its time, actor or system source, and relevant step
or recipient context. Repeated delivery failures are grouped in the default
view with expandable attempt details. Timer housekeeping, secrets, raw tokens,
and stack traces are not exposed as user history.

History is append-only and remains available for terminal runs. Viewing history
does not change the run lifecycle.

### Status and sources

The [history controller](../src/main/java/com/alertops/flow_execution_engine/controller/EscalationHistoryController.java)
and [generic audit service](../src/main/java/com/alertops/audit/service/AuditService.java)
implement the local behavior. The [timeline requirement](product-launch-readiness.md#5-incident-activity-timeline)
defines the event and safety rules.

## F-10 — Recovery and duplicate safety

### Durable and temporary responsibilities

- **PostgreSQL** is the source of truth for tasks, paths, runs, steps, statuses,
  timestamps, audit events, schedules, and pending notification obligations.
- **In-memory timers** are wake-up handles only. A callback reloads current state
  before acting, so cancellation, replacement, restart recovery, and duplicate
  callbacks are safe.
- **RabbitMQ** carries ready work and consumer retries. It is not the source of
  truth for delayed workflow timing.
- **Redis** stores short-lived workflow intents and shares webhook rate limiting.

### Reliability behavior

Lifecycle transitions, audit events, and durable publication obligations are
committed before timers or message publication are changed. Duplicate messages,
stale callbacks, repeated actions, and restart recovery recheck saved state and
must not create a second winning transition. Delivery may be at least once: an
SMTP-accepted message can be sent again if the application stops before it
saves the `SENT` result.

### Status and sources

See the [in-memory timer plan](in-memory-timer-implementation-plan.md), the
[launch reliability checks](product-launch-readiness.md#make-escalation-execution-safe-under-duplicate-messages-and-restarts),
and the [scheduler implementation](../src/main/java/com/alertops/flow_execution_engine/service/EscalationStartScheduler.java).
Infrastructure integration and deployed restart checks remain open.

## API route map

The UI uses these API areas. Routes below are stable technical entry points;
the user-facing feature names above are the preferred product vocabulary.

| API area | Base route | Main responsibility |
| --- | --- | --- |
| Authentication | `/api/v1/auth` | Registration, verification, and login. |
| Teams | `/api/v1/team` | Create, select, list, invite, and inspect team members. |
| Tasks | `/api/v1/task` | Create, list, read, edit, and delete task context. |
| Response paths | `/api/v1/flow` | Create paths, manage steps, timing, and order. |
| Escalation runs | `/api/v1/escalation` | Create, list, start, schedule, reschedule, cancel, repeat, and inspect runs. |
| Acknowledgement | `/api/v1/escalation/acknowledgement` | Preview and confirm recipient acknowledgement. |
| Resolution | `/api/v1/escalation/{id}/resolve` and `/api/v1/escalation/resolution/confirm` | Resolve as a team member or recipient. |
| Escalate now | `/api/v1/escalation/*/escalate-now` | Preview and confirm the next-step action. |
| Activity history | `/api/v1/escalation/{id}/history` | Read same-team paginated history. |
| Webhook configuration | `/api/v1/team/webhooks` | Create, list, rotate, disable, and inspect webhook events. |
| Webhook intake | `/api/v1/webhooks/{id}/events` | Accept authenticated external events. |

For API questions, read the relevant controller and service together with the
linked feature plan. Do not infer permissions, lifecycle transitions, or
idempotency behavior from route names alone.

## Current boundaries and deferred work

The current product sends SMTP email. SMS, phone calls, push notifications, and
native Slack or Teams delivery are not implemented. Response paths use named
team members; rotating on-call calendars are not implemented. **Send test
escalation** is deferred until initial users provide feedback; its behavior is
not yet agreed. Production hosting, verified sender/domain setup, and deployed
end-to-end validation are still open in the [launch checklist](product-launch-readiness.md#release-check).

## Canonical references

- [Product launch checklist](product-launch-readiness.md) — agreed behavior, release status, and verification gaps.
- [Webhook escalation plan](webhook-escalation-implementation-plan.md) — webhook contract, task/run creation, and idempotency.
- [Resolution timeout plan](resolution-timeout-implementation-plan.md) — lifecycle, acknowledgement, resolution, and manual action rules.
- [Recurring escalation plan](recurring-escalation-implementation-plan.md) — daily/weekly recurrence and restart behavior.
- [UI implementation plan](ui-implementation-plan.md) — browser routes and frontend/backend deployment boundary.
- [Deployment guide](deployment/README.md) — hosting, infrastructure, and operational readiness.
- [Feature backlog](feature-backlog.md) — deferred feature decisions.
