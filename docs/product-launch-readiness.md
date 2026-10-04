# AlertOps: critical path to product release

Reassessed: 2026-10-02. This list contains critical bugs to fix first, the two product features selected for this release, and the in-progress review of the Alert Escalation v1 requirements. Scheduling has focused PostgreSQL integration coverage with a simulated broker; the deployed end-to-end release journey remains unverified.

## Alert Escalation v1 requirements review

Review these requirements in order. A checked **Requirements agreed** box means the product behaviour and acceptance criteria are settled; it does not mean implementation is complete. Implementation progress is tracked separately.

### 1. Scheduled escalation start

- [x] **Requirements agreed**
- [ ] **Implementation complete locally — durable `START_FAILED` notification pending**
- [ ] **Implemented and verified in PostgreSQL/deployed flow**
- **Requested behaviour:** When creating an escalation, choose **Start immediately** or **Schedule for later**. A scheduled escalation stores a date, time, and timezone, remains `SCHEDULED` until its start time, and then enters the same workflow as an immediate escalation. Before it starts, it can be rescheduled or cancelled. Only one-time schedules are in v1. If all start attempts fail, the responsible users must be notified; that notification must survive an application crash or restart.
- **Current state:** The core local scheduling implementation is complete. The escalation stores a one-time UTC start instant and IANA timezone, exposes create/reschedule/cancel actions, schedules starts in memory from the durable database row, recovers scheduled rows on startup, and atomically claims a scheduled start before creating response steps. Exhausted retries produce `START_FAILED`, but a durable user notification has not yet been implemented. PostgreSQL integration and deployed end-to-end verification also remain pending.
- **Agreed product decisions:**
  - Put the schedule on the escalation run, not on the reusable task or escalation path.
  - Accept a local date/time plus an IANA timezone such as `Asia/Kolkata`; persist both the resolved UTC instant and the submitted timezone for accurate display and rescheduling.
  - Reject a scheduled time that is not in the future. Default the UI timezone to the browser timezone and allow the user to change it.
  - Allow reschedule and cancel only while the run is still `SCHEDULED`, enforced atomically so a start cannot race with either action.
  - Treat **Start immediately** as create-and-start, using the same start use case invoked when a scheduled time becomes due.
  - Add a terminal `CANCELLED` state for a cancelled scheduled run, consistent with the incident lifecycle in Requirement 2.
  - When the retry limit is exhausted, change the escalation to `START_FAILED` and atomically persist a pending failure-notification obligation in PostgreSQL. Do not rely on the scheduler callback or an in-memory queue to remember that notification.
  - Notify the user who scheduled the escalation and the team owner or administrators. If the application stops after recording `START_FAILED` but before notification delivery, startup recovery must find the pending notification and send it when the application is available again.
  - Record successful delivery so normal recovery does not resend it. Prefer at-least-once delivery over losing the notification; the implementation must tolerate the narrow possibility of a duplicate if the application stops after the provider accepts the notification but before delivery is recorded.
- **Verification needed:** API validation and team isolation; timezone and daylight-saving conversion; start-at-time behaviour; restart recovery; duplicate trigger safety; reschedule/start and cancel/start races; UI state and actions; proof that recurring or cron schedules are not accepted; exhausted retries persist `START_FAILED` and pending notification together; notification-delivery failure remains recoverable; application restart sends pending notifications; repeated recovery does not create avoidable duplicates; the correct user and team administrators are notified.
- **Evidence:** [schedule migration](../src/main/resources/db/migration/V6__add_escalation_scheduling.sql), [scheduled-start scheduler](../src/main/java/com/alertops/flow_execution_engine/service/EscalationStartScheduler.java), [escalation service](../src/main/java/com/alertops/flow_execution_engine/service/EscalationService.java), [start claims](../src/main/java/com/alertops/flow_execution_engine/repository/EscalationRepository.java), [creation UI](../ui/src/features/escalations/EscalationsPage.tsx), [schedule controls](../ui/src/features/escalations/EscalationDetailPage.tsx), [scheduling tests](../src/test/java/com/alertops/flow_execution_engine/service/EscalationServiceSchedulingTest.java).
- **Local verification:** Existing scheduling tests and `npm run build` pass. Durable failure notification and its crash/restart tests are still required. PostgreSQL/Testcontainers integration coverage is currently skipped in this environment, so the final readiness box stays open.

### 2. Incident lifecycle

