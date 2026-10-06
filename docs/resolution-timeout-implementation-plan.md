# Escalation lifecycle: resolution timeout, manual actions, and activity timeline

Created: 2026-10-05
Status: Planning — lifecycle timing, manual actions, timeline, response deadlines, email source eligibility, existing team permissions, and final storage details agreed; implementation pending.
Release priorities: [scheduled starts](product-launch-readiness.md#1-scheduled-escalation-start),
[resolution timeout](product-launch-readiness.md#3-resolution-timeout-after-acknowledgement), and
[Escalate now](product-launch-readiness.md#4-escalate-now), and
[activity timeline](product-launch-readiness.md#5-incident-activity-timeline).

## Agreed manual actions

**Start now begins the workflow.** It starts an `IDLE` run as today and also lets a team member start a `SCHEDULED` run before its saved start time. The first step keeps its configured delay. Reuse the existing start API and start use case; no extra public early-start endpoint is needed.

**Escalate now removes the next notification's wait.** In `OPEN`, make the next eligible unsent step due immediately, whether it is scheduled or still pending. In `ACKNOWLEDGED`, end the resolution waiting period, return to `OPEN`, and make the next eligible paused step due immediately. Ask for confirmation in the acknowledged case because someone is already working on the issue. Advance in saved step order, without jumping over unfinished steps or resending a sent notification. If there is no next eligible step, the action is unavailable and makes no state change.

Start now and the signed-in Escalate now action remain restricted to authenticated members of the selected team under the existing v1 permission rule. Email Escalate now additionally allows the intended notification recipient to act using a valid recipient-scoped token without signing in, as described below. Neither action reopens `COMPLETED`, `RESOLVED`, `CANCELLED`, or `START_FAILED` runs. Record the actor and the affected run/step through the generic audit service.

| Starting situation | Action | Result |
| --- | --- | --- |
| Run is `IDLE` | Start now | `OPEN`; schedule first step with its usual delay |
| Run is `SCHEDULED` | Start now | `OPEN` early; schedule first step with its usual delay; cancel start timer after commit |
| Run is `OPEN`, next step is waiting | Escalate now | Run stays `OPEN`; next eligible step becomes due immediately |
| Run is `ACKNOWLEDGED`, next step is paused | Escalate now | Run becomes `OPEN`; end active resolution wait; next step becomes due immediately |
| Run is terminal or no next step exists | Escalate now | Unavailable; no state change |

### How Start now will work

1. Extend the existing start use case to accept `IDLE` and `SCHEDULED` for a same-team manual request, keeping task/flow validation and the shared step-creation path.
2. Claim the expected database state and create execution steps in one transaction. A manual scheduled-start claim does not require the saved start time to have arrived. Keep that due-time check on automatic timer starts.
3. Save the transition to `OPEN`, clear active scheduled-start retry work, and write actor/audit information in that transaction. A failed start rolls everything back and leaves the scheduled timer available.
4. After commit, cancel the old in-memory start timer. Cancelling a timer does not set the run to `CANCELLED`.
5. If the automatic timer, cancellation, reschedule, or another start wins first, return the existing agreed conflict/idempotency result. A stale timer reloads `OPEN` and does nothing. Startup recovery must not schedule the run again.

### How Escalate now will work

1. Add a named manual-escalation operation and UI control. Bind a request to the expected next execution step so a duplicate/stale click cannot silently act on a later step after the worker advances.
2. Lock/reload the same-team run and its eligible step. Accept only `OPEN` or `ACKNOWLEDGED`; recheck that the target is the next unsent step in order. The server decides eligibility, not the UI.
3. For an acknowledged run, clear active resolution-deadline ownership and change to `OPEN`. Keep earlier acknowledgement history in audit; the already sent step remains `SENT`.
4. Set the target to `SCHEDULED`, save `dueAt = clock.instant()`, and persist its publication obligation and manual-action audit event in the same transaction. Do not call the normal delay-based scheduling method in a way that adds the node delay again.
5. After commit, cancel/replace old wake-ups and publish through the existing durable step-scheduling path. Reuse this immediate-delivery operation for resolution timeout expiry, with distinct manual/system audit reasons.
6. Serialize with acknowledgement, resolution, timeout, and sends already in progress. An in-progress send finishes normally; a stale request cannot resend its target or advance a different step. A stale resolution callback cannot resume a run after manual escalation or a newer acknowledgement.
7. Failed publication and restart recovery use the saved due time and publication obligation; memory owns timer handles only. Later steps retain their ordinary delays.

### Escalate now from email

- Add an **Escalate now** button to escalation emails alongside the acknowledgement action. It opens a confirmation page, with a read-only preview of the run and the next recipient. The email itself cannot promise a current next recipient because it may be opened later.
- The recipient can preview and confirm using a valid token without signing in. Reuse hashed, recipient-scoped token machinery, extending it with explicit permission for manual escalation and the exact source execution-step reference. Response deadlines gate actions, not link viewability. This permission does not authorize Start now or other team-management actions.
- Opening the link or loading/scanning the email never changes state. Require deliberate POST confirmation; do not place the state change on a GET route. In ACKNOWLEDGED, clearly warn that confirming ends the current resolution waiting period.
- Preview returns the current eligible target and state context. Confirmation revalidates that context under the run lock; if the target or acknowledgement context changed, require a refreshed preview rather than silently escalating a different step.
- Before acknowledgement, email Escalate now is allowed only for the current eligible sent step while the run is `OPEN` and the current time is strictly before that step's acknowledgement deadline. After acknowledgement, allow it only for the current acknowledging recipient/step while `ACKNOWLEDGED` and strictly before its resolution deadline. The latter ends the resolution wait early, as already agreed.
- Reject a new action after its response window expires or ownership advances, even if a timer is delayed or the next email has not yet been sent. Reject invalid/foreign tokens. Terminal runs and runs without a next eligible step show the action as unavailable; valid scoped links remain viewable with the reason explained.
- Use the same manual-escalation business operation and database transition as the signed-in UI, with authorization performed at the respective boundary. Record the token's saved recipient email as the actor, without inventing an authenticated user ID. Save the source and target step references with the audit event; keep raw tokens out of logs/audit and persist only their hashes.
- A successful email escalation invalidates that source's permission for new actions as part of the same transition. Duplicate confirmations must never advance multiple steps or produce repeated action events. Return the saved outcome for an already completed identical action where available, or a clear stale/conflict response; never reselect a later target. Step 1's email cannot advance Step 3 after it has already advanced Step 2, including while Step 2's delivery is still pending.
- Recheck source ownership, relevant deadline, and expected target under the run lock on POST, not just during preview. Preserve post-commit publication, durable recovery, and stale resolution/delivery callback checks on this email path too. These email-source restrictions do not introduce a new role/state or replace signed-in same-team authorization.

The agreed activity timeline below will consume these audit events; it introduces no new lifecycle state.

## Agreed incident activity timeline

Show saved event history on the escalation detail page, covering both user actions and automatic events. Present important milestones by default and expandable failure/retry details so users can understand the run without reading technical logs.

| Event group | History to retain |
| --- | --- |
| Run setup | Created, scheduled, rescheduled, started (including early/manual start), cancelled |
| Notification attempts | SMTP accepted, send attempt failed, retry scheduled, retries exhausted |
| Response and resolution | Acknowledged with resolution deadline when enabled, resolution timeout expired, resolved |
| Manual escalation | Escalate now from the signed-in UI or email, actor, source/next step, and whether a resolution wait ended |
| Run completion/failure | Completed by acknowledgement when timeout is disabled, exhausted, start failed |

### Readable presentation

- Show when the event happened, the actor (team user, token recipient email, or automatic system), and the affected step/recipient where applicable. Display saved UTC instants in the viewer's local timezone, consistent with existing screens.
- Use plain messages, such as “Alice acknowledged; resolve by 10:22”, “Alice escalated from email; Bob will be notified”, and “Alice's time to resolve expired; notifying Bob”. The last two mean Bob became due; show SMTP acceptance separately once it actually happens.
- Keep important milestones visible. Group repeated failure/retry events for the same step into a short summary, such as “Email to Alice failed; retried 3 times”, with an expandable chronological attempt list and eventual outcome. Count retries separately from the initial send attempt.
- Never merge retries from different steps or across an intervening acknowledgement/manual action in a way that hides the actual sequence. Grouping is a display choice, not a replacement for individual saved audit rows. Counts/details must remain correct when history is paginated.
- Exclude timer registration, queue housekeeping, stack traces, provider credentials, and raw tokens. Show safe, understandable failure wording.
- Keep current step progress and historical activity distinct: the existing “Execution timeline” is a current-state overview. Label it “Step progress” when adding the history view, so overwritten step fields are not mistaken for past events.
- Provide loading, empty, error/retry, refresh, and load-more states using existing UI components. Terminal runs retain their history. The history view itself is read-only.

### Audit and API design

- Reuse `AuditService` and append-only audit storage. Add specific audit-action enum values for these domain events; no new escalation/step status is needed for the timeline.
- Associate each run event with the escalation entity/id. Include the relevant execution-step ID, recipient, send-attempt/retry number, deadline, action origin, and target step only where needed in safe event details. This lets the run timeline query one event stream without a duplicate event store or dependency on editable flow nodes.
- Capture relevant labels/recipient/context when the event occurs so later flow edits do not rewrite history. Use persisted event facts to render messages; do not reconstruct previous actions from the current escalation status.
- Write the domain change and its audit event in the same transaction. Add event emission at the operation that owns the change, not at both controller and service. Repeated idempotent requests and stale callbacks must not add another successful-action event; rejected actions must not look like committed transitions.
- Reuse recipient-email audit for email actions; never invent a signed-in user ID. Automatic events are labelled as system actions.
- Add a team-scoped read API with stable chronological ordering/tie-breaking and pagination. Verify the current user's membership and run ownership before returning events, treating another team's run as not found. Expose a safe response DTO rather than the raw audit entity/metadata.
- Email action tokens authorize only their intended preview/action, not the full team activity timeline. Preserve the existing audit boundary so other entities can use it independently.
- SMTP acceptance remains the meaning of SENT. A crash between provider acceptance and database commit may still produce duplicate email; the timeline must not claim verified delivery or exactly-once sending.

### Acceptance examples

- An email Escalate now action appears with the recipient email, its source/target step, and time; a later send appears as its own event.
- Three retries can be summarized in one readable row while expansion shows every saved failed attempt/retry and final outcome in order.
- Repeated acknowledgement or duplicate timeout callback adds no duplicate transition event.
- Editing a reusable flow changes neither old event labels nor recipient/deadline facts.
- The same-team viewer can read history; anonymous or foreign-team viewers and email action tokens cannot read the full stream.
- Refresh/load more preserves grouping and chronology. Empty/failed requests have clear UI states. No current lifecycle status is changed by reading the timeline.

## Agreed resolution-timeout behavior

- The reusable flow owns `resolutionTimeoutEnabled`. There is no separate run-level configuration toggle or fallback timeout.
- When disabled, node resolution timeouts are absent (`null`). Acknowledgement retains today's behavior: `OPEN → COMPLETED` with resolution type `ACKNOWLEDGED`, and later notifications stop permanently.
- When enabled, every node has a positive `resolutionTimeout`. Acknowledgement means the recipient is working on the issue: `OPEN → ACKNOWLEDGED`. Resolution completes the incident through `ACKNOWLEDGED → RESOLVED`.
- Each acknowledgement uses the timeout of the exact execution step whose email was acknowledged.
- While acknowledged, the next scheduled step is paused. Later steps remain pending.
- If the issue remains unresolved at the saved deadline and a next step exists, resume `ACKNOWLEDGED → OPEN` and make that step due immediately. Do this whether its original due time was earlier, equal to, or later than the resolution deadline. Do not restart its configured delay or use `max(originalDueAt, resolutionDueAt)`. With no next step, complete as `EXHAUSTED` instead.
- Immediate delivery means the step becomes eligible for the existing worker immediately; normal queue/provider processing still applies.
- After that next step sends, later steps follow their normal configured delays unless another acknowledgement pauses the run.
- Acknowledgement schedules the resolution-deadline check. It does not immediately queue the next escalation email.
- Resolution before timeout wins skips the remaining unsent steps and prevents later notifications.

## Agreed uniform node waits and final-step outcome

Every node follows the same acknowledgement and resolution rules. Configure waits
on the node, not in a separate final-node setting. The only final-node difference
is that there is nobody else to notify when its wait expires.

| Event | A next step exists | No next step exists |
| --- | --- | --- |
| Notification accepted by SMTP | Step becomes `SENT`; wait for acknowledgement | Same; do not immediately mark the run exhausted |
| Acknowledgement wait expires without acknowledgement | Notify the next step; the existing delivery delay is this same wait, not an additional wait | `OPEN → COMPLETED`, resolution type `EXHAUSTED` |
| Recipient acknowledges, resolution enabled | `OPEN → ACKNOWLEDGED`; start this node's resolution wait and pause the next step | Same resolution wait; no next step to pause |
| Resolution wait expires unresolved | Resume `OPEN`; next step becomes due immediately | `ACKNOWLEDGED → COMPLETED`, resolution type `EXHAUSTED` |
| Recipient resolves before the deadline | `ACKNOWLEDGED → RESOLVED`; skip unsent steps | `ACKNOWLEDGED → RESOLVED` |

`EXHAUSTED` means the run ran out of escalation steps, not that the issue was
resolved. Do not resend the last message, restart the run, or invent a recipient.
Keep the sent step `SENT`; no extra last-node lifecycle or step status is needed.
With resolution disabled, acknowledgement still completes as `ACKNOWLEDGED`
without starting a resolution timer. The final recipient must still have a chance
to acknowledge before exhaustion; this corrects the existing immediate-exhaustion bug.
Exhausted send retries remain a delivery-failure path, not a recipient response wait.

**Two node response durations are agreed:** The existing delivery delay is the
acknowledgement waiting period, not a separate additional duration. If nobody
acknowledges, its expiry allows the next step to send. Timely acknowledgement
with resolution enabled pauses progression and starts the resolution duration
from the acknowledgement time. Unresolved resolution expiry makes the next step
due immediately; do not restart the acknowledgement/delivery wait. Do not stack
two ordinary waits, compare independent acknowledgement/send delays, add a third
duration, or introduce a special final-node timeout. Retain the existing first-step
delay; align runtime ownership/deadline storage with this shared wait during implementation.

## Agreed viewable links and action deadlines

- Keep the email link viewable after a response deadline. Passing that deadline makes the action unavailable, not the link itself expired. Opening/previewing remains read-only and exposes only recipient-scoped safe information; unknown or forged tokens never grant access.
- A new acknowledgement requires the current eligible sent step, `OPEN`, and `clock.instant()` strictly before its saved acknowledgement deadline. At or after the deadline, reject it even if the expiry callback has not run yet. A link to an earlier step cannot reclaim ownership after advancement.
- A new resolution requires the current acknowledged owner/step, `ACKNOWLEDGED`, and the current time strictly before its saved resolution deadline. Reject it at or after that deadline, even if the database status has not yet been advanced by a delayed timer.
- The deadlines are independent: acknowledgement accepted before its deadline starts the resolution window. Passing the acknowledgement deadline afterward does not prevent timely resolution by an eligible actor.
- An identical action already accepted returns its saved result without changing state, extending deadlines, producing another audit event, or advancing another step. Historical success is not renewed permission to act after timeout or on a later acknowledgement.
- The page explains “Acknowledgement window has expired”, “Resolution window has expired”, “Escalation has moved to another recipient”, or the already-saved result, as appropriate. Recheck state, exact step ownership, and the relevant deadline under the run lock on POST; preview alone cannot authorize an action.
- Preserve token hashing and exact recipient/run/step scope. Do not couple viewability to the acknowledgement deadline or invalidate a timely recipient's resolution access when that earlier deadline passes. A viewable old link does not grant permission to act on a later step; email Escalate now follows the source-window rules above.

### Reuse existing team access for resolution

Node creation/editing already require a recipient from the selected team. Reuse
the existing team-access model: authenticated same-team members may resolve in
the UI; the currently acknowledging recipient may resolve through their scoped
email link. Both paths check the current acknowledged context and resolution
deadline, and record the actual resolving actor. Do not require the UI actor to
be the original acknowledging recipient or introduce extra roles or states.

## Configuration and runtime ownership

| Owner | Values and purpose |
| --- | --- |
| `Flow` | Resolution-timeout toggle for the reusable configuration |
| `Node` | Existing duration representing the shared acknowledgement/delivery wait, plus resolution duration when enabled; no separate send-delay duration |
| `Escalation` | Snapshotted toggle, lifecycle status, acknowledged execution-step ID, acknowledgement time/actor, UTC resolution deadline, and resolution time/actor |
| `FlowExecutionState` | Snapshotted node timing, notification step status, due time, UTC acknowledgement deadline, and existing send-attempt/publication metadata |
| Acknowledgement token | Exact execution-step reference, recipient scope, and token hash; response eligibility comes from current ownership and saved deadlines, not link expiry |

Create execution-step rows at escalation start. Copy the flow toggle and node timeouts in the same transaction that claims the run and creates its steps. Validate and read a consistent flow configuration: a concurrent flow/node edit cannot produce a mixed snapshot. Once started, reusable-flow edits must not change that run's toggle, node timeouts, or delays. Scheduled runs take this snapshot when they actually start; advance snapshotting at schedule creation is outside the currently agreed scope.

Keep PostgreSQL authoritative. Store deadlines as UTC `Instant` values and inject `Clock` into the business services. In-memory entries own only timer handles. Keep audit history in the existing generic audit service.

Validate toggle/timeout consistency when saving flow configuration and before starting a run. Because the current editor saves nodes individually, the configuration update must provide a consistent way to enable the toggle with all required timeouts and disable it while clearing them. Reuse the flow version/locking approach to serialize related updates.

The application is not deployed, so preserving the old dual-status API is unnecessary. Use migrations and fixtures for the new schema without discarding workspace/database data implicitly.

## One notification-step status

Replace `executionState` and `notificationState` with one string-persisted enum, tentatively named `FlowExecutionStepStatus`:

| Status | Meaning |
| --- | --- |
| `PENDING` | Created at run start; waiting for its turn |
| `SCHEDULED` | Delivery has a saved due time |
| `PAUSED` | Delivery is suspended while another recipient works on resolution |
| `SENDING` | Worker owns this send attempt |
| `SENT` | SMTP accepted the message; actual recipient delivery is not guaranteed |
| `FAILED` | Send retries are exhausted |
| `SKIPPED` | Run ended before this step could send |

`SENT`, `FAILED`, and `SKIPPED` are terminal for a step; expose `isTerminal()` instead of adding a separate `TERMINAL` state. There is no step-level `IDLE`, acknowledgement status, resolution status, or delivery-outcome field. Acknowledgement leaves the sent step as `SENT`; incident lifecycle and completion reason remain on the escalation.

Allowed transitions:

```text
PENDING → SCHEDULED → SENDING → SENT
                         ├── retry allowed → SCHEDULED
                         └── retries exhausted → FAILED

SCHEDULED → PAUSED → SCHEDULED (due immediately on timeout or Escalate now)
PENDING / SCHEDULED / PAUSED → SKIPPED (run ends)
```

Keep the disabled-flow path unchanged apart from explicit step-state representation, marking unsent steps skipped on terminal acknowledgement, and the agreed final-recipient acknowledgement correction.

## Timer and concurrency contract

1. Ordinary progression uses one shared acknowledgement/delivery wait. After SMTP acceptance, persist the sent step and its acknowledgement deadline atomically, aligning the next eligible delivery with that same wait rather than adding another delay. Do not exhaust solely because this was the final send; persist its normal acknowledgement wait too. Keep the initial first-step delay unchanged.
2. Acknowledgement locks the run and validates the exact sent step/token. With timeout enabled, atomically save `ACKNOWLEDGED`, the owning step, the deadline, and the paused next step.
3. After commit, cancel its delivery wake-up where available and register the resolution timer. A delivery callback/message already in flight must reload state and ignore paused or obsolete attempts.
4. Resolution, timeout, and manual escalation use the same run-lock/conditional-transition boundary. Only one valid transition wins for the same acknowledgement/step. Publish timer changes or delivery work after commit, never on rollback.
5. If resolution wins, save `RESOLVED`, skip unsent steps, record the audit event, and cancel the deadline wake-up.
6. If resolution timeout wins and a next step exists, save `OPEN`, clear active deadline ownership, set that step to `SCHEDULED` with `dueAt = clock.instant()`, and persist its publication obligation. Otherwise save `COMPLETED` with `EXHAUSTED`. Record the outcome atomically through the audit service; publish delivery work only after commit. Preserve the original send as `SENT`; do not send it again.
7. A timeout callback must match the current acknowledged step and deadline. A callback from an earlier acknowledgement must not affect a later acknowledgement on the same run.
8. Recovery reloads scheduled deliveries and active acknowledgement/resolution waits from PostgreSQL, including final-node waits. Overdue deadlines are checked immediately; restart must not grant a fresh timeout or lose the next-step publication/exhaustion. Failed timer registration needs a retry/recovery path while the process remains running.
9. Acknowledgement expiry validates the current waiting step/deadline under the same run lock before continuing or exhausting. Acknowledgement, a send, manual escalation, and expiry must not produce conflicting transitions. Ignore obsolete callbacks after acknowledgement, advancement, or run completion.

Repeated acknowledgement of the same step must return its saved accepted result without extending its deadline or reclaiming current ownership. Duplicate timer callbacks must not advance two steps. Continue serializing acknowledgement with sends already in progress; SMTP acceptance still has the existing crash/duplicate uncertainty. New acknowledgement/resolution requests must enforce their saved deadlines under the run lock, even when timer execution is delayed.

## Final lifecycle storage decision

- `RESOLVED` is a terminal lifecycle status and needs no additional completion reason; the status itself records the successful outcome.
- `COMPLETED` remains the terminal status for non-resolution completion and requires one explicit completion reason: `ACKNOWLEDGED` when resolution timeout is disabled, or `EXHAUSTED` when the acknowledgement/resolution wait ends without another step or acknowledgement. `CANCELLED`, `START_FAILED`, and non-terminal statuses have no completion reason.
- The implementation may rename the legacy `resolutionType` field to a clearly named completion-reason field, while preserving readable string enum values and migrating existing rows explicitly. Do not add a second reason field for `RESOLVED`.
- Active acknowledgement ownership consists of the acknowledged execution-step ID, actor, acknowledgement time, and resolution deadline. Populate these only while the run is `ACKNOWLEDGED`; clear them atomically when resolution wins, a resolution timeout advances the run, or Escalate now ends the wait. Persist the resolving actor/time only for `RESOLVED`.
- Audit events retain the acknowledgement, timeout, escalation, and resolution actor/step/deadline facts after active ownership is cleared. UI/API history reads audit facts rather than reconstructing them from nullable current-owner fields.

This prerequisite is complete. Manual-action and activity-timeline behavior remain agreed; dependent implementation proceeds one task at a time.

## Implementation tasks, one at a time

### 1. Settle remaining product edge cases

- [x] Agree on the final lifecycle storage semantics above and update this plan and the launch checklist.
- Acceptance: final lifecycle storage meanings have explicit rules without adding states. The shared acknowledgement/delivery wait, optional resolution duration, final-step behavior, deadline-gated actions, email source eligibility, existing team permissions, and timeline presentation are already agreed above; do not reopen them.

### 2. Support Start now for scheduled runs

- [x] Extend the existing manual start API/use case and conditional claim; reuse step creation and add after-commit start-timer cancellation, actor audit, and the scheduled-run UI action.
- Acceptance: early start produces one execution-state set, retains the first-step delay, and rolls back without losing the original schedule on failure. Automatic due-time gating remains enforced. Test manual/automatic starts, cancellation/reschedule races, duplicate starts, stale timer callbacks, and team isolation.
- This focused task can proceed independently of the remaining resolution decisions when selected by the user.
- **Local implementation evidence:** The existing `POST /{escalationId}/start` accepts same-team `SCHEDULED` runs, uses a conditional early-start claim, records the authenticated actor, and cancels the scheduler handle after the transactional start returns. The UI exposes **Start now** for scheduled runs. Focused start/controller/scheduling tests, backend packaging, and the UI build pass. PostgreSQL/deployed verification remains pending.

### 3. Unify execution-step statuses

- [x] Add the enum and database constraints; migrate the model, repository queries, consumer, scheduler, recovery, DTOs, and UI status mappings together.
- Acceptance: one status per step; existing send, retry, duplicate-message, and disabled acknowledgement behavior still works. No unsupported status combinations remain.
- **Local implementation evidence:** `FlowExecutionStepStatus` is persisted as a string enum with `PENDING`, `SCHEDULED`, `PAUSED`, `SENDING`, `SENT`, `FAILED`, and `SKIPPED`. Migration V11 converts legacy rows, replaces the old columns, adds a database check, and rebuilds the recovery index. Queue claiming, scheduling, startup recovery, DTOs, UI badges, duplicate-delivery tests, and disabled acknowledgement now use the single status. Focused tests, the normal backend suite, backend packaging, and the UI build pass; PostgreSQL integration remains environment-gated and skipped.

### 4. Add agreed flow/node timing configuration

- [x] Implement the flow toggle and the agreed two node response durations, API validation, consistent configuration updates, and flow-editor controls. Reuse the existing duration for the shared acknowledgement/delivery wait; add no separate send-delay control. Use the same response controls/rules for first, middle, and last nodes, retaining the initial first-step delay.
- Acceptance: disabled flows have null resolution timeouts; enabled flows have positive resolution timeouts on every node; the configuration update is all-or-nothing under the flow version; and no final-only setting is introduced. Runtime acknowledgement, snapshot, and expiry behavior remain in later tasks.
- **Local implementation evidence:** Migration V12 stores the flow toggle and nullable positive node timeout. `PUT /api/v1/flow/{flowId}/timing` requires the current flow version and validates the complete node set before saving. Node create/edit requests apply the same enabled/disabled rules, and the flow editor exposes the toggle plus per-node timeout controls. Focused flow-service tests cover enable/disable, partial input, stale versions, and node-level validation.

### 5. Persist a consistent runtime snapshot

- [ ] Copy settings during immediate/scheduled start and bind acknowledgement tokens to exact execution steps. Add escalation acknowledgement/resolution ownership and durable per-step acknowledgement deadlines using the agreed timing mapping.
- Acceptance: flow edits cannot change a started run; failed start rolls back snapshot creation; same-recipient nodes remain distinguishable.

### 6. Implement acknowledgement pause

- [ ] Branch on the saved toggle, invalidate the acknowledgement wait, pause the next step if present, save the resolution deadline, and record lifecycle audit data atomically. Emit wake-up changes after commit.
- Acceptance: disabled acknowledgement still completes; enabled acknowledgement pauses even on the final node; repeated acknowledgement does not extend the deadline; a stale send cannot bypass the pause.

### 7. Implement explicit resolution

- [ ] Add the agreed resolve action, actor/time persistence, idempotency, authorization, skipped steps, audit, and after-commit timer cancellation.
- Acceptance: authenticated same-team members and the current acknowledging email recipient can resolve before the saved deadline; another team or an obsolete email owner cannot. Terminal runs stay terminal; resolution and timeout have one winner. No new role/state is introduced.

### 8. Implement deadline expiry and recovery

- [ ] Persist the acknowledgement wait after a successful send instead of immediate final-send exhaustion. Use shared deadline handling with current-owner validation, running-process retry, startup recovery, and the advance-or-exhaust branch; reuse durable publication machinery.
- Acceptance: acknowledgement expiry continues or exhausts under the agreed timing mapping; resolution expiry makes the next step due immediately in all original-due-time comparisons or exhausts if none exists. No extra last-node logic, fresh wait after restart, automatic resend, or duplicate transition; failed publication remains recoverable.

### 9. Implement Escalate now

- [ ] Add immediate-due scheduling shared with deadline expiry, a same-team manual action with expected-step validation, atomic audit, durable publication, and deadline invalidation.
- [ ] Add the UI action for waiting/paused steps, confirmation for acknowledged runs, and clear unavailable/conflict responses.
- [ ] Add the email button, anonymous recipient-token preview/POST confirmation, explicit token capability, and recipient-email actor audit using the same business operation.
- Acceptance: OPEN and ACKNOWLEDGED cases both make only the intended next step due immediately from UI or email. Email eligibility uses the current source's acknowledgement/resolution window and is revalidated on POST; expired windows make the action unavailable without hiding the scoped preview. No next step means no change; sent/terminal steps cannot be reopened. Opening/scanning the link is read-only. Invalid/foreign/stale tokens, repeated confirmations, changed preview targets, concurrent resolution/timeout/send, publication failure, and restart do not skip or resend steps or duplicate action audit events.

### 10. Complete escalation/email UI

- [ ] Show acknowledgement ownership and deadline, provide the agreed resolve action, explain paused/skipped steps, and keep valid recipient previews viewable with deadline-specific unavailable-action messages. Handle invalid tokens and email Escalate now source-window restrictions clearly.
- Acceptance: UI distinguishes acknowledgement from resolution only for enabled runs and always displays saved server state.

### 11. Extend audit event coverage

- [ ] Add the missing action enums and safe event details; record creation, sends/retries, acknowledgement/deadline, manual escalation, resolution/timeout, and completion events at their owning operations.
- Acceptance: domain updates and audit roll back together; idempotent/stale paths do not repeat events; step context survives flow edits; email and system actors are labelled correctly. Include audit assertions with each implementation task instead of waiting until the UI is built.

### 12. Add the activity history read API

- [ ] Add same-team access checks, safe timeline DTOs, stable ordering, pagination, and grouped-attempt detail support using the existing audit repository.
- Acceptance: foreign-team/anonymous/token-only access cannot expose history; event order and retry counts remain correct across pages; raw diagnostics and secrets are excluded.

### 13. Build the readable activity timeline

- [ ] Add the detail-page history view, important milestones, expandable failure/retry summaries, actor/time/recipient text, and loading/error/empty/refresh/load-more states. Relabel existing current-state display as Step progress.
- Acceptance: users can read milestone history without expanding retries, then inspect complete attempt details. UI does not fabricate previous events from current statuses or editable nodes.

### 14. Complete local behavior and concurrency verification

- [ ] Cover all acceptance cases, update readiness with evidence, and run focused tests plus normal backend/UI builds.
- [ ] Prepare/run PostgreSQL concurrency and recovery checks when the test environment is available, reporting skips explicitly.
- Acceptance: disabled and enabled paths, early scheduled starts, manual escalation from UI/email, token security and read-only email previews, first/middle/final steps, equal/before/after original due times, retries, terminal outcomes, isolation, restart, and timeline audit/API/grouping are covered according to the agreed rules.
- Deployment and full deployed end-to-end verification occur after remaining implementation work is complete, as requested.

## Verification examples

Shared-wait example: a step sends at 10:00 with a five-minute ordinary wait.
Without acknowledgement, the next step becomes due at 10:05, not 10:10 from
adding another five-minute delay. Acknowledgement at 10:02 with a ten-minute
resolution duration pauses progression until 10:12; if unresolved, make the next
step due immediately then. No extra ordinary delay is applied. With resolution
disabled, timely acknowledgement instead completes the run as already agreed.

| Original next-step due time | Resolution deadline | Unresolved timeout result |
| --- | --- | --- |
| 10:10 | 10:20 | Make next step due immediately at 10:20 |
| 10:20 | 10:20 | Make next step due immediately at 10:20 |
| 10:30 | 10:20 | Make next step due immediately at 10:20 |

If the application recovers at 10:25, process the overdue deadline and make the next step due at recovery time. If resolution already won, skip it. Times illustrate behavior; persisted instants use UTC.

Also test acknowledgement racing a send, resolution racing expiry, duplicate/stale deadlines, a new acknowledgement after escalation resumes, same email on multiple nodes, invalid configuration, flow edits during start, missing rows/identifiers, and rollback with no after-commit publication.

Link/action acceptance: valid scoped previews remain read-only and viewable after
response deadlines; new actions succeed only before their own deadline and with
current ownership. Test exact deadline equality, delayed callbacks, restart with
overdue deadlines but unchanged status, stale preview-to-POST, and an old Step 1
link after Step 2 becomes active. Timely acknowledgement permits resolution after
the acknowledgement deadline but before the resolution deadline. Repeated accepted
actions return saved outcomes without new state changes/audit events or reclaiming
ownership; invalid/foreign tokens reveal no recipient data.

Email Escalate now acceptance: test before/at/after the acknowledgement deadline
in `OPEN` and before/at/after the resolution deadline in `ACKNOWLEDGED`. A timely
acknowledgement allows escalation within its resolution window even after the
earlier acknowledgement deadline passes. Expired or advanced sources remain
viewable but cannot mutate the run. Cover delayed callbacks/restart, preview
becoming stale, no-next-step/terminal runs, and repeated Step 1 confirmation both
before and after Step 2 sends: none may advance Step 3 or add another audit event.

Final-node acceptance cases (also run as single-node flows): successful send keeps
the run `OPEN` during its acknowledgement window; no acknowledgement exhausts only
at expiry; enabled acknowledgement starts its resolution window; timely resolution
ends `RESOLVED`; unresolved expiry ends `COMPLETED` / `EXHAUSTED` without another
email. Disabled acknowledgement still ends `COMPLETED` / `ACKNOWLEDGED`. Cover
deadline boundaries, acknowledgement/expiry races, restart before/after each saved
deadline, duplicate callbacks, and repeated clicks after exhaustion. Assert one
transition/audit event and no additional send.

## Current evidence

This document specifies planned behavior. Manual start now accepts same-team `IDLE`
and `SCHEDULED` runs, while automatic scheduled start remains due-gated. Current
code still completes runs on acknowledgement, but execution steps now use the
single persisted `FlowExecutionStepStatus` enum. Resolution-timeout and activity-
timeline behavior remain unimplemented.
Audit actions cover scheduling, rescheduling, start, cancellation, and start
failure; the detail-page execution timeline shows current step rows rather than
event history. No Escalate now, resolution-timeout, or full activity-timeline
implementation/test success is claimed here. Start now has local implementation
and focused-test evidence only; PostgreSQL/deployed verification is pending.

Relevant entry points: [flow model](../src/main/java/com/alertops/flow/model/Flow.java), [node model](../src/main/java/com/alertops/flow/model/Node.java), [start service](../src/main/java/com/alertops/flow_execution_engine/service/FlowExecutionStateService.java), [acknowledgement service](../src/main/java/com/alertops/flow_execution_engine/service/EscalationAcknowledgementService.java), [consumer](../src/main/java/com/alertops/messaging/MessageConsumer.java), and [step scheduling service](../src/main/java/com/alertops/messaging/StepSchedulingService.java).
