# Current task plan

## Task: Complete escalation and email UI

Started: 2026-10-08
Status: In progress

### Goal and scope

Complete the agreed acknowledgement/resolution experience in the team detail
page and recipient email page. Reuse the existing resolution endpoints and
server lifecycle state; keep deployment out of scope.

### Decision-compliance note

- `Escalation.status`, `resolutionDeadline`, `issueSolvedBy`, and
  `acknowledgedStepId` remain the server-owned source of truth.
- Only `ACKNOWLEDGED` runs expose resolution controls; disabled resolution
  remains the existing terminal acknowledgement behavior.
- Recipient tokens remain scoped to the current acknowledgement and cannot
  grant team history or change lifecycle state through the UI.

### Acceptance criteria

- Team members can see acknowledgement ownership/deadline and resolve an
  acknowledged run from the detail page.
- The recipient link distinguishes acknowledgement, active resolution, and
  resolved/completed outcomes with clear errors and deadline messaging.
- Step progress explains paused, skipped, sent, and failed states without
  inventing history from editable flow data.
- Escalate now preview/action restrictions remain visible and understandable.

### Steps

- [x] Add resolution API/types and saved deadline to recipient preview data.
- [x] Add team and recipient resolve controls and clear lifecycle messages.
- [x] Explain step states and improve stale/expired action messaging.
- [x] Run UI/backend verification, update readiness/changelog, and commit.

### Intended verification and limitations

`npm run build`, focused acknowledgement/resolution security and service tests,
the backend package build, and `git diff --check` pass. PostgreSQL/deployed
verification remains pending; no independent read-only verifier is available.