- [x] **Requirements agreed**
- [ ] **Implementation complete locally**
- [ ] **Implemented and verified in PostgreSQL/deployed flow**
- **Requested behaviour:** Every escalation follows one explicit lifecycle. Status changes happen only through named operations, invalid transitions are rejected consistently, simultaneous actions cannot both win, and terminal history remains available for investigation.
- **Agreed statuses:**
  - `IDLE`: created but not started or scheduled.
  - `SCHEDULED`: waiting for its configured start time or an eligible start retry.
  - `OPEN`: actively processing response steps.
  - `COMPLETED`: terminal; `ACKNOWLEDGED` or `EXHAUSTED` records why it completed.
  - `CANCELLED`: terminal; a scheduled escalation was cancelled before starting.
  - `START_FAILED`: terminal for v1; all scheduled-start attempts were exhausted.
- **Agreed transitions:**
  - `IDLE` may become `OPEN` through **Start now** or `SCHEDULED` through **Schedule later**.
  - `SCHEDULED` may remain `SCHEDULED` when rescheduled or when another start retry is allowed. It may become `OPEN` when its due start succeeds, `CANCELLED` when cancellation wins first, or `START_FAILED` when all retries are exhausted.
  - `OPEN` becomes `COMPLETED` when the intended recipient acknowledges it or when every response step is exhausted.
  - `COMPLETED`, `CANCELLED`, and `START_FAILED` do not transition again in v1. A manual retry for `START_FAILED` is post-release work.
- **Agreed action behaviour:**
  - A repeated acknowledgement by the same recipient returns the saved result. Another recipient cannot overwrite it, and an `EXHAUSTED` escalation cannot be acknowledged later.
  - Repeated cancellation is idempotent and returns the existing cancelled result.
  - Scheduled timer callbacks reload the database row and stop when the escalation is no longer `SCHEDULED`. Terminal runs are read-only and remain visible for history.
  - All authenticated members of the selected team may create, start, schedule, reschedule, cancel, and view escalations in v1. Email acknowledgement requires the valid recipient token; automatic transitions are backend-only; another team's escalation is never exposed.
- **Concurrency rule:** The first valid database transition that commits wins. Conditional updates or row locks enforce the decision; a stale request returns a conflict and an internal stale callback becomes a no-op. In-memory state never decides lifecycle ownership.
- **API behaviour:** Invalid input returns `400`, missing authentication returns `401`, missing or another-team escalation returns `404`, and an invalid transition returns `409`. Duplicate starts return `409`; repeated cancellation and same-recipient acknowledgement are idempotent successes.
- **Minimum v1 audit data:** Persist the scheduling user needed for failure notification, the latest scheduling actor after reschedule, start time, cancellation actor and time, start-failure time and safe reason, retry count, and the existing acknowledgement actor and time. A complete event timeline remains Requirement 5.
- **Audit boundary:** Lifecycle services decide and persist valid state transitions; `AuditService` persists append-only events for any entity. Audit storage is separate from domain models and uses an entity type/id, so adding event consumers or auditing another entity does not require changing the audited entity's model or repository. Audit writes join the same transaction as the lifecycle transition.
- **Implementation checklist:**
  - [x] Remove the generic `updateEscalationStatus` method so callers cannot assign arbitrary status and resolution strings.
  - [x] Centralize the allowed status and resolution values and prevent arbitrary values from being persisted.
  - [x] Add the minimum lifecycle actor, timestamp, and safe failure fields, including the scheduling user required for notification.
  - [ ] Make repeated cancellation idempotent and return the saved cancelled result.
  - [x] Map validation, authentication, team isolation, and transition conflicts to the agreed HTTP responses through typed escalation errors, global bad-request handling, and the security authentication entry point.
  - [ ] Add the durable `START_FAILED` notification and UI described in Requirement 1.
  - [ ] Add focused transition, authorization, stale-callback, idempotency, and concurrency tests, including start/schedule, start/cancel, start/reschedule, acknowledgement/send, and failure/cancellation races.
