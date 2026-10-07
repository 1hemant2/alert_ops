# Current task plan

## Task: Build the escalation activity timeline UI

Started: 2026-10-08
Status: Complete

### Goal and scope

Show the saved escalation activity history on the escalation detail page. Reuse
the existing history API and current step-progress display; keep this task
read-only, keep deployment out of scope, and do not commit the changes.

### Decision-compliance note

- The history API is the canonical source for activity; the UI must not infer
  past events from current escalation or editable flow state.
- Keep the existing execution-step data as a separate “Step progress” view.
- Use the API's safe actor/details fields; never render token hashes, secrets,
  diagnostics, or raw metadata strings.
- Preserve server pagination/order and keep history reads read-only.

### Acceptance criteria

- The detail page shows important milestones with plain event wording, actor,
  time, recipient/step details, and retry counts.
- Repeated failures/retries are grouped by execution step with expandable
  attempt details, while all saved events remain available.
- Loading, empty, error, refresh, and load-more states are understandable and
  do not change escalation state.
- UI build, focused checks, backend package, and whitespace checks pass.

### Steps

- [x] Add the API client/types and milestone/retry presentation helpers.
- [x] Add the history section, state handling, and focused UI verification.
- [x] Run verification and update readiness/changelog without committing.

### Verification and limitations

- `npm run build` and `npm run lint` passed; lint reported two pre-existing
  warnings in `ui/src/features/flows/FlowDetailPage.tsx`.
- `./mvnw -q package` and `git diff --check` passed. Browser/deployed
  verification remains unavailable, and no independent read-only verifier was
  available in this environment.
- Changes remain uncommitted because the user did not request a commit.
