# Current task plan

## Task: Complete webhook event history navigation

Started: 2026-10-08
Status: Complete

### Goal and scope

Finish the remaining webhook UI implementation item: let team members reveal
all saved events for a webhook and open the exact task created by an event.
Reuse the existing event-list API and task-detail route. Do not change backend
event storage, pagination contracts, webhook configuration, or deployment
verification.

Previous task: task detail/editing and saved run context UI is complete in
commits `89abc0d` and `d8cc188`.

### Decision-compliance note

- The existing team-scoped `GET /api/v1/team/webhooks/{id}/events` response is
  the source of truth; do not add a second history endpoint or client store.
- Event task links must use the existing `/app/:teamId/tasks/:taskId` route,
  preserving team-scoped access and the task detail editor.
- Keep the current recent-event view compact and make older events explicitly
  available through a clear user action.

### Acceptance criteria

- A webhook with more than three events provides an obvious way to show and
  hide the complete event history.
- Each event links to its specific task detail page and existing run detail.
- Empty, loading, and error states remain understandable.
- UI typecheck, lint, production build, and diff checks pass.
- The launch checklist and changelog record the completed UI item.

### Steps

- [x] Add expandable full-history navigation and specific task links.
- [x] Run UI verification and update launch documentation/changelog.

### Verification and limitations

`npm run typecheck` and `npm run build` pass. `npm run lint` passes with two
pre-existing warnings in `FlowDetailPage.tsx`; the webhook UI adds no warnings.
`git diff --check` passes. No focused UI test script is available. PostgreSQL,
Redis, browser, and deployed verification remain separate release checks.
Independent read-only verification was skipped because no usable subagent
mechanism was available.