- **Done when:** No generic status mutation path remains; every transition follows the agreed state graph and team boundary; concurrent actions have one winner; terminal states cannot be reopened; required audit data is saved; invalid actions return the agreed API response; and focused unit plus PostgreSQL integration tests prove the lifecycle.
- **Evidence:** [escalation service](../src/main/java/com/alertops/flow_execution_engine/service/EscalationService.java), [generic audit service](../src/main/java/com/alertops/audit/service/AuditService.java), [audit event model](../src/main/java/com/alertops/audit/model/AuditEventEntity.java), [audit event repository](../src/main/java/com/alertops/audit/repository/AuditEventRepository.java), [conditional transition queries](../src/main/java/com/alertops/flow_execution_engine/repository/EscalationRepository.java), [lifecycle database constraints](../src/main/resources/db/migration/V8__constrain_escalation_lifecycle_values.sql), [audit migration](../src/main/resources/db/migration/V9__create_audit_events.sql), [scheduled-start retry service](../src/main/java/com/alertops/flow_execution_engine/service/EscalationStartRetryService.java), and [lifecycle tests](../src/test/java/com/alertops/flow_execution_engine/service/EscalationServiceSchedulingTest.java).

### Remaining requirements

- [ ] 3. Resolution timeout after acknowledgement — not yet reviewed
- [ ] 4. Escalate now — not yet reviewed
- [ ] 5. Incident activity timeline — not yet reviewed
- [ ] 6. Send test escalation — not yet reviewed

## Fix these bugs first, in order

### 1. Close public account deletion

- [x] Disable `DELETE /api/v1/auth/user` until safe self-service deletion semantics are defined. The old endpoint accepted a caller-supplied email or ID under a publicly permitted auth route. The route is denied in security configuration, the handler and email-delete path are removed, and `UserRepository` now uses UUID IDs to match the entity.
- [x] Add focused API tests confirming anonymous and authenticated requests are denied and do not call the user service.
- **Done when:** the unsafe route stays unavailable until safe self-service deletion semantics are defined; focused API tests cover the denial.
- **Evidence:** [auth controller](../src/main/java/com/alertops/auth/controller/UserController.java), [security rules](../src/main/java/com/alertops/security/SecurityConfig.java), [user repository](../src/main/java/com/alertops/auth/repository/UserRepository.java).

### 2. Make escalation execution safe under duplicate messages and restarts

- [x] Reload the saved step for each queue message and atomically claim an eligible delivery. Ignore duplicate deliveries and messages for an older send attempt. Unit tests cover these consumer decisions, and PostgreSQL integration tests prove concurrent deliveries can claim a step only once.
- [x] When an unexpected processing error occurs, look for the next `PENDING` step before deciding the escalation is exhausted.
- [x] Persist each step's due time and pending-publication flag on the execution-state row. Publish immediately after the scheduling transaction commits, confirm RabbitMQ acceptance, and retry failed publications while the backend remains running. Startup recovery republishes outstanding steps with only the remaining delay; there is no separate outbox table or recurring idle poll. Existing active steps without a due time are recovered from their saved timestamps.
- [x] Claim a start by changing the escalation from `IDLE` to `OPEN` with a conditional database update, so only one simultaneous request can create step states.
- [x] Add database integration tests proving that duplicate starts create one state set. Concurrent delivery claims are already covered in PostgreSQL.
- **Done when:** duplicate messages, repeated starts, consumer crashes, and application restarts do not skip or advance a step twice; the remaining SMTP uncertainty after a send succeeds but before the database records it is explicitly handled or documented. Focused integration tests exercise these cases.
- **Evidence:** [consumer](../src/main/java/com/alertops/messaging/MessageConsumer.java), [reconciler](../src/main/java/com/alertops/messaging/ReconcilerService.java), [publisher](../src/main/java/com/alertops/messaging/MessagePublisher.java), [start use case](../src/main/java/com/alertops/flow_execution_engine/application/StartFlowExecutionUseCase.java).

The scheduling integration tests cover publication after commit, no publication on rollback, startup recovery, failed-publication retries, preserved due times, and late confirmations for old attempts. Recovery checks run on startup or after a publication failure; a healthy idle backend does not poll the database. SMTP remains outside the database transaction: if the mail server accepts an email and the backend stops before `SENT` commits, redelivery can send that email again. The scheduling fix does not guarantee exactly-once email delivery.

### 3. Verify the email address used to accept an invitation

