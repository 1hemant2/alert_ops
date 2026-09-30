# AlertOps: critical path to product release

Reassessed: 2026-09-30. This list contains critical bugs to fix first and the two product features selected for this release. Scheduling has focused PostgreSQL integration coverage with a simulated broker; the deployed end-to-end release journey remains unverified.

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
- [x] Claim a start by changing the escalation from `IDLE` to `RUNNING` with a conditional database update, so only one simultaneous request can create step states.
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

- [ ] The task form accepts 1,000 characters, but the initial database migration gives `tasks.description` and `flow_execution_state.task_details` only 255 characters. Add a new Flyway migration and matching API limits so a valid form or webhook payload cannot fail later when a run starts.
- **Done when:** the allowed maximum length survives task creation, run start, saved state reads, and email creation.
- **Evidence:** [task form](../ui/src/features/tasks/TasksPage.tsx), [initial schema](../src/main/resources/db/migration/V1__create_initial_schema.sql).

## Then build these two product features

### Feature 1: Acknowledge an escalation from email

- [ ] Put an **Acknowledge** button in every escalation email. It opens a confirmation page for that specific run and recipient. The confirmation action records acknowledgement and stops later steps. The link needs a scoped, expiring token and must be safe to use more than once.
- [ ] Make the confirmation a deliberate `POST`; opening the link with `GET` must not acknowledge anything, since mail scanners can open links automatically. The worker must recheck the saved run status before a later send. Show the acknowledged status, person, and time in the run detail UI.
- **Done when:** acknowledgement before the next step prevents that step from sending; an expired or invalid link cannot acknowledge; repeated clicks return the same result; a completed run cannot be changed incorrectly. Cover the race between acknowledgement and an in-flight send.
- **Evidence of current gap:** [run API](../src/main/java/com/alertops/flow_execution_engine/controller/EscalationController.java), [email template](../src/main/java/com/alertops/messaging/Notification.java).

### Feature 2: Create and start an escalation through a webhook

- [ ] Let a team configure a webhook for a selected escalation path. A valid incoming event creates the task context and starts a run automatically. Give each webhook a revocable secret, validate payloads and size, limit abuse, and keep the secret out of responses after creation.
- [ ] Require an event ID or idempotency key so retries return the existing run instead of starting another one. Return the run ID and enough error detail for the sender to retry safely. Add UI to create, view, rotate, and disable the webhook configuration.
- **Done when:** a signed or secret-authenticated request starts one run on the configured team and path; retrying the same event does not create another; invalid credentials, foreign team IDs, and malformed payloads are rejected; the created run appears in the UI and can be acknowledged from its email.
- **Evidence of current gap:** [run API](../src/main/java/com/alertops/flow_execution_engine/controller/EscalationController.java), [current UI API](../ui/src/api/escalations.ts).

## Release check

- [ ] All four critical fixes above are complete.
- [ ] Both product features work together in a deployed end-to-end run: webhook event → one task and run → email → recipient acknowledgement → no later step sent.
- [ ] The same journey works after a restart and a duplicate webhook or queue delivery. The UI shows saved server state. `SENT` continues to mean SMTP acceptance unless actual delivery tracking is added.
