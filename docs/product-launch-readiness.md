# AlertOps: critical path to product release

Reassessed: 2026-09-27. This list is based on the current source code. It contains only critical bugs to fix first and the two product features selected for this release. Runtime behavior has not been verified in this review.

## Fix these bugs first, in order

### 1. Close public account deletion

- [x] Disable `DELETE /api/v1/auth/user` until safe self-service deletion semantics are defined. The old endpoint accepted a caller-supplied email or ID under a publicly permitted auth route. The route is denied in security configuration, the handler and email-delete path are removed, and `UserRepository` now uses UUID IDs to match the entity.
- [x] Add focused API tests confirming anonymous and authenticated requests are denied and do not call the user service.
- **Done when:** the unsafe route stays unavailable until safe self-service deletion semantics are defined; focused API tests cover the denial.
- **Evidence:** [auth controller](../src/main/java/com/alertops/auth/controller/UserController.java), [security rules](../src/main/java/com/alertops/security/SecurityConfig.java), [user repository](../src/main/java/com/alertops/auth/repository/UserRepository.java).

### 2. Make escalation execution safe under duplicate messages and restarts

- [x] Reload the saved step for each queue message and atomically claim an eligible delivery. Ignore duplicate deliveries and messages for an older send attempt. Unit tests cover these consumer decisions; database concurrency integration coverage is still needed.
- [x] When an unexpected processing error occurs, look for the next `PENDING` step before deciding the escalation is exhausted.
- [ ] Persist when a step is due so recovery does not restart its full wait. Make the database-to-queue handoff recoverable and prevent consumer crashes from losing or duplicating scheduled work.
- [x] Claim a start by changing the escalation from `IDLE` to `RUNNING` with a conditional database update, so only one simultaneous request can create step states.
- [ ] Add database integration tests proving that duplicate starts create one state set and duplicate deliveries cannot advance a step twice.
- **Done when:** duplicate messages, repeated starts, consumer crashes, and application restarts do not skip or advance a step twice; the remaining SMTP uncertainty after a send succeeds but before the database records it is explicitly handled or documented. Focused integration tests exercise these cases.
- **Evidence:** [consumer](../src/main/java/com/alertops/messaging/MessageConsumer.java), [reconciler](../src/main/java/com/alertops/messaging/ReconcilerService.java), [publisher](../src/main/java/com/alertops/messaging/MessagePublisher.java), [start use case](../src/main/java/com/alertops/flow_execution_engine/application/StartFlowExecutionUseCase.java).

### 3. Verify the email address used to accept an invitation

- [ ] Registration currently allows a user to claim any email address, while invitation acceptance trusts the email on the signed-in account. Require proof of control of that address before the invite grants team membership.
- **Done when:** an unverified account cannot accept an invite for its claimed address; verified accounts can accept once; tests cover wrong account, expired link, and repeat acceptance.
- **Evidence:** [registration](../src/main/java/com/alertops/auth/service/UserService.java), [invite acceptance](../src/main/java/com/alertops/team/service/TeamInvitationService.java).

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

No services were started, tests run, or emails sent for this document update.