- [x] Registration creates an unverified account and sends a one-time, expiring verification link. Login rejects unverified accounts before issuing a JWT, and the UI routes them to the verification screen.
- [x] Invitation acceptance checks the current database user, requires a verified address, and compares that address with the invite before creating membership.
- **Done when:** an unverified account cannot accept an invite for its claimed address; verified accounts can accept once; tests cover wrong account, expired link, repeat acceptance, and the verification state change.
- **Evidence:** [verification service](../src/main/java/com/alertops/auth/service/EmailVerificationService.java), [registration](../src/main/java/com/alertops/auth/service/UserService.java), [security filter](../src/main/java/com/alertops/security/JwtAuthenticationFilter.java), [invite acceptance](../src/main/java/com/alertops/team/service/TeamInvitationService.java), [migration](../src/main/resources/db/migration/V2__add_email_verification.sql).

### 4. Align task description limits with the database

- [x] The task form accepts up to 1,000 characters, and migration V3 changes both `tasks.description` and `flow_execution_state.task_details` to PostgreSQL `TEXT`. The saved task context can therefore survive task creation, run start, and email creation without the original 255-character database limit.
- **Done when:** the allowed maximum length survives task creation, run start, saved state reads, and email creation.
- **Evidence:** [task form](../ui/src/features/tasks/TasksPage.tsx), [task entity](../src/main/java/com/alertops/task/model/Task.java), [context migration](../src/main/resources/db/migration/V3__use_text_for_task_context.sql).

## Then build these two product features

### Feature 1: Acknowledge an escalation from email

- [x] Put an **Acknowledge** button in every escalation email. It opens a confirmation page for that specific run and recipient. The confirmation action records acknowledgement and stops later steps. The link uses a recipient-scoped, expiring token; only its hash is stored.
- [x] Make confirmation a deliberate `POST`; the email link opens a page and a read-only preview first. The worker and acknowledgement action lock the run row while making their state changes. If a send is already in progress, it finishes before acknowledgement is accepted; ready later steps then see the completed run and are ignored. The run detail shows who acknowledged and when.
- [x] Focused tests cover valid, expired, repeated, invalid-state, and wrong-recipient acknowledgement cases; anonymous API access is limited to the token endpoints and `GET` cannot confirm. A PostgreSQL integration case exercises acknowledgement while a send is in flight.
- **Done when:** acknowledgement before the next step prevents that step from sending; expired or invalid links cannot acknowledge; repeated clicks return the saved result; completed runs cannot be changed incorrectly; acknowledgement and in-flight sends are serialized.
- **Evidence:** [acknowledgement API](../src/main/java/com/alertops/flow_execution_engine/controller/EscalationAcknowledgementController.java), [acknowledgement service](../src/main/java/com/alertops/flow_execution_engine/service/EscalationAcknowledgementService.java), [email template](../src/main/java/com/alertops/messaging/Notification.java), [confirmation page](../ui/src/features/escalations/AcknowledgeEscalationPage.tsx), [database migration](../src/main/resources/db/migration/V4__add_escalation_acknowledgement.sql).

### Feature 2: Create and start an escalation through a webhook

- [ ] Add source and optional priority, category, and reference URL to tasks, including manual create/edit, detail views, and email. Use wording that fits alerts, onboarding questions, and other requests. Default manual tasks to source “Manual”; show task source in every email subject and body.
- [ ] Let a team configure a webhook with a default response path. A request may specify a same-team `flowId` to use another path; without it, use the default. Give each webhook a revocable secret, validate payloads and size, limit abuse, and keep the secret out of responses after creation.
- [ ] Require `eventId`, `taskName`, `description`, and `source` in the request body before creating anything. Save the complete JSON event with the created task ID and run ID; show the event and its task link in the UI. Retries with the same event ID return the existing task and run. Add UI to create, view, rotate, and disable the webhook configuration.
- **Done when:** a valid request starts one task and run on the configured team and selected path, with the full event saved and linked to both; a request missing required task fields or naming a foreign flow saves nothing; retries do not create another run; invalid credentials and malformed payloads are rejected; the run can be acknowledged from its email.
- **Evidence of current gap:** [run API](../src/main/java/com/alertops/flow_execution_engine/controller/EscalationController.java), [current UI API](../ui/src/api/escalations.ts).

## Release check

- [ ] All four critical fixes above are complete.
- [ ] A scheduled escalation that exhausts its start retries notifies the responsible user and team administrators. The pending notification survives an application crash and is recovered after restart.
- [ ] Both product features work together in a deployed end-to-end run: webhook event → one task and run → email → recipient acknowledgement → no later step sent.
- [ ] The same journey works after a restart and a duplicate webhook or queue delivery. The UI shows saved server state. `SENT` continues to mean SMTP acceptance unless actual delivery tracking is added.
